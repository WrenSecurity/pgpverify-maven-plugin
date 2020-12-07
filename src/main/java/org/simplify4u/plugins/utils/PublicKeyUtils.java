/*
 * Copyright 2020 Slawomir Jaranowski
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.simplify4u.plugins.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import io.vavr.control.Try;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility for PGPPublicKey
 */
public final class PublicKeyUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicKeyUtils.class);

    private PublicKeyUtils() {
        // No need to instantiate utility class.
    }

    /**
     * Generate string version of key fingerprint
     *
     * @param publicKey
     *         given key
     *
     * @return fingerprint as string
     */
    public static String fingerprint(PGPPublicKey publicKey) {
        return HexUtils.fingerprintToString(publicKey.getFingerprint());
    }

    /**
     * Generate string version of master key fingerprint
     *
     * @param publicKey
     *         given key
     * @param publicKeyRing
     *         keys ring with master and sub keys
     *
     * @return master key fingerprint as string
     */
    public static String fingerprintForMaster(PGPPublicKey publicKey, PGPPublicKeyRing publicKeyRing) {
        return fingerprint(getMasterKey(publicKey, publicKeyRing).orElse(publicKey));
    }

    /**
     * Generate string with key id description.
     *
     * @param publicKey
     *         given key
     * @param publicKeyRing
     *         keys ring with master and sub keys
     *
     * @return string with key id description
     */
    public static String keyIdDescription(PGPPublicKey publicKey, PGPPublicKeyRing publicKeyRing) {

        Optional<PGPPublicKey> masterKey = getMasterKey(publicKey, publicKeyRing);

        if (masterKey.isPresent()) {
            return String.format("SubKeyId: 0x%16X of %s", publicKey.getKeyID(), fingerprint(masterKey.get()));
        } else {
            return "KeyId: " + fingerprint(publicKey);
        }
    }

    /**
     * Return master key for given sub public key.
     *
     * @param publicKey
     *         given key
     * @param publicKeyRing
     *         keys ring with master and sub keys
     *
     * @return master key of empty if not found or given key is master key
     */
    public static Optional<PGPPublicKey> getMasterKey(PGPPublicKey publicKey, PGPPublicKeyRing publicKeyRing) {

        if (publicKey.isMasterKey()) {
            return Optional.empty();
        }

        Iterator<?> signatures = publicKey.getSignaturesOfType(PGPSignature.SUBKEY_BINDING);
        if (signatures.hasNext()) {
            PGPSignature sig = (PGPSignature) signatures.next();
            return Optional.ofNullable(publicKeyRing.getPublicKey(sig.getKeyID()));
        }

        return Optional.empty();
    }

    public static Collection<String> getUserIDs(PGPPublicKey publicKey, PGPPublicKeyRing publicKeyRing) {
        // use getRawUserIDs and standard java String to transform byte array to utf8
        // because BC generate exception if there is some problem in decoding utf8
        // https://github.com/s4u/pgpverify-maven-plugin/issues/61
        Set<byte[]> ret = new LinkedHashSet<>();
        publicKey.getRawUserIDs().forEachRemaining(ret::add);

        getMasterKey(publicKey, publicKeyRing).ifPresent(masterKey ->
                masterKey.getRawUserIDs().forEachRemaining(ret::add)
        );

        return ret.stream()
                .map(b -> new String(b, StandardCharsets.UTF_8))
                .collect(Collectors.toSet());
    }

    /**
     * Load Public Keys ring from stream for given keyId.
     *
     * @param keyStream
     *         input stream with public keys
     * @param keyId
     *         key ID for find proper key ring
     *
     * @return key ring with given key id
     *
     * @throws IOException
     *         if problem with comunication
     * @throws PGPException
     *         if problem with PGP data
     */
    public static Optional<PGPPublicKeyRing> loadPublicKeyRing(InputStream keyStream, PGPKeyId keyId)
            throws IOException, PGPException {

        InputStream keyIn = PGPUtil.getDecoderStream(keyStream);
        PGPPublicKeyRingCollection pgpRing = new PGPPublicKeyRingCollection(keyIn, new BcKeyFingerprintCalculator());

        Optional<PGPPublicKeyRing> publicKeyRing = Optional.ofNullable(keyId.getKeyRingFromRingCollection(pgpRing));
        publicKeyRing.ifPresent(PublicKeyUtils::verifyPublicKeyRing);

        return publicKeyRing;
    }

    /**
     * Validate signatures for subKeys in given key ring.
     *
     * @param publicKeyRing
     *         keys to verify
     */
    private static void verifyPublicKeyRing(PGPPublicKeyRing publicKeyRing) {

        StreamSupport.stream(publicKeyRing.spliterator(), false)
                .filter(key -> !key.isMasterKey())
                .forEach(key -> Try.run(() -> verifySigForSubKey(key, publicKeyRing)).get());
    }

    private static void verifySigForSubKey(PGPPublicKey subKey, PGPPublicKeyRing publicKeyRing) throws PGPException {

        int signatureTypeToCheck = subKey.hasRevocation()
                ? PGPSignature.SUBKEY_REVOCATION : PGPSignature.SUBKEY_BINDING;

        AtomicBoolean hasValidSignature = new AtomicBoolean(false);

        Iterator<?> it = subKey.getSignaturesOfType(signatureTypeToCheck);
        it.forEachRemaining(s -> Try.run(() -> {
                    PGPSignature sig = (PGPSignature) s;

                    PGPPublicKey masterKey = publicKeyRing.getPublicKey(sig.getKeyID());
                    if (masterKey != null) {
                        sig.init(new BcPGPContentVerifierBuilderProvider(), masterKey);
                        if (sig.verifyCertification(masterKey, subKey)) {
                            hasValidSignature.set(true);
                        } else {
                            LOGGER.debug("Invalid signature [{}] type: {} for subKey: {}",
                                    sig.getCreationTime(), sig.getSignatureType(), fingerprint(subKey));
                        }
                    } else {
                        throw new PGPException(
                                String.format("Signature type: %d Not found key 0x%016X for subKeyId: %s",
                                sig.getSignatureType(), sig.getKeyID(), fingerprint(subKey)));
                    }
                }).get()
        );

        if (!hasValidSignature.get()) {
            throw new PGPException(String.format("No valid signature type: %d for subKey: %s",
                    signatureTypeToCheck, fingerprint(subKey)));
        }
    }
}
