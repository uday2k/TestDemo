/*
 * Copyright 2015 the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.springframework.integration.stomp.outbound;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.event.inbound.ApplicationEventListeningMessageProducer;
import org.springframework.integration.stomp.StompSessionManager;
import org.springframework.integration.stomp.WebSocketStompSessionManager;
import org.springframework.integration.stomp.event.StompExceptionEvent;
import org.springframework.integration.stomp.event.StompIntegrationEvent;
import org.springframework.integration.stomp.event.StompReceiptEvent;
import org.springframework.integration.stomp.event.StompSessionConnectedEvent;
import org.springframework.integration.test.support.LogAdjustingTestSupport;
import org.springframework.integration.test.support.LongRunningIntegrationTest;
import org.springframework.integration.test.util.TestUtils;
import org.springframework.integration.websocket.TomcatWebSocketTestServer;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.PollableChannel;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptorAdapter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Controller;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.config.annotation.AbstractWebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.server.standard.TomcatRequestUpgradeStrategy;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

/**
 * @author Artem Bilan
 * @author Gary Russell
 * @since 4.2
 */
@ContextConfiguration(classes = StompMessageHandlerWebSocketIntegrationTests.ContextConfiguration.class)
@RunWith(SpringJUnit4ClassRunner.class)
@DirtiesContext
public class StompMessageHandlerWebSocketIntegrationTests extends LogAdjustingTestSupport {

	@ClassRule
	public static LongRunningIntegrationTest longTests = new LongRunningIntegrationTest();

	@Value("#{server.serverContext}")
	private ApplicationContext serverContext;

	@Autowired
	@Qualifier("webSocketOutputChannel")
	private MessageChannel webSocketOutputChannel;

	@Autowired
	@Qualifier("stompEvents")
	private PollableChannel stompEvents;

	public StompMessageHandlerWebSocketIntegrationTests() {
		super("org.springframework", "org.springframework.integration.stomp");
	}

	@Test
	public void testStompMessageHandler() throws InterruptedException {
		StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SEND);
		headers.setDestination("/app/simple");
		Message<String> message = MessageBuilder.withPayload("foo").setHeaders(headers).build();
		this.webSocketOutputChannel.send(message);

		SimpleController controller = this.serverContext.getBean(SimpleController.class);
		assertTrue(controller.latch.await(10, TimeUnit.SECONDS));

		Message<?> receive = this.stompEvents.receive(10000);
		assertNotNull(receive);
		assertThat(receive.getPayload(), instanceOf(StompSessionConnectedEvent.class));

		// Simple Broker Relay doesn't support RECEIPT Frame, so we check here the 'lost' StompReceiptEvent
		receive = this.stompEvents.receive(10000);
		assertNotNull(receive);
		assertThat(receive.getPayload(), instanceOf(StompReceiptEvent.class));
		StompReceiptEvent stompReceiptEvent = (StompReceiptEvent) receive.getPayload();
		assertEquals(StompCommand.SEND, stompReceiptEvent.getStompCommand());
		assertEquals("/app/simple", stompReceiptEvent.getDestination());
		assertTrue(stompReceiptEvent.isLost());
		assertNotNull(stompReceiptEvent.getMessage());

		headers = StompHeaderAccessor.create(StompCommand.SEND);
		headers.setDestination("/foo");
		message = MessageBuilder.withPayload("bar").setHeaders(headers).build();
		this.webSocketOutputChannel.send(message);

		receive = this.stompEvents.receive(10000);
		assertNotNull(receive);
		assertThat(receive.getPayload(), instanceOf(StompExceptionEvent.class));
		StompExceptionEvent stompExceptionEvent = (StompExceptionEvent) receive.getPayload();
		Throwable cause = stompExceptionEvent.getCause();
		assertThat(cause, instanceOf(MessageDeliveryException.class));
		MessageDeliveryException messageDeliveryException = (MessageDeliveryException) cause;
		Message<?> failedMessage = messageDeliveryException.getFailedMessage();
		assertThat((String) failedMessage.getPayload(), containsString("preSend intentional Exception"));

