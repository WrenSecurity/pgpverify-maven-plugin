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
package org.simplify4u.plugins.keysmap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.simplify4u.plugins.TestArtifactBuilder.testArtifact;
import static org.simplify4u.plugins.TestUtils.getPGPgpPublicKey;

import org.assertj.core.api.Assertions;
import org.codehaus.plexus.resource.ResourceManager;
import org.codehaus.plexus.resource.loader.ResourceNotFoundException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/**
 * @author Slawomir Jaranowski.
 */
@Listeners(MockitoTestNGListener.class)
public class KeysMapTest {

    @Mock
    private ResourceManager resourceManager;

    @InjectMocks
    private KeysMap keysMap;


    @BeforeMethod
    void setup() throws ResourceNotFoundException {
        doAnswer(invocation -> getClass().getResourceAsStream(invocation.getArgument(0)))
                .when(resourceManager).getResourceAsInputStream(anyString());
    }

    @Test
    public void isComponentSet() {
        assertThat(keysMap).isNotNull();
    }

    @Test
    public void nullLocationTest() throws Exception {
        reset(resourceManager);
        keysMap.load(null);

        verifyNoInteractions(resourceManager);
        assertThat(keysMap.isValidKey(testArtifact().build(), null, null)).isTrue();
    }

    @Test
    public void emptyLocationTest() throws Exception {
        reset(resourceManager);
        keysMap.load("");

        verifyNoInteractions(resourceManager);
        assertThat(keysMap.isValidKey(testArtifact().build(), null, null)).isTrue();
    }

    @Test
    public void validKeyFromMap() throws Exception {

        keysMap.load("/keysMap.list");

        verify(resourceManager).getResourceAsInputStream(anyString());

        assertThat(
                keysMap.isValidKey(
                        testArtifact().groupId("junit").artifactId("junit").version("4.12").build(),
                        getPGPgpPublicKey(0x123456789abcdef0L), null)
        ).isTrue();

        assertThat(
                keysMap.isValidKey(
                        testArtifact().groupId("junit").artifactId("junit").version("4.12").build(),
                        getPGPgpPublicKey(0x123456789abcdeffL), null)
        ).isTrue();

        assertThat(
                keysMap.isValidKey(
                        testArtifact().groupId("testlong").artifactId("fingerprint").version("x.x.x").build(),
                        getPGPgpPublicKey(0x123456789abcdef0L), null)
        ).isTrue();

        assertThat(
                keysMap.isValidKey(
                        testArtifact().groupId("test.test").artifactId("test").version("1.2.3").build(),
                        getPGPgpPublicKey(0x123456789abcdef0L), null)
        ).isTrue();

        assertThat(
                keysMap.isValidKey(
                        testArtifact().groupId("test").artifactId("test").version("1.0.0").build(),
                        getPGPgpPublicKey(0x123456789abcdef0L), null)
        ).isTrue();

        assertThat(
                keysMap.isValidKey(
                        testArtifact().groupId("test2").artifactId("test-package").version("1.0.0").build(),
                        getPGPgpPublicKey(0xA6ADFC93EF34893EL), null)
        ).isTrue();

        assertThat(
                keysMap.isValidKey(
                        testArtifact().groupId("test2").artifactId("test-package").version("1.0.0").build(),
                        getPGPgpPublicKey(0xA6ADFC93EF34893FL), null)
        ).isTrue();
        assertThat(
                keysMap.isWithKey(testArtifact().groupId("test2").artifactId("test-package").version("1.0.0").build())
        ).isTrue();
        assertThat(
                keysMap.isWithKey(testArtifact().groupId("noSig").artifactId("test").version("1").build())
        ).isFalse();
        assertThat(
                keysMap.isWithKey(testArtifact().groupId("noSig").artifactId("non-existent").version("9999").build())
        ).isFalse();
    }

    @Test
    public void invalidKeyFromMap() throws Exception {
        keysMap.load("/keysMap.list");

        verify(resourceManager).getResourceAsInputStream(anyString());

        assertThat(
                keysMap.isValidKey(
                        testArtifact().groupId("junit").artifactId("junit").version("4.11").build(),
                        getPGPgpPublicKey(0x123456789abcdef0L), null)
        ).isFalse();
    }

    @Test
    public void specialValueNoSig() throws Exception {

        keysMap.load("/keysMap.list");

        verify(resourceManager).getResourceAsInputStream(anyString());

        assertThat(
                keysMap.isNoSignature(testArtifact().groupId("noSig").artifactId("test").build())
        ).isTrue();

        assertThat(
                keysMap.isNoSignature(testArtifact().groupId("noSig").artifactId("test2").build())
        ).isTrue();

        assertThat(
                keysMap.isNoSignature(testArtifact().groupId("noSig").artifactId("test3").build())
        ).isTrue();

        assertThat(
                keysMap.isValidKey(
                        testArtifact().groupId("noSig").artifactId("test3").build(),
                        getPGPgpPublicKey(0x123456789ABCDEF0L), null)
        ).isTrue();

    }

    @Test
    public void specialValueBadSig() throws Exception {

        keysMap.load("/keysMap.list");

        verify(resourceManager).getResourceAsInputStream(anyString());

        assertThat(
                keysMap.isBrokenSignature(testArtifact().groupId("badSig").build())
        ).isTrue();
    }

    @Test
    public void specialValueNoKey() throws Exception {

        keysMap.load("/keysMap.list");

        verify(resourceManager).getResourceAsInputStream(anyString());

        assertThat(
                keysMap.isKeyMissing(testArtifact().groupId("noKey").build())
        ).isTrue();
    }

    @Test
    public void shortKeyShouldThrownException() throws Exception {
        Assertions.assertThatCode(() -> keysMap.load("/keyMap-keyToShort.list"))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Key length for = 0x10 is 8 bits, should be between 64 and 160 bits");

    }
}
