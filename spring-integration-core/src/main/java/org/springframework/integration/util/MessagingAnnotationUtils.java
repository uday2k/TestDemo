/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.integration.annotation.Payloads;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * Utility methods to support annotation processing.
 *
 * @author Gary Russell
 * @author Dave Syer
 * @author Gunnar Hillert
 * @author Soby Chacko
 * @author Artem Bilan
 * @since 4.0
 */
public final class MessagingAnnotationUtils {

	/**
	 * Get the attribute value from the annotation hierarchy, returning the first non-empty
	 * value closest to the annotated method. While traversing up the hierarchy, for string-valued
	 * attributes, an empty string is ignored. For array-valued attributes, an empty
	 * array is ignored.
	 * The overridden attribute must be the same type.
	 * @param annotations The meta-annotations in order (closest first).
	 * @param name The attribute name.
	 * @param requiredType The expected type.
	 * @param <T> The type.
	 * @return The value.
	 */
	@SuppressWarnings("unchecked")
	public static <T> T resolveAttribute(List<Annotation> annotations, String name, Class<T> requiredType) {
		for (Annotation annotation : annotations) {
			if (annotation != null) {
				Object value = AnnotationUtils.getValue(annotation, name);
				if (value != null && value.getClass() == requiredType && hasValue(value)) {
					return (T) value;
				}
			}
		}
		return null;
	}

	public static boolean hasValue(Object value) {
		return value != null && (!(value instanceof String) || (StringUtils.hasText((String) value)))
				&& (!value.getClass().isArray() || ((Object[]) value).length > 0);
	}

	public static Method findAnnotatedMethod(Object target, final Class<? extends Annotation> annotationType) {
		final AtomicReference<Method> reference = new AtomicReference<Method>();

		ReflectionUtils.doWithMethods(getTargetClass(target), new ReflectionUtils.MethodCallback() {

			@Override
			public void doWith(Method method) throws IllegalArgumentException, IllegalAccessException {
				reference.compareAndSet(null, method);
			}
		}, new ReflectionUtils.MethodFilter() {

			@Override
			public boolean matches(Method method) {
				return ReflectionUtils.USER_DECLARED_METHODS.matches(method) &&
						AnnotatedElementUtils.isAnnotated(method, annotationType.getName());
			}
		});

		return reference.get();
	}

	/**
	 * Find the one of {@link Payload}, {@link Header} or {@link Headers} annotation from
	 * the provided {@code annotations} array. Optionally also detects {@link Payloads}.
	 * @param annotations the annotations to scan.
	 * @param payloads true if @Payloads should be detected.
	 * @return the matched annotation or {@code null}.
	 * @throws MessagingException if more than one of {@link Payload}, {@link Header}
	 * or {@link Headers} annotations are presented.
	 */
	@SuppressWarnings("deprecation")
	public static Annotation findMessagePartAnnotation(Annotation[] annotations, boolean payloads) {
		if (annotations == null || annotations.length == 0) {
			return null;
		}
		Annotation match = null;
		for (Annotation annotation : annotations) {
			Class<? extends Annotation> type = annotation.annotationType();
			if (type.equals(org.springframework.integration.annotation.Payload.class)
					|| type.equals(Payload.class)
					|| type.equals(org.springframework.integration.annotation.Header.class)
					|| type.equals(Header.class)
					|| type.equals(org.springframework.integration.annotation.Headers.class)
					|| type.equals(Headers.class)
					|| (payloads && type.equals(Payloads.class))) {
				if (match != null) {
					throw new MessagingException("At most one parameter annotation can be provided "
							+ "for message mapping, but found two: [" + match.annotationType().getName() + "] and ["
							+ annotation.annotationType().getName() + "]");
				}
				match = annotation;
			}
		}
		return match;
	}

	private static Class<?> getTargetClass(Object targetObject) {
		Class<?> targetClass = targetObject.getClass();
		if (AopUtils.isAopProxy(targetObject)) {
			targetClass = AopUtils.getTargetClass(targetObject);
		}
		else if (ClassUtils.isCglibProxyClass(targetClass)) {
			Class<?> superClass = targetObject.getClass().getSuperclass();
			if (!Object.class.equals(superClass)) {
				targetClass = superClass;
			}
		}
		return targetClass;
	}

	private MessagingAnnotationUtils() {}

}
