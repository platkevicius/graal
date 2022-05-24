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

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Wrapper class for annotation access that defends against
 * https://bugs.openjdk.java.net/browse/JDK-7183985: when an annotation declares a Class<?> array
 * parameter and one of the referenced classes is not present on the classpath parsing the
 * annotations will result in an ArrayStoreException instead of caching of a
 * TypeNotPresentExceptionProxy. This is a problem in JDK8 but was fixed in JDK11+. This wrapper
 * class also defends against incomplete class path issues. If the element for which annotations are
 * queried is a JMVCI value, i.e., a HotSpotResolvedJavaField, or HotSpotResolvedJavaMethod, the
 * annotations are read via HotSpotJDKReflection using the
 * getFieldAnnotation()/getMethodAnnotation() methods which first construct the field/method object
 * via CompilerToVM.asReflectionField()/CompilerToVM.asReflectionExecutable() which eagerly try to
 * resolve the types referenced in the element signature. If a field declared type or a method
 * return type is missing then JVMCI throws a NoClassDefFoundError.
 */
public final class GuardedAnnotationAccess {
    private static final ThreadLocal<ServiceLoader<AnnotationExtracter>> extracterProvider = ThreadLocal.withInitial(() -> ServiceLoader.load(AnnotationExtracter.class));

    public static boolean isAnnotationPresent(AnnotatedElement element, Class<? extends Annotation> annotationClass) {
        try {
            Optional<AnnotationExtracter> extracter = extracterProvider.get().findFirst();
            if (extracter.isPresent()) {
                return extracter.get().isAnnotationPresent(element, annotationClass);
            }
            return element.isAnnotationPresent(annotationClass);
        } catch (ArrayStoreException | LinkageError e) {
            /*
             * Returning null essentially means that the element doesn't declare the annotationType,
             * but we cannot know that since the annotation parsing failed. However, this allows us
             * to defend against crashing the image builder if the above JDK bug is encountered in
             * user code or if the user code references types missing from the classpath.
             */
            return false;
        }
    }

    public static <T extends Annotation> T getAnnotation(AnnotatedElement element, Class<T> annotationType) {
        try {
            Optional<AnnotationExtracter> extracter = extracterProvider.get().findFirst();
            if (extracter.isPresent()) {
                return extracter.get().getAnnotation(element, annotationType, false);
            }
            return element.getAnnotation(annotationType);
        } catch (ArrayStoreException | LinkageError e) {
            /*
             * Returning null essentially means that the element doesn't declare the annotationType,
             * but we cannot know that since the annotation parsing failed. However, this allows us
             * to defend against crashing the image builder if the above JDK bug is encountered in
             * user code or if the user code references types missing from the classpath.
             */
            return null;
        }
    }

    public static Annotation[] getAnnotations(AnnotatedElement element) {
        try {
            return element.getAnnotations();
        } catch (ArrayStoreException | LinkageError e) {
            /*
             * Returning an empty array essentially means that the element doesn't declare any
             * annotations, but we know that it is not true since the reason the annotation parsing
             * failed is because some annotation referenced a missing class. However, this allows us
             * to defend against crashing the image builder if the above JDK bug is encountered in
             * user code or if the user code references types missing from the classpath.
             */
            return new Annotation[0];
        }
    }

    public static <T extends Annotation> T getDeclaredAnnotation(AnnotatedElement element, Class<T> annotationType) {
        try {
            Optional<AnnotationExtracter> extracter = extracterProvider.get().findFirst();
            if (extracter.isPresent()) {
                return extracter.get().getAnnotation(element, annotationType, true);
            }
            return element.getDeclaredAnnotation(annotationType);
        } catch (ArrayStoreException | LinkageError e) {
            /*
             * Returning null essentially means that the element doesn't declare the annotationType,
             * but we cannot know that since the annotation parsing failed. However, this allows us
             * to defend against crashing the image builder if the above JDK bug is encountered in
             * user code or if the user code references types missing from the classpath.
             */
            return null;
        }
    }

    public static Annotation[] getDeclaredAnnotations(AnnotatedElement element) {
        try {
            return element.getDeclaredAnnotations();
        } catch (ArrayStoreException | LinkageError e) {
            /*
             * Returning an empty array essentially means that the element doesn't declare any
             * annotations, but we know that it is not true since the reason the annotation parsing
             * failed is because it at least one annotation referenced a missing class. However,
             * this allows us to defend against crashing the image builder if the above JDK bug is
             * encountered in user code or if the user code references types missing from the
             * classpath.
             */
            return new Annotation[0];
        }
    }
}
