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

package org.springframework.integration.ip.config;

import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.integration.config.xml.AbstractChannelAdapterParser;
import org.springframework.integration.config.xml.IntegrationNamespaceUtils;

import org.w3c.dom.Element;

/**
 * @author Gary Russell
 * @since 3.0
 *
 * @deprecated in favor of the generic event adapter.
 */
@Deprecated
public class TcpConnectionEventInboundChannelAdapterParser extends AbstractChannelAdapterParser {

	@Override
	@SuppressWarnings("deprecation")
	protected AbstractBeanDefinition doParse(Element element, ParserContext parserContext, String channelName) {
		BeanDefinitionBuilder adapterBuilder = BeanDefinitionBuilder
				.rootBeanDefinition(org.springframework.integration.ip.tcp.connection.TcpConnectionEventListeningMessageProducer.class);
		adapterBuilder.addPropertyReference("outputChannel", channelName);
		IntegrationNamespaceUtils.setReferenceIfAttributeDefined(adapterBuilder, element, "error-channel", "errorChannel");
		IntegrationNamespaceUtils.setValueIfAttributeDefined(adapterBuilder, element, "event-types");
		return adapterBuilder.getBeanDefinition();
	}

}
