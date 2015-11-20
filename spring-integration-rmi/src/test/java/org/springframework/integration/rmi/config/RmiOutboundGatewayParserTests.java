/*
 * Copyright 2002-2014 the original author or authors.
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

package org.springframework.integration.rmi.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.handler.advice.AbstractRequestHandlerAdvice;
import org.springframework.integration.rmi.RmiInboundGateway;
import org.springframework.integration.rmi.RmiOutboundGateway;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.integration.test.util.TestUtils;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.PollableChannel;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * @author Mark Fisher
 * @author Gary Russell
 * @author Artem Bilan
 */
@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
@DirtiesContext
public class RmiOutboundGatewayParserTests {

	private static final QueueChannel testChannel = new QueueChannel();

	@Autowired
	public FooAdvice advice;

	@Autowired
	private MessageChannel advisedChannel;

	@Autowired
	private MessageChannel rmiOutboundGatewayInsideChain;

	@Autowired
	private MessageChannel requestReplyRmiWithChainChannel;

	@Autowired
	private PollableChannel replyChannel;

	@Autowired
	@Qualifier("gateway.handler")
	RmiOutboundGateway gateway;

	@Autowired
	@Qualifier("advised.handler")
	RmiOutboundGateway advised;

	@BeforeClass
	public static void setupTestInboundGateway() throws Exception {
		testChannel.setBeanName("testChannel");
		RmiInboundGateway gateway = new RmiInboundGateway();
		gateway.setRequestChannel(testChannel);
		gateway.setExpectReply(false);
		gateway.setBeanFactory(mock(BeanFactory.class));
		gateway.afterPropertiesSet();
	}

	@Test
	public void testOrder() {
		assertEquals(23, TestUtils.getPropertyValue(gateway, "order"));
		assertTrue(TestUtils.getPropertyValue(gateway, "requiresReply", Boolean.class));
	}

	@Test
	public void directInvocation() {
		assertFalse(TestUtils.getPropertyValue(advised, "requiresReply", Boolean.class));

		advisedChannel.send(new GenericMessage<String>("test"));
		Message<?> result = testChannel.receive(1000);
		assertNotNull(result);
		assertEquals("test", result.getPayload());
		assertEquals(1, advice.adviceCalled);
	}

	@Test //INT-1029
	public void testRmiOutboundGatewayInsideChain() {
		rmiOutboundGatewayInsideChain.send(MessageBuilder.withPayload("test").build());
		Message<?> result = testChannel.receive(1000);
		assertNotNull(result);
		assertEquals("test", result.getPayload());
	}

	@Test //INT-1029
	public void testRmiRequestReplyWithinChain() {
		requestReplyRmiWithChainChannel.send(MessageBuilder.withPayload("test").build());
		Message<?> result = replyChannel.receive(1000);
		assertNotNull(result);
		assertEquals("TEST", result.getPayload());
	}

	public static class FooAdvice extends AbstractRequestHandlerAdvice {

		int adviceCalled;

		@Override
		protected Object doInvoke(ExecutionCallback callback, Object target, Message<?> message) throws Exception {
			adviceCalled++;
			return callback.execute();
		}

	}
}
