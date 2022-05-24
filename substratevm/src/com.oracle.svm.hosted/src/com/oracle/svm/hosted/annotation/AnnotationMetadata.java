/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.hosted.annotation;

import java.lang.annotation.Annotation;
import java.lang.annotation.AnnotationFormatError;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import org.graalvm.collections.Pair;
import org.graalvm.compiler.debug.GraalError;

import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.util.ReflectionUtil;

import jdk.internal.reflect.ConstantPool;
import jdk.vm.ci.meta.JavaConstant;
import sun.invoke.util.Wrapper;
import sun.reflect.annotation.AnnotationParser;
import sun.reflect.annotation.AnnotationType;
import sun.reflect.annotation.ExceptionProxy;
import sun.reflect.annotation.TypeNotPresentExceptionProxy;

public class AnnotationMetadata {

    @SuppressWarnings("serial")
    static final class AnnotationExtractionError extends Error {
        AnnotationExtractionError(Throwable cause) {
            super(cause);
        }

        AnnotationExtractionError(String message) {
            super(message);
        }
    }

    private static final Method annotationParserParseSig = ReflectionUtil.lookupMethod(AnnotationParser.class, "parseSig", String.class, Class.class);
    private static final Constructor<?> annotationTypeMismatchExceptionProxyConstructor;

    static {
        try {
            annotationTypeMismatchExceptionProxyConstructor = ReflectionUtil.lookupConstructor(Class.forName("sun.reflect.annotation.AnnotationTypeMismatchExceptionProxy"), String.class);
        } catch (ClassNotFoundException e) {
            throw GraalError.shouldNotReachHere();
        }
    }

    private static Object extractType(ByteBuffer buf, ConstantPool cp, Class<?> container, boolean skip) {
        int typeIndex = buf.getShort() & 0xFFFF;
        if (skip) {
            return null;
        }
        Class<?> type;
        String signature = cp.getUTF8At(typeIndex);
        try {
            type = (Class<?>) annotationParserParseSig.invoke(null, signature, container);
        } catch (InvocationTargetException e) {
            Throwable targetException = e.getTargetException();
            if (targetException instanceof LinkageError || targetException instanceof TypeNotPresentException) {
                return new TypeNotPresentExceptionProxy(signature, targetException);
            }
            throw new AnnotationExtractionError(e);
        } catch (ReflectiveOperationException e) {
            throw new AnnotationExtractionError(e);
        }
        return type;
    }

    private static String extractString(ByteBuffer buf, ConstantPool cp, boolean skip) {
        int index = buf.getShort() & 0xFFFF;
        return skip ? null : cp.getUTF8At(index);
    }

