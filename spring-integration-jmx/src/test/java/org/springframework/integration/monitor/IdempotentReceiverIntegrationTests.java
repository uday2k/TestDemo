/*
 * Copyright 2014-2015 the original author or authors.
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

package org.springframework.integration.monitor;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.aopalliance.aop.Advice;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.IntegrationMessageHeaderAccessor;
import org.springframework.integration.MessageRejectedException;
import org.springframework.integration.annotation.IdempotentReceiver;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.handler.MessageProcessor;
import org.springframework.integration.handler.ServiceActivatingHandler;
import org.springframework.integration.handler.advice.AbstractRequestHandlerAdvice;
import org.springframework.integration.handler.advice.IdempotentReceiverInterceptor;
import org.springframework.integration.jmx.config.EnableIntegrationMBeanExport;
import org.springframework.integration.metadata.ConcurrentMetadataStore;
import org.springframework.integration.metadata.MetadataStore;
import org.springframework.integration.metadata.SimpleMetadataStore;
import org.springframework.integration.selector.MetadataStoreSelector;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.integration.test.util.TestUtils;
import org.springframework.integration.transformer.Transformer;
import org.springframework.jmx.support.MBeanServerFactoryBean;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessageHandlingException;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.PollableChannel;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.stereotype.Component;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

/**
 * @author Artem Bilan
 * @author Gary Russell
 * @since 4.1
 */
@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
@DirtiesContext
public class IdempotentReceiverIntegrationTests {

	@Autowired
	private MessageChannel input;

	@Autowired
	private PollableChannel output;

	@Autowired
	private MetadataStore store;

	@Autowired
	private IdempotentReceiverInterceptor idempotentReceiverInterceptor;

	@Autowired
	private AtomicInteger adviceCalled;

	@Autowired
	private MessageChannel annotatedMethodChannel;

	@Autowired
	private FooService fooService;

	@Autowired
	private MessageChannel annotatedBeanMessageHandlerChannel;

	@Autowired
	private MessageChannel annotatedBeanMessageHandlerChannel2;

	@Test
	public void testIdempotentReceiver() {
		this.idempotentReceiverInterceptor.setThrowExceptionOnRejection(true);
		TestUtils.getPropertyValue(this.store, "metadata", Map.class).clear();
		Message<String> message = new GenericMessage<String>("foo");
		this.input.send(message);
		Message<?> receive = this.output.receive(10000);
		assertNotNull(receive);
		assertEquals(1, this.adviceCalled.get());
		assertEquals(1, TestUtils.getPropertyValue(this.store, "metadata", Map.class).size());
		String foo = this.store.get("foo");
		assertEquals("FOO", foo);

		try {
			this.input.send(message);
			fail("MessageRejectedException expected");
		}
		catch (Exception e) {
			assertThat(e, instanceOf(MessageRejectedException.class));
		}
		this.idempotentReceiverInterceptor.setThrowExceptionOnRejection(false);
		this.input.send(message);
		receive = this.output.receive(10000);
		assertNotNull(receive);
		assertEquals(2, this.adviceCalled.get());
		assertTrue(receive.getHeaders().get(IntegrationMessageHeaderAccessor.DUPLICATE_MESSAGE, Boolean.class));
		assertEquals(1, TestUtils.getPropertyValue(store, "metadata", Map.class).size());
	}

	@Test
	public void testIdempotentReceiverOnMethod() {
		TestUtils.getPropertyValue(this.store, "metadata", Map.class).clear();
		Message<String> message = new GenericMessage<String>("foo");
		this.annotatedMethodChannel.send(message);
		this.annotatedMethodChannel.send(message);

		assertEquals(2, this.fooService.messages.size());
		assertTrue(this.fooService.messages.get(1).getHeaders().get(IntegrationMessageHeaderAccessor.DUPLICATE_MESSAGE,
				Boolean.class));
	}

