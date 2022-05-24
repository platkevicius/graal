/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Facilities for encoding/decoding a set of options to/from a byte array.
 */
public final class OptionsEncoder {

    private OptionsEncoder() {
    }

    /**
     * Determines if {@code value} is supported by {@link #encode(Map)}.
     */
    public static boolean isValueSupported(Object value) {
        return TypedDataOutputStream.isValueSupported(value);
    }

    /**
     * Encodes {@code options} into a byte array.
     *
     * @throws IllegalArgumentException if any value in {@code options} is not
     *             {@linkplain #isValueSupported(Object) supported}
     */
    public static byte[] encode(final Map<String, Object> options) {
        try (ByteArrayOutputStream baout = new ByteArrayOutputStream()) {
            try (TypedDataOutputStream out = new TypedDataOutputStream(baout)) {
                out.writeInt(options.size());
                for (Map.Entry<String, Object> e : options.entrySet()) {
                    out.writeUTF(e.getKey());
                    try {
                        out.writeTypedValue(e.getValue());
                    } catch (IllegalArgumentException iae) {
                        throw new IllegalArgumentException(String.format("Key: %s, Value: %s, Value type: %s",
                                        e.getKey(), e.getValue(), e.getValue().getClass()), iae);
                    }
                }
            }
            return baout.toByteArray();
        } catch (IOException ioe) {
            throw new IllegalArgumentException(ioe);
        }
    }

    /**
     * Decodes {@code input} into a name/value map.
     *
     * @throws IllegalArgumentException if {@code input} cannot be decoded
     */
    public static Map<String, Object> decode(byte[] input) {
        Map<String, Object> res = new LinkedHashMap<>();
        try (TypedDataInputStream in = new TypedDataInputStream(new ByteArrayInputStream(input))) {
            final int size = in.readInt();
            for (int i = 0; i < size; i++) {
                final String key = in.readUTF();
                final Object value = in.readTypedValue();
                res.put(key, value);
            }
            if (in.available() != 0) {
                throw new IllegalArgumentException(in.available() + " undecoded bytes");
            }
        } catch (IOException ioe) {
            throw new IllegalArgumentException(ioe);
        }
        return res;
    }
}
