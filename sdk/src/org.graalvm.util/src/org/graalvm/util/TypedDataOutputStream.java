/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * A stream that can write (trivial) values together with their data type, for use with
 * {@link TypedDataInputStream}.
 */
public class TypedDataOutputStream extends DataOutputStream {
    /** Determines if {@code value} is supported by {@link #writeTypedValue(Object)}. */
    public static boolean isValueSupported(Object value) {
        if (value == null) {
            return false;
        }
        Class<?> valueClass = value.getClass();
        return valueClass == Boolean.class ||
                        valueClass == Byte.class ||
                        valueClass == Short.class ||
                        valueClass == Character.class ||
                        valueClass == Integer.class ||
                        valueClass == Long.class ||
                        valueClass == Float.class ||
                        valueClass == Double.class ||
                        valueClass == String.class ||
                        value.getClass().isEnum();
    }

    public TypedDataOutputStream(OutputStream out) {
        super(out);
    }

    /**
     * Writes the value that is represented by the given non-null object, together with information
     * on the value's data type.
     *
     * @param value A value of a {@linkplain #isValueSupported supported type}.
     * @exception IllegalArgumentException when the provided type is not supported.
     * @exception IOException in case of an I/O error.
     */
    public void writeTypedValue(Object value) throws IOException {
        Class<?> valueClz = value.getClass();
        if (valueClz == Boolean.class) {
            this.writeByte('Z');
            this.writeBoolean((Boolean) value);
        } else if (valueClz == Byte.class) {
            this.writeByte('B');
            this.writeByte((Byte) value);
        } else if (valueClz == Short.class) {
            this.writeByte('S');
            this.writeShort((Short) value);
        } else if (valueClz == Character.class) {
            this.writeByte('C');
            this.writeChar((Character) value);
        } else if (valueClz == Integer.class) {
            this.writeByte('I');
            this.writeInt((Integer) value);
        } else if (valueClz == Long.class) {
            this.writeByte('J');
            this.writeLong((Long) value);
        } else if (valueClz == Float.class) {
            this.writeByte('F');
            this.writeFloat((Float) value);
        } else if (valueClz == Double.class) {
            this.writeByte('D');
            this.writeDouble((Double) value);
        } else if (valueClz == String.class) {
            writeStringValue((String) value);
        } else if (valueClz.isEnum()) {
            writeStringValue(((Enum<?>) value).name());
        } else {
            throw new IllegalArgumentException(String.format("Unsupported type: Value: %s, Value type: %s", value, valueClz));
        }
    }

    private void writeStringValue(String value) throws IOException {
        this.writeByte('U');
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        this.writeInt(bytes.length);
        this.write(bytes);
    }
}
