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

package org.springframework.integration.jms.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Properties;

import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;

import org.junit.Test;

import org.springframework.beans.DirectFieldAccessor;
import org.springframework.beans.NotReadablePropertyException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.integration.history.MessageHistory;
import org.springframework.integration.jms.JmsMessageDrivenEndpoint;
import org.springframework.integration.support.SmartLifecycleRoleController;
import org.springframework.integration.test.util.TestUtils;
import org.springframework.jms.connection.JmsTransactionManager;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.AbstractMessageListenerContainer;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.jms.support.destination.JmsDestinationAccessor;
import org.springframework.messaging.Message;
import org.springframework.messaging.PollableChannel;
import org.springframework.util.MultiValueMap;

/**
 * @author Mark Fisher
 * @author Gary Russell
 * @author Artem Bilan
 */
public class JmsInboundGatewayParserTests {

	@Test
	public void testGatewayWithConnectionFactoryAndDestination() {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(
				"jmsGatewayWithConnectionFactoryAndDestination.xml", this.getClass());
		PollableChannel channel = (PollableChannel) context.getBean("requestChannel");
		JmsMessageDrivenEndpoint gateway = (JmsMessageDrivenEndpoint) context.getBean("jmsGateway");
		assertEquals(JmsMessageDrivenEndpoint.class, gateway.getClass());
		context.start();
		Message<?> message = channel.receive(3000);
		MessageHistory history = MessageHistory.read(message);
		assertNotNull(history);
		Properties componentHistoryRecord = TestUtils.locateComponentInHistory(history, "jmsGateway", 0);
		assertNotNull(componentHistoryRecord);
		assertEquals("jms:inbound-gateway", componentHistoryRecord.get("type"));
		assertNotNull("message should not be null", message);
		assertEquals("message-driven-test", message.getPayload());
		context.close();
	}

	@Test
	public void testGatewayWithConnectionFactoryAndDestinationName() {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(
				"jmsGatewayWithConnectionFactoryAndDestinationName.xml", this.getClass());
		PollableChannel channel = (PollableChannel) context.getBean("requestChannel");
		JmsMessageDrivenEndpoint gateway = (JmsMessageDrivenEndpoint) context.getBean("jmsGateway");
		assertEquals(JmsMessageDrivenEndpoint.class, gateway.getClass());
		context.start();
		Message<?> message = channel.receive(3000);
		assertNotNull("message should not be null", message);
		assertEquals("message-driven-test", message.getPayload());
		context.close();
	}

	@Test
	public void testGatewayWithMessageConverter() {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(
				"jmsGatewayWithMessageConverter.xml", this.getClass());
		PollableChannel channel = (PollableChannel) context.getBean("requestChannel");
		JmsMessageDrivenEndpoint gateway = (JmsMessageDrivenEndpoint) context.getBean("jmsGateway");
		assertEquals(JmsMessageDrivenEndpoint.class, gateway.getClass());
		context.start();
		Message<?> message = channel.receive(3000);
		assertNotNull("message should not be null", message);
		assertEquals("converted-test-message", message.getPayload());
		context.close();
	}

	@Test
	public void testGatewayWithDefaultExtractPayload() {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(
				"jmsGatewaysWithExtractPayloadAttributes.xml", this.getClass());
		JmsMessageDrivenEndpoint gateway = (JmsMessageDrivenEndpoint) context.getBean("defaultGateway");
		DirectFieldAccessor accessor = new DirectFieldAccessor(gateway);
		accessor = new DirectFieldAccessor(accessor.getPropertyValue("listener"));
		assertEquals(Boolean.TRUE, accessor.getPropertyValue("extractReplyPayload"));
		context.close();
	}

	@Test
	public void testGatewayWithExtractReplyPayloadTrue() {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(
				"jmsGatewaysWithExtractPayloadAttributes.xml", this.getClass());
		JmsMessageDrivenEndpoint gateway = (JmsMessageDrivenEndpoint) context.getBean("extractReplyPayloadTrue");
		DirectFieldAccessor accessor = new DirectFieldAccessor(gateway);
		accessor = new DirectFieldAccessor(accessor.getPropertyValue("listener"));
		assertEquals(Boolean.TRUE, accessor.getPropertyValue("extractReplyPayload"));
		context.close();
	}

	@Test
	public void testGatewayWithExtractReplyPayloadFalse() {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(
				"jmsGatewaysWithExtractPayloadAttributes.xml", this.getClass());
		JmsMessageDrivenEndpoint gateway = (JmsMessageDrivenEndpoint) context.getBean("extractReplyPayloadFalse");
		DirectFieldAccessor accessor = new DirectFieldAccessor(gateway);
		accessor = new DirectFieldAccessor(accessor.getPropertyValue("listener"));
		assertEquals(Boolean.FALSE, accessor.getPropertyValue("extractReplyPayload"));
		context.close();
	}