		receive = this.stompEvents.receive(10000);
		assertNotNull(receive);
		assertThat(receive.getPayload(), instanceOf(StompReceiptEvent.class));
		stompReceiptEvent = (StompReceiptEvent) receive.getPayload();
		assertEquals(StompCommand.SEND, stompReceiptEvent.getStompCommand());
		assertEquals("/foo", stompReceiptEvent.getDestination());
		assertTrue(stompReceiptEvent.isLost());
	}

	// STOMP Client

	@Configuration
	@EnableIntegration
	public static class ContextConfiguration {

		@Bean
		public TomcatWebSocketTestServer server() {
			return new TomcatWebSocketTestServer(ServerConfig.class);
		}

		@Bean
		public WebSocketClient webSocketClient() {
			return new SockJsClient(Collections.<Transport>singletonList(new WebSocketTransport(new StandardWebSocketClient())));
		}

		@Bean
		public WebSocketStompClient stompClient(TaskScheduler taskScheduler) {
			WebSocketStompClient webSocketStompClient = new WebSocketStompClient(webSocketClient());
			webSocketStompClient.setTaskScheduler(taskScheduler);
			webSocketStompClient.setReceiptTimeLimit(5000);
			webSocketStompClient.setMessageConverter(new StringMessageConverter());
			return webSocketStompClient;
		}

		@Bean
		public StompSessionManager stompSessionManager(WebSocketStompClient stompClient) {
			WebSocketStompSessionManager webSocketStompSessionManager =
					new WebSocketStompSessionManager(stompClient, server().getWsBaseUrl() + "/ws");
			webSocketStompSessionManager.setAutoReceipt(true);
			webSocketStompSessionManager.setRecoveryInterval(1000);
			return webSocketStompSessionManager;
		}

		@Bean
		@ServiceActivator(inputChannel = "webSocketOutputChannel")
		public MessageHandler stompMessageHandler(StompSessionManager stompSessionManager) {
			StompMessageHandler stompMessageHandler = new StompMessageHandler(stompSessionManager);
			stompMessageHandler.setConnectTimeout(10000);
			return stompMessageHandler;
		}

		@Bean
		public PollableChannel stompEvents() {
			return new QueueChannel();
		}

		@Bean
		@SuppressWarnings("unchecked")
		public ApplicationListener<ApplicationEvent> stompEventListener() {
			ApplicationEventListeningMessageProducer producer = new ApplicationEventListeningMessageProducer();
			producer.setEventTypes(StompIntegrationEvent.class);
			producer.setOutputChannel(stompEvents());
			return producer;
		}

	}

	// WebSocket Server part

	@Target({ElementType.TYPE})
	@Retention(RetentionPolicy.RUNTIME)
	@Controller
	private @interface IntegrationTestController {
	}

	@IntegrationTestController
	static class SimpleController {

		private final CountDownLatch latch = new CountDownLatch(1);

		@MessageMapping(value = "/simple")
		public void handle() {
			this.latch.countDown();
		}

	}

	@Configuration
	@EnableWebSocketMessageBroker
	@ComponentScan(
			basePackageClasses = StompMessageHandlerWebSocketIntegrationTests.class,
			useDefaultFilters = false,
			includeFilters = @ComponentScan.Filter(IntegrationTestController.class))
	static class ServerConfig extends AbstractWebSocketMessageBrokerConfigurer {

		@Bean
		public DefaultHandshakeHandler handshakeHandler() {
			return new DefaultHandshakeHandler(new TomcatRequestUpgradeStrategy());
		}

		@Override
		public void registerStompEndpoints(StompEndpointRegistry registry) {
			registry.addEndpoint("/ws").setHandshakeHandler(handshakeHandler()).withSockJS();
		}

		@Override
		public void configureMessageBroker(MessageBrokerRegistry configurer) {
			configurer.setApplicationDestinationPrefixes("/app");
			configurer.enableSimpleBroker("/topic", "/queue");
		}


		@Override
		public void configureClientInboundChannel(ChannelRegistration registration) {
			registration.setInterceptors(new ChannelInterceptorAdapter() {

				private final AtomicBoolean invoked = new AtomicBoolean();

				@Override
				public Message<?> preSend(Message<?> message, MessageChannel channel) {
					if (StompCommand.CONNECT.equals(StompHeaderAccessor.wrap(message).getCommand()) ||
							this.invoked.compareAndSet(false, true)) {
						return super.preSend(message, channel);
					}
					throw new RuntimeException("preSend intentional Exception");
				}

			});
		}

	}

}
