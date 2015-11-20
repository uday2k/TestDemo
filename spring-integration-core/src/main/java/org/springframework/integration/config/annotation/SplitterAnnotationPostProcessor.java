/*
 * Copyright 2002-2015 the original author or authors.
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

package org.springframework.integration.config.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.integration.annotation.Splitter;
import org.springframework.integration.splitter.AbstractMessageSplitter;
import org.springframework.integration.splitter.MethodInvokingSplitter;
import org.springframework.integration.util.MessagingAnnotationUtils;
import org.springframework.messaging.MessageHandler;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Post-processor for Methods annotated with {@link Splitter @Splitter}.
 *
 * @author Mark Fisher
 * @author Gary Russell
 * @author Artem Bilan
 */
public class SplitterAnnotationPostProcessor extends AbstractMethodAnnotationPostProcessor<Splitter> {

	public SplitterAnnotationPostProcessor(ConfigurableListableBeanFactory beanFactory) {
		super(beanFactory);
		this.messageHandlerAttributes.addAll(Arrays.<String>asList("outputChannel", "applySequence", "adviceChain"));
	}

	@Override
	protected MessageHandler createHandler(Object bean, Method method, List<Annotation> annotations) {
		String applySequence = MessagingAnnotationUtils.resolveAttribute(annotations, "applySequence", String.class);

		AbstractMessageSplitter splitter;
		if (AnnotatedElementUtils.isAnnotated(method, Bean.class.getName())) {
			Object target = this.resolveTargetBeanFromMethodWithBeanAnnotation(method);
			splitter = this.extractTypeIfPossible(target, AbstractMessageSplitter.class);
			if (splitter == null) {
				if (target instanceof MessageHandler) {
					Assert.hasText(applySequence, "'applySequence' can be applied to 'AbstractMessageSplitter', but " +
							"target handler is: " + target.getClass());
					return (MessageHandler) target;
				}
				else {
					splitter = new MethodInvokingSplitter(target);
				}
			}
			else {
				checkMessageHandlerAttributes(resolveTargetBeanName(method), annotations);
				return splitter;
			}
		}
		else {
			splitter = new MethodInvokingSplitter(bean, method);
		}

		if (StringUtils.hasText(applySequence)) {
			String applySequenceValue = this.beanFactory.resolveEmbeddedValue(applySequence);
			if (StringUtils.hasText(applySequenceValue)) {
				splitter.setApplySequence(Boolean.parseBoolean(applySequenceValue));
			}
		}

		this.setOutputChannelIfPresent(annotations, splitter);
		return splitter;
	}

}