    private static Object checkResult(Object value, Class<?> expectedType) {
        if (!expectedType.isInstance(value)) {
            try {
                if (value instanceof Annotation) {
                    return annotationTypeMismatchExceptionProxyConstructor.newInstance(value.toString());
                } else {
                    return annotationTypeMismatchExceptionProxyConstructor.newInstance(value.getClass().getName() + "[" + value + "]");
                }
            } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
                throw new AnnotationExtractionError(e);
            }
        }
        return value;
    }

    private static final Map<byte[], byte[]> byteArrayCache = new ConcurrentHashMap<>();

    // Values copied from TypeAnnotationParser
    // Regular type parameter annotations
    private static final byte CLASS_TYPE_PARAMETER = 0x00;
    private static final byte METHOD_TYPE_PARAMETER = 0x01;
    // Type Annotations outside method bodies
    private static final byte CLASS_EXTENDS = 0x10;
    private static final byte CLASS_TYPE_PARAMETER_BOUND = 0x11;
    private static final byte METHOD_TYPE_PARAMETER_BOUND = 0x12;
    private static final byte FIELD = 0x13;
    private static final byte METHOD_RETURN = 0x14;
    private static final byte METHOD_RECEIVER = 0x15;
    private static final byte METHOD_FORMAL_PARAMETER = 0x16;
    private static final byte THROWS = 0x17;
    // Type Annotations inside method bodies
    private static final byte LOCAL_VARIABLE = (byte) 0x40;
    private static final byte RESOURCE_VARIABLE = (byte) 0x41;
    private static final byte EXCEPTION_PARAMETER = (byte) 0x42;
    private static final byte INSTANCEOF = (byte) 0x43;
    private static final byte NEW = (byte) 0x44;
    private static final byte CONSTRUCTOR_REFERENCE = (byte) 0x45;
    private static final byte METHOD_REFERENCE = (byte) 0x46;
    private static final byte CAST = (byte) 0x47;
    private static final byte CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT = (byte) 0x48;
    private static final byte METHOD_INVOCATION_TYPE_ARGUMENT = (byte) 0x49;
    private static final byte CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT = (byte) 0x4A;
    private static final byte METHOD_REFERENCE_TYPE_ARGUMENT = (byte) 0x4B;

    private static byte[] extractTargetInfo(ByteBuffer buf) {
        int startPos = buf.position();
        int posCode = buf.get() & 0xFF;
        switch (posCode) {
            case CLASS_TYPE_PARAMETER:
            case METHOD_TYPE_PARAMETER:
            case METHOD_FORMAL_PARAMETER:
            case EXCEPTION_PARAMETER:
                buf.get();
                break;
            case CLASS_EXTENDS:
            case THROWS:
            case INSTANCEOF:
            case NEW:
            case CONSTRUCTOR_REFERENCE:
            case METHOD_REFERENCE:
                buf.getShort();
                break;
            case CLASS_TYPE_PARAMETER_BOUND:
            case METHOD_TYPE_PARAMETER_BOUND:
                buf.get();
                buf.get();
                break;
            case FIELD:
            case METHOD_RETURN:
            case METHOD_RECEIVER:
                break;
            case LOCAL_VARIABLE:
            case RESOURCE_VARIABLE:
                short length = buf.getShort();
                for (int i = 0; i < length; ++i) {
                    buf.getShort();
                    buf.getShort();
                    buf.getShort();
                }
                break;
            case CAST:
            case CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT:
            case METHOD_INVOCATION_TYPE_ARGUMENT:
            case CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT:
            case METHOD_REFERENCE_TYPE_ARGUMENT:
                buf.getShort();
                buf.get();
                break;
            default:
                throw new AnnotationFormatError("Could not parse bytes for type annotations");
        }
        int endPos = buf.position();
        byte[] targetInfo = new byte[endPos - startPos];
        buf.position(startPos).get(targetInfo).position(endPos);
        return byteArrayCache.computeIfAbsent(targetInfo, Function.identity());
    }

    private static byte[] extractLocationInfo(ByteBuffer buf) {
        int startPos = buf.position();
        int depth = buf.get() & 0xFF;
        for (int i = 0; i < depth; i++) {
            buf.get();
            buf.get();
        }
        int endPos = buf.position();
        byte[] locationInfo = new byte[endPos - startPos];
        buf.position(startPos).get(locationInfo).position(endPos);
        return byteArrayCache.computeIfAbsent(locationInfo, Function.identity());
    }

    public interface AnnotationMemberValue {
        static AnnotationMemberValue extract(ByteBuffer buf, ConstantPool cp, Class<?> container, boolean skip) {
            char tag = (char) buf.get();
            switch (tag) {
                case 'e':
                    return EnumValue.extract(buf, cp, container, skip);
                case 'c':
                    return ClassValue.extract(buf, cp, container, skip);
                case 's':
                    return StringValue.extract(buf, cp, skip);
                case '@':
                    return AnnotationValue.extract(buf, cp, container, true, skip);
                case '[':
                    return ArrayValue.extract(buf, cp, container, skip);
                default:
                    return PrimitiveValue.extract(buf, cp, tag, skip);
            }
        }

        static AnnotationMemberValue from(Class<?> type, Object value) {
            if (type.isAnnotation()) {
                return new AnnotationValue((Annotation) value);
            } else if (type.isEnum()) {
                return new EnumValue((Enum<?>) value);
            } else if (type == Class.class) {
                return new ClassValue((Class<?>) value);
            } else if (type == String.class) {
                return new StringValue((String) value);
            } else if (type.isArray()) {
                return new ArrayValue(type.getComponentType(), (Object[]) value);
            } else {
                return new PrimitiveValue(type, value);
            }
        }

        default List<Class<?>> getTypes() {
            return Collections.emptyList();
        }

        default List<String> getStrings() {
            return Collections.emptyList();
        }

        default List<JavaConstant> getExceptionProxies() {
            return Collections.emptyList();
        }

        char getTag();

        Object get(Class<?> memberType);
    }

    public static final class AnnotationValue implements AnnotationMemberValue {
        private static final Map<Pair<Class<? extends Annotation>, Map<String, AnnotationMemberValue>>, AnnotationValue> cache = new ConcurrentHashMap<>();
        private static final Map<AnnotationValue, Annotation> resolvedAnnotations = new ConcurrentHashMap<>();

        final Class<? extends Annotation> type;
        final Map<String, AnnotationMemberValue> members;

        @SuppressWarnings("unchecked")
        static AnnotationValue extract(ByteBuffer buf, ConstantPool cp, Class<?> container, boolean exceptionOnMissingAnnotationClass, boolean skip) {
            boolean skipMembers = skip;
            Object typeOrException = extractType(buf, cp, container, skip);
            if (typeOrException instanceof TypeNotPresentExceptionProxy) {
                if (exceptionOnMissingAnnotationClass) {
                    TypeNotPresentExceptionProxy proxy = (TypeNotPresentExceptionProxy) typeOrException;
                    throw new TypeNotPresentException(proxy.typeName(), proxy.getCause());
                }
                skipMembers = true;
            }

            int numMembers = buf.getShort() & 0xFFFF;
            Map<String, AnnotationMemberValue> memberValues = new LinkedHashMap<>();
            for (int i = 0; i < numMembers; i++) {
                String memberName = extractString(buf, cp, skipMembers);
                AnnotationMemberValue memberValue = AnnotationMemberValue.extract(buf, cp, container, skipMembers);
                if (!skipMembers) {
                    memberValues.put(memberName, memberValue);
                }
            }

            if (skipMembers) {
                return null;
            }
            Class<? extends Annotation> type = (Class<? extends Annotation>) typeOrException;
            return cache.computeIfAbsent(Pair.create(type, memberValues), (ignored) -> new AnnotationValue(type, memberValues));
        }

        AnnotationValue(Annotation annotation) {
            this.type = annotation.annotationType();
            this.members = new LinkedHashMap<>();
            AnnotationType annotationType = AnnotationType.getInstance(type);
            annotationType.members().forEach((memberName, memberAccessor) -> {
                AnnotationMemberValue memberValue;
                try {
                    memberValue = AnnotationMemberValue.from(annotationType.memberTypes().get(memberName), memberAccessor.invoke(annotation));
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new AnnotationExtractionError(e);
                }
                Object memberDefault = annotationType.memberDefaults().get(memberName);
                if (!memberValue.equals(memberDefault)) {
                    members.put(memberName, memberValue);
                }
            });
        }

        private AnnotationValue(Class<? extends Annotation> type, Map<String, AnnotationMemberValue> members) {
            this.type = type;
            this.members = members;
        }

        public Class<? extends Annotation> getType() {
            return type;
        }

        public int getMemberCount() {
            return members.size();
        }

        public void forEachMember(BiConsumer<String, AnnotationMemberValue> callback) {
            members.forEach(callback);
        }

        @Override
        public List<Class<?>> getTypes() {
            List<Class<?>> types = new ArrayList<>();
            types.add(type);
            for (AnnotationMemberValue memberValue : members.values()) {
                types.addAll(memberValue.getTypes());
            }
            return types;
        }

        @Override
        public List<String> getStrings() {
            List<String> strings = new ArrayList<>();
            members.forEach((memberName, memberValue) -> {
                strings.add(memberName);
                strings.addAll(memberValue.getStrings());
            });
            return strings;
        }

        @Override
        public List<JavaConstant> getExceptionProxies() {
            List<JavaConstant> exceptionProxies = new ArrayList<>();
            for (AnnotationMemberValue memberValue : members.values()) {
                exceptionProxies.addAll(memberValue.getExceptionProxies());
            }
            return exceptionProxies;
        }

        @Override
        public char getTag() {
            return '@';
        }

        @Override
        public Object get(Class<?> memberType) {
            Annotation value = resolvedAnnotations.computeIfAbsent(this, annotationValue -> {
                AnnotationType annotationType = AnnotationType.getInstance(annotationValue.type);
                Map<String, Object> memberValues = new LinkedHashMap<>(annotationType.memberDefaults());
                annotationValue.members.forEach((memberName, memberValue) -> memberValues.put(memberName, memberValue.get(annotationType.memberTypes().get(memberName))));
                return AnnotationParser.annotationForMap(type, memberValues);
            });
            return checkResult(value, memberType);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            AnnotationValue that = (AnnotationValue) o;
            return Objects.equals(type, that.type) && members.equals(that.members);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, members);
        }
    }

    public static final class ClassValue implements AnnotationMemberValue {
        private static final Map<Class<?>, ClassValue> cache = new ConcurrentHashMap<>();

        private final Class<?> value;

        private static AnnotationMemberValue extract(ByteBuffer buf, ConstantPool cp, Class<?> container, boolean skip) {
            Object typeOrException = extractType(buf, cp, container, skip);
            if (skip) {
                return null;
            }
            if (typeOrException instanceof ExceptionProxy) {
                return new ExceptionProxyValue((ExceptionProxy) typeOrException);
            }
            Class<?> type = (Class<?>) typeOrException;
            return cache.computeIfAbsent(type, ClassValue::new);
        }

        private ClassValue(Class<?> value) {
            this.value = value;
        }

        public Class<?> getValue() {
            return value;
        }

        @Override
        public List<Class<?>> getTypes() {
            return Collections.singletonList(value);
        }

        @Override
        public char getTag() {
            return 'c';
        }

        @Override
        public Object get(Class<?> memberType) {
            return checkResult(value, memberType);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ClassValue that = (ClassValue) o;
            return Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }

    public static final class EnumValue implements AnnotationMemberValue {
        private static final Map<Pair<Class<? extends Enum<?>>, String>, EnumValue> cache = new ConcurrentHashMap<>();

        private final Class<? extends Enum<?>> type;
        private final String name;

        @SuppressWarnings({"unchecked"})
        private static AnnotationMemberValue extract(ByteBuffer buf, ConstantPool cp, Class<?> container, boolean skip) {
            Object typeOrException = extractType(buf, cp, container, skip);
            String constName = extractString(buf, cp, skip);
            if (skip) {
                return null;
            }
            if (typeOrException instanceof ExceptionProxy) {
                return new ExceptionProxyValue((ExceptionProxy) typeOrException);
            }
            Class<? extends Enum<?>> type = (Class<? extends Enum<?>>) typeOrException;
            return cache.computeIfAbsent(Pair.create(type, constName), (ignored) -> new EnumValue(type, constName));
        }

        private EnumValue(Enum<?> value) {
            this.type = value.getDeclaringClass();
            this.name = value.name();
        }

        private EnumValue(Class<? extends Enum<?>> type, String name) {
            this.type = type;
            this.name = name;
        }

        public Class<? extends Enum<?>> getType() {
            return type;
        }

        public String getName() {
            return name;
        }

        @Override
        public List<Class<?>> getTypes() {
            return Collections.singletonList(type);
        }

        @Override
        public List<String> getStrings() {
            return Collections.singletonList(name);
        }

        @Override
        public char getTag() {
            return 'e';
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public Object get(Class<?> memberType) {
            Enum<? extends Enum<?>> value = Enum.valueOf((Class<? extends Enum>) type, name);
            return checkResult(value, memberType);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            EnumValue enumValue = (EnumValue) o;
            return Objects.equals(type, enumValue.type) && Objects.equals(name, enumValue.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, name);
        }
    }

    public static final class StringValue implements AnnotationMemberValue {
        private static final Map<String, StringValue> cache = new ConcurrentHashMap<>();

        private final String value;

        private static StringValue extract(ByteBuffer buf, ConstantPool cp, boolean skip) {
            String value = extractString(buf, cp, skip);
            return skip ? null : cache.computeIfAbsent(value, StringValue::new);
        }

        private StringValue(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public List<String> getStrings() {
            return Collections.singletonList(value);
        }

        @Override
        public char getTag() {
            return 's';
        }

        @Override
        public Object get(Class<?> memberType) {
            return checkResult(value, memberType);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            StringValue that = (StringValue) o;
            return Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }

    public static final class ArrayValue implements AnnotationMemberValue {
        private static final ArrayValue EMPTY_ARRAY_VALUE = new ArrayValue();

        private final AnnotationMemberValue[] elements;

        private static ArrayValue extract(ByteBuffer buf, ConstantPool cp, Class<?> container, boolean skip) {
            int length = buf.getShort() & 0xFFFF;
            if (length == 0) {
                return EMPTY_ARRAY_VALUE;
            }
            AnnotationMemberValue[] elements = new AnnotationMemberValue[length];
            for (int i = 0; i < length; ++i) {
                elements[i] = AnnotationMemberValue.extract(buf, cp, container, skip);
            }
            return skip ? null : new ArrayValue(elements);
        }

        private ArrayValue(Class<?> elementType, Object[] values) {
            this.elements = new AnnotationMemberValue[values.length];
            for (int i = 0; i < values.length; ++i) {
                this.elements[i] = AnnotationMemberValue.from(elementType, values[i]);
            }
        }

        private ArrayValue(AnnotationMemberValue... elements) {
            this.elements = elements;
        }

        public int getElementCount() {
            return elements.length;
        }

        public void forEachElement(Consumer<AnnotationMemberValue> callback) {
            for (AnnotationMemberValue element : elements) {
                callback.accept(element);
            }
        }

        @Override
        public List<Class<?>> getTypes() {
            List<Class<?>> types = new ArrayList<>();
            for (AnnotationMemberValue element : elements) {
                types.addAll(element.getTypes());
            }
            return types;
        }

        @Override
        public List<String> getStrings() {
            List<String> strings = new ArrayList<>();
            for (AnnotationMemberValue element : elements) {
                strings.addAll(element.getStrings());
            }
            return strings;
        }

        @Override
        public List<JavaConstant> getExceptionProxies() {
            List<JavaConstant> exceptionProxies = new ArrayList<>();
            for (AnnotationMemberValue element : elements) {
                exceptionProxies.addAll(element.getExceptionProxies());
            }
            return exceptionProxies;
        }

        @Override
        public char getTag() {
            return '[';
        }

        @Override
        public Object get(Class<?> memberType) {
            Class<?> componentType = memberType.getComponentType();
            Object[] result = (Object[]) Array.newInstance(memberType.getComponentType(), elements.length);
            int tag = 0;
            boolean typeMismatch = false;
            for (int i = 0; i < elements.length; ++i) {
                Object value = elements[i].get(componentType);
                if (value instanceof ExceptionProxy) {
                    typeMismatch = true;
                    tag = elements[i].getTag();
                } else {
                    result[i] = value;
                }
            }
            if (typeMismatch) {
                try {
                    return annotationTypeMismatchExceptionProxyConstructor.newInstance("Array with component tag: " + (tag == 0 ? "0" : (char) tag));
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                    throw new AnnotationExtractionError(e);
                }
            }
            return checkResult(result, memberType);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ArrayValue that = (ArrayValue) o;
            return Arrays.equals(elements, that.elements);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(elements);
        }
    }

    public static final class PrimitiveValue implements AnnotationMemberValue {
        private static final Map<Object, PrimitiveValue> cache = new ConcurrentHashMap<>();

        private final char tag;
        private final Object value;

        private static PrimitiveValue extract(ByteBuffer buf, ConstantPool cp, char tag, boolean skip) {
            int constIndex = buf.getShort() & 0xFFFF;
            if (skip) {
                return null;
            }
            Object value;
            switch (tag) {
                case 'B':
                    value = (byte) cp.getIntAt(constIndex);
                    break;
                case 'C':
                    value = (char) cp.getIntAt(constIndex);
                    break;
                case 'D':
                    value = cp.getDoubleAt(constIndex);
                    break;
                case 'F':
                    value = cp.getFloatAt(constIndex);
                    break;
                case 'I':
                    value = cp.getIntAt(constIndex);
                    break;
                case 'J':
                    value = cp.getLongAt(constIndex);
                    break;
                case 'S':
                    value = (short) cp.getIntAt(constIndex);
                    break;
                case 'Z':
                    value = cp.getIntAt(constIndex) != 0;
                    break;
                default:
                    throw new AnnotationExtractionError("Invalid annotation encoding. Unknown tag " + tag);
            }
            assert Wrapper.forWrapperType(value.getClass()).basicTypeChar() == tag;
            return cache.computeIfAbsent(value, v -> new PrimitiveValue(tag, v));
        }

        private PrimitiveValue(Class<?> type, Object value) {
            this((type.isPrimitive() ? Wrapper.forPrimitiveType(type) : Wrapper.forWrapperType(type)).basicTypeChar(), value);
        }

        private PrimitiveValue(char tag, Object value) {
            this.tag = tag;
            this.value = value;
        }

        @Override
        public char getTag() {
            return tag;
        }

        public Object getValue() {
            return value;
        }

        @Override
        public Object get(Class<?> memberType) {
            return checkResult(value, memberType);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            PrimitiveValue that = (PrimitiveValue) o;
            return tag == that.tag && Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tag, value);
        }
    }

    public static final class ExceptionProxyValue implements AnnotationMemberValue {
        private final ExceptionProxy exceptionProxy;
        private final JavaConstant objectConstant;

        private ExceptionProxyValue(ExceptionProxy exceptionProxy) {
            this.exceptionProxy = exceptionProxy;
            this.objectConstant = SubstrateObjectConstant.forObject(exceptionProxy);
        }

        public JavaConstant getObjectConstant() {
            return objectConstant;
        }

        @Override
        public char getTag() {
            return 'E';
        }

        @Override
        public Object get(Class<?> memberType) {
            return exceptionProxy;
        }

        @Override
        public List<Class<?>> getTypes() {
            return Collections.singletonList(exceptionProxy.getClass());
        }

        @Override
        public List<JavaConstant> getExceptionProxies() {
            return Collections.singletonList(objectConstant);
        }
    }

    public static final class TypeAnnotationValue {
        private final byte[] targetInfo;
        private final byte[] locationInfo;
        private final AnnotationValue annotation;

        static TypeAnnotationValue extract(ByteBuffer buf, ConstantPool cp, Class<?> container) {
            byte[] targetInfo = extractTargetInfo(buf);
            byte[] locationInfo = extractLocationInfo(buf);
            AnnotationValue annotation = AnnotationValue.extract(buf, cp, container, false, false);

            return new TypeAnnotationValue(targetInfo, locationInfo, annotation);
        }

        private TypeAnnotationValue(byte[] targetInfo, byte[] locationInfo, AnnotationValue annotation) {
            this.targetInfo = targetInfo;
            this.locationInfo = locationInfo;
            this.annotation = annotation;
        }

        public byte[] getTargetInfo() {
            return targetInfo;
        }

        public byte[] getLocationInfo() {
            return locationInfo;
        }

        public AnnotationValue getAnnotationData() {
            return annotation;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TypeAnnotationValue that = (TypeAnnotationValue) o;
            return Arrays.equals(targetInfo, that.targetInfo) && Arrays.equals(locationInfo, that.locationInfo) && Objects.equals(annotation, that.annotation);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(annotation);
            result = 31 * result + Arrays.hashCode(targetInfo);
            result = 31 * result + Arrays.hashCode(locationInfo);
            return result;
        }
    }
}