	@Test
	public void testGatewayWithExtractRequestPayloadTrue() {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(
				"jmsGatewaysWithExtractPayloadAttributes.xml", this.getClass());
		JmsMessageDrivenEndpoint gateway = (JmsMessageDrivenEndpoint) context.getBean("extractRequestPayloadTrue");
		DirectFieldAccessor accessor = new DirectFieldAccessor(gateway);
		accessor = new DirectFieldAccessor(accessor.getPropertyValue("listener"));
		assertEquals(Boolean.TRUE, accessor.getPropertyValue("extractRequestPayload"));
		context.close();
	}

	@Test
	public void testGatewayWithExtractRequestPayloadFalse() {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(
				"jmsGatewaysWithExtractPayloadAttributes.xml", this.getClass());
		JmsMessageDrivenEndpoint gateway = (JmsMessageDrivenEndpoint) context.getBean("extractRequestPayloadFalse");
		DirectFieldAccessor accessor = new DirectFieldAccessor(gateway);
		accessor = new DirectFieldAccessor(accessor.getPropertyValue("listener"));
		assertEquals(Boolean.FALSE, accessor.getPropertyValue("extractRequestPayload"));
		context.close();
	}

	@Test(expected = BeanDefinitionStoreException.class)
	public void testGatewayWithConnectionFactoryOnly() {
		try {
			new ClassPathXmlApplicationContext("jmsGatewayWithConnectionFactoryOnly.xml", this.getClass()).close();
		}
		catch (BeanDefinitionStoreException e) {
			assertTrue(e.getMessage().contains("request-destination"));
			assertTrue(e.getMessage().contains("request-destination-name"));
			throw e;
		}
	}

	@Test(expected = BeanDefinitionStoreException.class)
	public void testGatewayWithEmptyConnectionFactory() {
		try {
			new ClassPathXmlApplicationContext("jmsGatewayWithEmptyConnectionFactory.xml", this.getClass()).close();
		}
		catch (BeanDefinitionStoreException e) {
			assertTrue(e.getMessage().contains("connection-factory"));
			throw e;
		}
	}

	@Test
	public void testGatewayWithDefaultConnectionFactory() {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(
				"jmsGatewayWithDefaultConnectionFactory.xml", this.getClass());
		PollableChannel channel = (PollableChannel) context.getBean("requestChannel");
		JmsMessageDrivenEndpoint gateway = (JmsMessageDrivenEndpoint) context.getBean("jmsGateway");
		assertEquals(JmsMessageDrivenEndpoint.class, gateway.getClass());
		context.start();
		Message<?> message = channel.receive(3000);
		assertNotNull("message should not be null", message);
		assertEquals("message-driven-test", message.getPayload());
		context.close();
	}

	@Test
	public void testTransactionManagerIsNullByDefault() {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(
				"jmsGatewayTransactionManagerTests.xml", this.getClass());
		JmsMessageDrivenEndpoint gateway = (JmsMessageDrivenEndpoint) context.getBean("gatewayWithoutTransactionManager");
		DirectFieldAccessor accessor = new DirectFieldAccessor(gateway);
		accessor = new DirectFieldAccessor(accessor.getPropertyValue("listenerContainer"));
		assertNull(accessor.getPropertyValue("transactionManager"));
		context.close();
	}

	@Test
	public void testGatewayWithTransactionManagerReference() {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(
				"jmsGatewayTransactionManagerTests.xml", this.getClass());
		JmsMessageDrivenEndpoint gateway = (JmsMessageDrivenEndpoint) context.getBean("gatewayWithTransactionManager");
		DirectFieldAccessor accessor = new DirectFieldAccessor(gateway);
		accessor = new DirectFieldAccessor(accessor.getPropertyValue("listenerContainer"));
		Object txManager = accessor.getPropertyValue("transactionManager");
		assertEquals(JmsTransactionManager.class, txManager.getClass());
		assertEquals(context.getBean("txManager"), txManager);
		assertEquals(context.getBean("testConnectionFactory"), ((JmsTransactionManager) txManager).getConnectionFactory());
		context.close();
	}

	@Test
	public void testGatewayWithConcurrentConsumers() {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(
				"jmsGatewayWithContainerSettings.xml", this.getClass());
		JmsMessageDrivenEndpoint gateway = (JmsMessageDrivenEndpoint) context.getBean("gatewayWithConcurrentConsumers");
		gateway.start();
		AbstractMessageListenerContainer container = (AbstractMessageListenerContainer)
				new DirectFieldAccessor(gateway).getPropertyValue("listenerContainer");
		assertEquals(3, new DirectFieldAccessor(container).getPropertyValue("concurrentConsumers"));
		gateway.stop();
		context.close();
	}

