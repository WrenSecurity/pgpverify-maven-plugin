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

import org.apache.commons.lang3.StringUtils;

/**
 * @author Slawomir Jaranowski
 */
public final class ExceptionUtils {

    private ExceptionUtils() {
    }

    /**
     * Return first non blank message from Throwable stack.
     *
     * @param throwable
     *         for looking message
     *
     * @return first not blank message
     */
    public static String getMessage(Throwable throwable) {

        String message = throwable.getMessage();
        Throwable cause = throwable.getCause();

        while (StringUtils.isBlank(message) && cause != null) {
            message = cause.getMessage();
            if (cause.getCause() != cause) {
                cause = cause.getCause();
            } else {
                cause = null;
            }
        }
        return message;
    }
}
