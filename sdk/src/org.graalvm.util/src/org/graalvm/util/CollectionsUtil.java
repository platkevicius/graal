/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * This class contains utility methods for commonly used functional patterns for collections.
 */
public final class CollectionsUtil {

    private CollectionsUtil() {
    }

    /**
     * Concatenates two iterables into a single iterable. The iterator exposed by the returned
     * iterable does not support {@link Iterator#remove()} even if the input iterables do.
     *
     * @throws NullPointerException if {@code a} or {@code b} is {@code null}
     */
    public static <T> Iterable<T> concat(Iterable<T> a, Iterable<T> b) {
        List<Iterable<T>> l = Arrays.asList(a, b);
        return concat(l);
    }

    /**
     * Concatenates multiple iterables into a single iterable. The iterator exposed by the returned
     * iterable does not support {@link Iterator#remove()} even if the input iterables do.
     *
     * @throws NullPointerException if {@code iterables} or any of its elements are {@code null}
     */
    public static <T> Iterable<T> concat(List<Iterable<T>> iterables) {
        for (Iterable<T> iterable : iterables) {
            Objects.requireNonNull(iterable);
        }
        return new Iterable<>() {
            @Override
            public Iterator<T> iterator() {
                if (iterables.size() == 0) {
                    return Collections.emptyIterator();
                }
                return new Iterator<>() {
                    Iterator<Iterable<T>> cursor = iterables.iterator();
                    Iterator<T> currentIterator = cursor.next().iterator();

                    private void advance() {
                        while (!currentIterator.hasNext() && cursor.hasNext()) {
                            currentIterator = cursor.next().iterator();
                        }
                    }

                    @Override
                    public boolean hasNext() {
                        advance();
                        return currentIterator.hasNext();
                    }

                    @Override
                    public T next() {
                        advance();
                        return currentIterator.next();
                    }
                };
            }

        };
    }

    /**
     * Returns whether all elements in {@code inputs} match {@code predicate}. May not evaluate
     * {@code predicate} on all elements if not necessary for determining the result. If
     * {@code inputs} is empty then {@code true} is returned and {@code predicate} is not evaluated.
     *
     * @return {@code true} if either all elements in {@code inputs} match {@code predicate} or
     *         {@code inputs} is empty, otherwise {@code false}.
     */
    public static <T> boolean allMatch(T[] inputs, Predicate<T> predicate) {
        return allMatch(Arrays.asList(inputs), predicate);
    }