	@Test
	public void testIdempotentReceiverOnBeanMessageHandler() {
		PollableChannel replyChannel = new QueueChannel();
		Message<String> message = MessageBuilder.withPayload("bar").setReplyChannel(replyChannel).build();
		this.annotatedBeanMessageHandlerChannel.send(message);

		Message<?> receive = replyChannel.receive(10000);
		assertNotNull(receive);
		assertFalse(receive.getHeaders().containsKey(IntegrationMessageHeaderAccessor.DUPLICATE_MESSAGE));

		this.annotatedBeanMessageHandlerChannel.send(message);
		receive = replyChannel.receive(10000);
		assertNotNull(receive);
		assertTrue(receive.getHeaders().containsKey(IntegrationMessageHeaderAccessor.DUPLICATE_MESSAGE));
		assertTrue(receive.getHeaders().get(IntegrationMessageHeaderAccessor.DUPLICATE_MESSAGE, Boolean.class));

		this.annotatedBeanMessageHandlerChannel2.send(new GenericMessage<String>("baz"));
		try {
			this.annotatedBeanMessageHandlerChannel2.send(new GenericMessage<String>("baz"));
			fail("MessageHandlingException expected");
		}
		catch (Exception e) {
			assertThat(e.getMessage(), containsString("duplicate message has been received"));
		}
	}


	@Configuration
	@EnableIntegration
	@EnableIntegrationMBeanExport(server = "mBeanServer")
	public static class ContextConfiguration {

		@Bean
		public static MBeanServerFactoryBean mBeanServer() {
			MBeanServerFactoryBean mBeanServerFactoryBean = new MBeanServerFactoryBean();
			mBeanServerFactoryBean.setLocateExistingServerIfPossible(true);
			return mBeanServerFactoryBean;
		}

		@Bean
		public HazelcastInstance hazelcastInstance() {
			return Hazelcast.newHazelcastInstance(new Config().setProperty( "hazelcast.logging.type", "log4j" ));
		}


		@Bean
		public ConcurrentMetadataStore store() {
			return new SimpleMetadataStore(hazelcastInstance().<String, String>getMap("idempotentReceiverMetadataStore"));
		}

		@Bean
		public IdempotentReceiverInterceptor idempotentReceiverInterceptor() {
			return new IdempotentReceiverInterceptor(new MetadataStoreSelector(new MessageProcessor<String>() {

						@Override
						public String processMessage(Message<?> message) {
							return message.getPayload().toString();
						}

					}, new MessageProcessor<String>() {

						@Override
						public String processMessage(Message<?> message) {
							return message.getPayload().toString().toUpperCase();
						}

					}, store()));
		}

		@Bean
		public MessageChannel input() {
			return new DirectChannel();
		}

		@Bean
		public PollableChannel output() {
			return new QueueChannel();
		}

		@Bean
		@org.springframework.integration.annotation.Transformer(inputChannel = "input",
				outputChannel = "output", adviceChain = "fooAdvice")
		@IdempotentReceiver("idempotentReceiverInterceptor")
		public Transformer transformer() {
			return new Transformer() {

				@Override
				public Message<?> transform(Message<?> message) {
					return message;
				}

			};
		}

		@Bean
		public AtomicInteger adviceCalled() {
			return new AtomicInteger();
		}

		@Bean
		public Advice fooAdvice(final AtomicInteger adviceCalled) {
			return new AbstractRequestHandlerAdvice() {

				@Override
				protected Object doInvoke(ExecutionCallback callback, Object target, Message<?> message)
						throws Exception {
					adviceCalled.incrementAndGet();
					return callback.execute();
				}

			};
		}

		@Bean
		public MessageChannel annotatedMethodChannel() {
			return new DirectChannel();
		}

		@Bean
		public FooService fooService() {
			return new FooService();
		}

		@Bean
		@ServiceActivator(inputChannel = "annotatedBeanMessageHandlerChannel")
		@IdempotentReceiver("idempotentReceiverInterceptor")
		public MessageHandler messageHandler() {
			return new ServiceActivatingHandler(new MessageProcessor<Object>() {

				@Override
				public Object processMessage(Message<?> message) {
					return message;
				}

			});
		}

		@Bean
		@ServiceActivator(inputChannel = "annotatedBeanMessageHandlerChannel2")
		@IdempotentReceiver("idempotentReceiverInterceptor")
		public MessageHandler messageHandler2() {
			return new MessageHandler() {

				@Override
				public void handleMessage(Message<?> message) throws MessagingException {
					if (message.getHeaders().containsKey(IntegrationMessageHeaderAccessor.DUPLICATE_MESSAGE)) {
						throw new MessageHandlingException(message, "duplicate message has been received");
					}
				}

			};
		}

	}

	@Component
	private static class FooService {

		private final List<Message<?>> messages = new ArrayList<Message<?>>();

		@ServiceActivator(inputChannel = "annotatedMethodChannel")
		@IdempotentReceiver("idempotentReceiverInterceptor")
		public void handle(Message<?> message) {
			this.messages.add(message);
		}

	}

}
