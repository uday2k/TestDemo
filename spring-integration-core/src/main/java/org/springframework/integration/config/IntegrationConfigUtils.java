/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.config;

import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.context.Lifecycle;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.context.IntegrationContextUtils;
import org.springframework.integration.support.SmartLifecycleRoleController;

/**
 * Shared utility methods for Integration configuration.
 *
 * @author Artem Bilan
 * @since 4.0
 */
public final class IntegrationConfigUtils {

	public static final String BASE_PACKAGE = "org.springframework.integration";

	public static final String HANDLER_ALIAS_SUFFIX = ".handler";

	// TODO: Boot constant - move to Spring Framework?
	public static final String FACTORY_BEAN_OBJECT_TYPE = "factoryBeanObjectType";

	public static void registerSpelFunctionBean(BeanDefinitionRegistry registry, String functionId, String className,
												String methodSignature) {
		BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(SpelFunctionFactoryBean.class)
				.addConstructorArgValue(className)
				.addConstructorArgValue(methodSignature);
		registry.registerBeanDefinition(functionId, builder.getBeanDefinition());
	}

	public static void autoCreateDirectChannel(String channelName, BeanDefinitionRegistry registry) {
		BeanDefinitionBuilder channelBuilder = BeanDefinitionBuilder.genericBeanDefinition(DirectChannel.class);
		BeanDefinitionHolder holder = new BeanDefinitionHolder(channelBuilder.getBeanDefinition(), channelName);
		BeanDefinitionReaderUtils.registerBeanDefinition(holder, registry);
	}

	public static void registerRoleControllerDefinitionIfNecessary(BeanDefinitionRegistry registry) {
		if (!registry.containsBeanDefinition(
				IntegrationContextUtils.INTEGRATION_LIFECYCLE_ROLE_CONTROLLER)) {
			BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(SmartLifecycleRoleController.class);
			builder.addConstructorArgValue(new ManagedList<String>());
			builder.addConstructorArgValue(new ManagedList<Lifecycle>());
			registry.registerBeanDefinition(
					IntegrationContextUtils.INTEGRATION_LIFECYCLE_ROLE_CONTROLLER, builder.getBeanDefinition());
		}
	}

	private IntegrationConfigUtils() {
	}

}
