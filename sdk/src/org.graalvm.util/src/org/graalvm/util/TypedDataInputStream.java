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

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * A stream that can read (trivial) values using their in-band data type information, intended for
 * use with {@link TypedDataOutputStream}.
 */
public class TypedDataInputStream extends DataInputStream {
    public TypedDataInputStream(InputStream in) {
        super(in);
    }

    /**
     * Reads a single value, using the data type encoded in the stream.
     *
     * @return The read value, such as a boxed primitive or a {@link String}.
     * @exception IOException in case of an I/O error.
     */
    public Object readTypedValue() throws IOException {
        Object value;
        final byte type = readByte();
        switch (type) {
            case 'Z':
                value = readBoolean();
                break;
            case 'B':
                value = readByte();
                break;
            case 'S':
                value = readShort();
                break;
            case 'C':
                value = readChar();
                break;
            case 'I':
                value = readInt();
                break;
            case 'J':
                value = readLong();
                break;
            case 'F':
                value = readFloat();
                break;
            case 'D':
                value = readDouble();
                break;
            case 'U':
                int len = readInt();
                byte[] bytes = new byte[len];
                readFully(bytes);
                value = new String(bytes, StandardCharsets.UTF_8);
                break;
            default:
                throw new IOException("Unsupported type: " + Integer.toHexString(type));
        }
        return value;
    }
}