	@Test
	public void testGatewayWithMaxConcurrentConsumers() {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(
				"jmsGatewayWithContainerSettings.xml", this.getClass());
		JmsMessageDrivenEndpoint gateway = (JmsMessageDrivenEndpoint) context.getBean("gatewayWithMaxConcurrentConsumers");
		gateway.start();
		AbstractMessageListenerContainer container = (AbstractMessageListenerContainer)
				new DirectFieldAccessor(gateway).getPropertyValue("listenerContainer");
		assertEquals(22, new DirectFieldAccessor(container).getPropertyValue("maxConcurrentConsumers"));
		gateway.stop();
		context.close();
	}

	@Test
	public void testGatewayWithMaxMessagesPerTask() {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(
				"jmsGatewayWithContainerSettings.xml", this.getClass());
		JmsMessageDrivenEndpoint gateway = (JmsMessageDrivenEndpoint) context.getBean("gatewayWithMaxMessagesPerTask");
		gateway.start();
		AbstractMessageListenerContainer container = (AbstractMessageListenerContainer)
				new DirectFieldAccessor(gateway).getPropertyValue("listenerContainer");
		assertEquals(99, new DirectFieldAccessor(container).getPropertyValue("maxMessagesPerTask"));
		gateway.stop();
		context.close();
	}

	@Test
	public void testGatewayWithReceiveTimeout() {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(
				"jmsGatewayWithContainerSettings.xml", this.getClass());
		JmsMessageDrivenEndpoint gateway = (JmsMessageDrivenEndpoint) context.getBean("gatewayWithReceiveTimeout");
		gateway.start();
		AbstractMessageListenerContainer container = (AbstractMessageListenerContainer)
				new DirectFieldAccessor(gateway).getPropertyValue("listenerContainer");
		assertEquals(1111L, new DirectFieldAccessor(container).getPropertyValue("receiveTimeout"));
		gateway.stop();
		context.close();
	}

	@Test
	public void testGatewayWithRecoveryInterval() {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(
				"jmsGatewayWithContainerSettings.xml", this.getClass());
		JmsMessageDrivenEndpoint gateway = (JmsMessageDrivenEndpoint) context.getBean("gatewayWithRecoveryInterval");
		gateway.start();
		AbstractMessageListenerContainer container = (AbstractMessageListenerContainer)
				new DirectFieldAccessor(gateway).getPropertyValue("listenerContainer");
		Object recoveryInterval;
		try {
			recoveryInterval = TestUtils.getPropertyValue(container, "recoveryInterval");
		}
		catch (NotReadablePropertyException e) {
			recoveryInterval = TestUtils.getPropertyValue(container, "backOff.interval");
		}
		assertEquals(2222L, recoveryInterval);
		gateway.stop();
		context.close();
	}

	@Test
	public void testGatewayWithIdleTaskExecutionLimit() {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(
				"jmsGatewayWithContainerSettings.xml", this.getClass());
		JmsMessageDrivenEndpoint gateway = (JmsMessageDrivenEndpoint) context.getBean("gatewayWithIdleTaskExecutionLimit");
		gateway.start();
		AbstractMessageListenerContainer container = (AbstractMessageListenerContainer)
				new DirectFieldAccessor(gateway).getPropertyValue("listenerContainer");
		assertEquals(7, new DirectFieldAccessor(container).getPropertyValue("idleTaskExecutionLimit"));
		gateway.stop();
		context.close();
	}

	@Test
	public void testGatewayWithIdleConsumerLimit() {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(
				"jmsGatewayWithContainerSettings.xml", this.getClass());
		JmsMessageDrivenEndpoint gateway = (JmsMessageDrivenEndpoint) context.getBean("gatewayWithIdleConsumerLimit");
		gateway.start();
		AbstractMessageListenerContainer container = (AbstractMessageListenerContainer)
				new DirectFieldAccessor(gateway).getPropertyValue("listenerContainer");
		assertEquals(33, new DirectFieldAccessor(container).getPropertyValue("idleConsumerLimit"));
		gateway.stop();
		context.close();
	}