    /**
     * Returns whether all elements in {@code inputs} match {@code predicate}. May not evaluate
     * {@code predicate} on all elements if not necessary for determining the result. If
     * {@code inputs} is empty then {@code true} is returned and {@code predicate} is not evaluated.
     *
     * @return {@code true} if either all elements in {@code inputs} match {@code predicate} or
     *         {@code inputs} is empty, otherwise {@code false}.
     */
    public static <T> boolean allMatch(Iterable<T> inputs, Predicate<T> predicate) {
        for (T t : inputs) {
            if (!predicate.test(t)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns whether any elements in {@code inputs} match {@code predicate}. May not evaluate
     * {@code predicate} on all elements if not necessary for determining the result. If
     * {@code inputs} is empty then {@code false} is returned and {@code predicate} is not
     * evaluated.
     *
     * @return {@code true} if any elements in {@code inputs} match {@code predicate}, otherwise
     *         {@code false}.
     */
    public static <T> boolean anyMatch(T[] inputs, Predicate<T> predicate) {
        return anyMatch(Arrays.asList(inputs), predicate);
    }

    /**
     * Returns whether any elements in {@code inputs} match {@code predicate}. May not evaluate
     * {@code predicate} on all elements if not necessary for determining the result. If
     * {@code inputs} is empty then {@code false} is returned and {@code predicate} is not
     * evaluated.
     *
     * @return {@code true} if any elements in {@code inputs} match {@code predicate}, otherwise
     *         {@code false}.
     */
    public static <T> boolean anyMatch(Iterable<T> inputs, Predicate<T> predicate) {
        for (T t : inputs) {
            if (predicate.test(t)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a new list consisting of the elements in {@code inputs} that match {@code predicate}.
     *
     * @return the new list.
     */
    public static <T> List<T> filterToList(List<T> inputs, Predicate<? super T> predicate) {
        return filterToList(inputs, predicate, ArrayList::new);
    }

    /**
     * Appends elements of {@code inputs} that match {@code predicate} to the list generated by
     * {@code listGenerator}.
     *
     * @return the list generated by {@code listGenerator}.
     */
    public static <T> List<T> filterToList(List<T> inputs, Predicate<? super T> predicate, Supplier<List<T>> listGenerator) {
        List<T> resultList = listGenerator.get();
        for (T t : inputs) {
            if (predicate.test(t)) {
                resultList.add(t);
            }
        }
        return resultList;
    }

    /**
     * Filters {@code inputs} with {@code predicate}, applies {@code mapper} and adds them in the
     * array provided by {@code arrayGenerator}.
     *
     * @return the array provided by {@code arrayGenerator}.
     */
    public static <T, R> R[] filterAndMapToArray(T[] inputs, Predicate<? super T> predicate, Function<? super T, ? extends R> mapper, IntFunction<R[]> arrayGenerator) {
        List<R> resultList = new ArrayList<>();
        for (T t : inputs) {
            if (predicate.test(t)) {
                resultList.add(mapper.apply(t));
            }
        }
        return resultList.toArray(arrayGenerator.apply(resultList.size()));
    }

    /**
     * Applies {@code mapper} on the elements in {@code inputs} and adds them in the array provided
     * by {@code arrayGenerator}.
     *
     * @return the array provided by {@code arrayGenerator}.
     */
    public static <T, R> R[] mapToArray(T[] inputs, Function<? super T, ? extends R> mapper, IntFunction<R[]> arrayGenerator) {
        return mapToArray(Arrays.asList(inputs), mapper, arrayGenerator);
    }

    /**
     * Applies {@code mapper} on the elements in {@code inputs} and adds them in the array provided
     * by {@code arrayGenerator}.
     *
     * @return the array provided by {@code arrayGenerator}.
     */
    public static <T, R> R[] mapToArray(Collection<T> inputs, Function<? super T, ? extends R> mapper, IntFunction<R[]> arrayGenerator) {
        R[] result = arrayGenerator.apply(inputs.size());
        int idx = 0;
        for (T t : inputs) {
            result[idx++] = mapper.apply(t);
        }
        return result;
    }

    /**
     * Applies {@code mapper} on the elements in {@code inputs}, and joins them together separated
     * by {@code delimiter}.
     *
     * @return a new String that is composed from {@code inputs}.
     */
    public static <T, R> String mapAndJoin(T[] inputs, Function<? super T, ? extends R> mapper, String delimiter) {
        return mapAndJoin(Arrays.asList(inputs), mapper, delimiter, "", "");
    }

    /**
     * Applies {@code mapper} on the elements in {@code inputs}, and joins them together separated
     * by {@code delimiter} and starting with {@code prefix}.
     *
     * @return a new String that is composed from {@code inputs}.
     */
    public static <T, R> String mapAndJoin(T[] inputs, Function<? super T, ? extends R> mapper, String delimiter, String prefix) {
        return mapAndJoin(Arrays.asList(inputs), mapper, delimiter, prefix, "");
    }

    /**
     * Applies {@code mapper} on the elements in {@code inputs}, and joins them together separated
     * by {@code delimiter} and starting with {@code prefix} and ending with {@code suffix}.
     *
     * @return a new String that is composed from {@code inputs}.
     */
    public static <T, R> String mapAndJoin(T[] inputs, Function<? super T, ? extends R> mapper, String delimiter, String prefix, String suffix) {
        return mapAndJoin(Arrays.asList(inputs), mapper, delimiter, prefix, suffix);
    }

    /**
     * Applies {@code mapper} on the elements in {@code inputs}, and joins them together separated
     * by {@code delimiter}.
     *
     * @return a new String that is composed from {@code inputs}.
     */
    public static <T, R> String mapAndJoin(Iterable<T> inputs, Function<? super T, ? extends R> mapper, String delimiter) {
        return mapAndJoin(inputs, mapper, delimiter, "", "");
    }

    /**
     * Applies {@code mapper} on the elements in {@code inputs}, and joins them together separated
     * by {@code delimiter} and starting with {@code prefix}.
     *
     * @return a new String that is composed from {@code inputs}.
     */
    public static <T, R> String mapAndJoin(Iterable<T> inputs, Function<? super T, ? extends R> mapper, String delimiter, String prefix) {
        return mapAndJoin(inputs, mapper, delimiter, prefix, "");
    }

    /**
     * Applies {@code mapper} on the elements in {@code inputs}, and joins them together separated
     * by {@code delimiter} and starting with {@code prefix} and ending with {@code suffix}.
     *
     * @return a new String that is composed from {@code inputs}.
     */
    public static <T, R> String mapAndJoin(Iterable<T> inputs, Function<? super T, ? extends R> mapper, String delimiter, String prefix, String suffix) {
        StringBuilder strb = new StringBuilder();
        String sep = "";
        for (T t : inputs) {
            strb.append(sep).append(prefix).append(mapper.apply(t)).append(suffix);
            sep = delimiter;
        }
        return strb.toString();
    }

}