	@Test
	public void testGatewayWithContainerReference() {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(
				"inboundGatewayWithContainerReference.xml", this.getClass());
		JmsMessageDrivenEndpoint gateway =
				context.getBean("gatewayWithContainerReference", JmsMessageDrivenEndpoint.class);
		SmartLifecycleRoleController roleController = context.getBean(SmartLifecycleRoleController.class);
		@SuppressWarnings("unchecked")
		MultiValueMap<String, SmartLifecycle> lifecycles =
				TestUtils.getPropertyValue(roleController, "lifecycles", MultiValueMap.class);
		assertTrue(lifecycles.containsKey("foo"));
		assertSame(gateway, lifecycles.getFirst("foo"));
		gateway.start();
		AbstractMessageListenerContainer container = (AbstractMessageListenerContainer)
				new DirectFieldAccessor(gateway).getPropertyValue("listenerContainer");
		assertEquals(context.getBean("messageListenerContainer"), container);
		gateway.stop();
		context.close();
	}

	@Test
	public void testGatewayWithMessageSelector() {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(
				"inboundGatewayWithMessageSelector.xml", this.getClass());
		JmsMessageDrivenEndpoint gateway = (JmsMessageDrivenEndpoint) context.getBean("gatewayWithMessageSelector");
		gateway.start();
		AbstractMessageListenerContainer container = (AbstractMessageListenerContainer)
				new DirectFieldAccessor(gateway).getPropertyValue("listenerContainer");
		String messageSelector = (String) new DirectFieldAccessor(container).getPropertyValue("messageSelector");
		assertEquals("TestProperty = 'foo'", messageSelector);
		gateway.stop();
		context.close();
	}

	@Test
	public void testGatewayWithReplyQosProperties() {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(
				"inboundGatewayWithReplyQos.xml", this.getClass());
		JmsMessageDrivenEndpoint gateway = context.getBean("gatewayWithReplyQos", JmsMessageDrivenEndpoint.class);
		DirectFieldAccessor accessor = new DirectFieldAccessor(
				new DirectFieldAccessor(gateway).getPropertyValue("listener"));
		assertEquals(12345L, accessor.getPropertyValue("replyTimeToLive"));
		assertEquals(7, accessor.getPropertyValue("replyPriority"));
		assertEquals(DeliveryMode.NON_PERSISTENT, accessor.getPropertyValue("replyDeliveryMode"));
		assertEquals(true, accessor.getPropertyValue("explicitQosEnabledForReplies"));
		context.close();
	}

	@Test
	public void replyQosPropertiesDisabledByDefault() {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(
				"inboundGatewayDefault.xml", this.getClass());
		JmsMessageDrivenEndpoint gateway = context.getBean("gateway", JmsMessageDrivenEndpoint.class);
		DirectFieldAccessor accessor = new DirectFieldAccessor(
				new DirectFieldAccessor(gateway).getPropertyValue("listener"));
		assertEquals(false, accessor.getPropertyValue("explicitQosEnabledForReplies"));
		context.close();
	}

	@Test
	public void gatewayWithPubSubDomain() {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(
				"jmsGatewayWithPubSubDomain.xml", this.getClass());
		JmsMessageDrivenEndpoint endpoint = context.getBean("gateway", JmsMessageDrivenEndpoint.class);
		JmsDestinationAccessor container = (JmsDestinationAccessor) new DirectFieldAccessor(endpoint).getPropertyValue("listenerContainer");
		assertEquals(Boolean.TRUE, container.isPubSubDomain());
		context.close();
	}

	@Test
	public void gatewayWithDurableSubscription() {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(
				"inboundGatewayWithDurableSubscription.xml", this.getClass());
		JmsMessageDrivenEndpoint endpoint = context.getBean("gateway", JmsMessageDrivenEndpoint.class);
		DefaultMessageListenerContainer container = (DefaultMessageListenerContainer) new DirectFieldAccessor(endpoint).getPropertyValue("listenerContainer");
		assertEquals(Boolean.TRUE, container.isPubSubDomain());
		assertEquals(Boolean.TRUE, container.isSubscriptionDurable());
		assertEquals("testDurableSubscriptionName", container.getDurableSubscriptionName());
		assertEquals("testClientId", container.getClientId());
		assertTrue(container.isSubscriptionShared());
		context.close();
	}

	@Test
	public void gatewayWithReplyChannel() {
		ActiveMqTestUtils.prepare();
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(
				"jmsGatewayWithReplyChannel.xml", this.getClass());
		JmsMessageDrivenEndpoint gateway = (JmsMessageDrivenEndpoint) context.getBean("gateway");
		Object replyChannel = TestUtils.getPropertyValue(gateway, "listener.gatewayDelegate.replyChannel");
		assertEquals(context.getBean("replies"), replyChannel);
		JmsTemplate template = new JmsTemplate(context.getBean(ConnectionFactory.class));
		template.convertAndSend("testDestination", "Hello");
		assertNotNull(template.receive("testReplyDestination"));
		context.close();
	}

}
