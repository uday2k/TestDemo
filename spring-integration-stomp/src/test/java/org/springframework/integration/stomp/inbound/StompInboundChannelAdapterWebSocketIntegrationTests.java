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

package org.springframework.integration.stomp.inbound;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.event.inbound.ApplicationEventListeningMessageProducer;
import org.springframework.integration.stomp.StompSessionManager;
import org.springframework.integration.stomp.WebSocketStompSessionManager;
import org.springframework.integration.stomp.event.StompConnectionFailedEvent;
import org.springframework.integration.stomp.event.StompIntegrationEvent;
import org.springframework.integration.stomp.event.StompReceiptEvent;
import org.springframework.integration.stomp.event.StompSessionConnectedEvent;
import org.springframework.integration.test.support.LogAdjustingTestSupport;
import org.springframework.integration.websocket.TomcatWebSocketTestServer;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandlingException;
import org.springframework.messaging.PollableChannel;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.broker.SubscriptionRegistry;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.support.AbstractSubscribableChannel;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.config.annotation.AbstractWebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.server.standard.TomcatRequestUpgradeStrategy;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

/**
 * @author Artem Bilan
 * @since 4.2
 */
@ContextConfiguration(classes = StompInboundChannelAdapterWebSocketIntegrationTests.ContextConfiguration.class)
@RunWith(SpringJUnit4ClassRunner.class)
@DirtiesContext
public class StompInboundChannelAdapterWebSocketIntegrationTests extends LogAdjustingTestSupport {

	@Value("#{server.serverContext}")
	private ConfigurableApplicationContext serverContext;

	@Autowired
	@Qualifier("stompInputChannel")
	private PollableChannel stompInputChannel;

	@Autowired
	@Qualifier("errorChannel")
	private PollableChannel errorChannel;

	@Autowired
	@Qualifier("stompEvents")
	private PollableChannel stompEvents;

	@Autowired
	private StompInboundChannelAdapter stompInboundChannelAdapter;

	public StompInboundChannelAdapterWebSocketIntegrationTests() {
		super("org.springframework", "org.springframework.integration.stomp");
	}

	@Test
	public void testWebSocketStompClient() throws Exception {
		Message<?> eventMessage = this.stompEvents.receive(10000);
		assertNotNull(eventMessage);
		assertThat(eventMessage.getPayload(), instanceOf(StompSessionConnectedEvent.class));

		Message<?> receive = this.stompEvents.receive(10000);
		assertNotNull(receive);
		assertThat(receive.getPayload(), instanceOf(StompReceiptEvent.class));
		StompReceiptEvent stompReceiptEvent = (StompReceiptEvent) receive.getPayload();
		assertEquals(StompCommand.SUBSCRIBE, stompReceiptEvent.getStompCommand());
		assertEquals("/topic/myTopic", stompReceiptEvent.getDestination());

		waitForSubscribe("/topic/myTopic");

		SimpMessagingTemplate messagingTemplate = this.serverContext.getBean("brokerMessagingTemplate",
				SimpMessagingTemplate.class);

		StompHeaderAccessor stompHeaderAccessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
		stompHeaderAccessor.setContentType(MediaType.APPLICATION_JSON);
		stompHeaderAccessor.updateStompCommandAsServerMessage();
		stompHeaderAccessor.setLeaveMutable(true);

		messagingTemplate.send("/topic/myTopic",
				MessageBuilder.createMessage("{\"foo\": \"bar\"}".getBytes(), stompHeaderAccessor.getMessageHeaders()));

		receive = this.stompInputChannel.receive(10000);
		assertNotNull(receive);
		assertThat(receive.getPayload(), instanceOf(Map.class));
		@SuppressWarnings("unchecked")
		Map<String, String> payload = (Map<String, String>) receive.getPayload();
		String foo = payload.get("foo");
		assertNotNull(foo);
		assertEquals("bar", foo);

		this.stompInboundChannelAdapter.removeDestination("/topic/myTopic");

		waitForUnsubscribe("/topic/myTopic");

		messagingTemplate.convertAndSend("/topic/myTopic", "foo");
		receive = this.errorChannel.receive(100);
		assertNull(receive);

		this.stompInboundChannelAdapter.addDestination("/topic/myTopic");
		receive = this.stompEvents.receive(10000);
		assertNotNull(receive);

		waitForSubscribe("/topic/myTopic");

		messagingTemplate.convertAndSend("/topic/myTopic", "foo");
		receive = this.errorChannel.receive(10000);
		assertNotNull(receive);
		assertThat(receive, instanceOf(ErrorMessage.class));
		ErrorMessage errorMessage = (ErrorMessage) receive;
		Throwable throwable = errorMessage.getPayload();
		assertThat(throwable, instanceOf(MessageHandlingException.class));
		assertThat(throwable.getCause(), instanceOf(MessageConversionException.class));
		assertThat(throwable.getMessage(), containsString("No suitable converter, payloadType=interface java.util.Map"));

		this.serverContext.close();

		eventMessage = this.stompEvents.receive(10000);
		assertNotNull(eventMessage);
		assertThat(eventMessage.getPayload(), instanceOf(StompConnectionFailedEvent.class));

		this.serverContext.refresh();

		do {
			eventMessage = this.stompEvents.receive(10000);
			assertNotNull(eventMessage);
		}
		while (!(eventMessage.getPayload() instanceof StompSessionConnectedEvent));

		waitForSubscribe("/topic/myTopic");

		messagingTemplate = this.serverContext.getBean("brokerMessagingTemplate", SimpMessagingTemplate.class);
		messagingTemplate.convertAndSend("/topic/myTopic", "foo");
		receive = this.errorChannel.receive(10000);
		assertNotNull(receive);
		assertEquals(0, ((QueueChannel) this.errorChannel).getQueueSize());
	}

	private void waitForSubscribe(String destination) throws InterruptedException {
		SimpleBrokerMessageHandler serverBrokerMessageHandler =
				this.serverContext.getBean("simpleBrokerMessageHandler", SimpleBrokerMessageHandler.class);

		SubscriptionRegistry subscriptionRegistry = serverBrokerMessageHandler.getSubscriptionRegistry();

		int n = 0;
		while (!containsDestination(destination, subscriptionRegistry) && n++ < 100) {
			Thread.sleep(100);
		}

		assertTrue("The subscription for the '" + destination + "' destination hasn't been registered", n < 100);
	}

	private void waitForUnsubscribe(String destination) throws InterruptedException {
		SimpleBrokerMessageHandler serverBrokerMessageHandler =
				this.serverContext.getBean("simpleBrokerMessageHandler", SimpleBrokerMessageHandler.class);

		SubscriptionRegistry subscriptionRegistry = serverBrokerMessageHandler.getSubscriptionRegistry();

		int n = 0;
		while (containsDestination(destination, subscriptionRegistry) && n++ < 100) {
			Thread.sleep(100);
		}

		assertTrue("The subscription for the '" + destination + "' destination hasn't been registered", n < 100);
	}


	private boolean containsDestination(String destination, SubscriptionRegistry subscriptionRegistry) {
		StompHeaderAccessor stompHeaderAccessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
		stompHeaderAccessor.setDestination(destination);
		Message<byte[]> message = MessageBuilder.createMessage(new byte[0], stompHeaderAccessor.toMessageHeaders());
		MultiValueMap<String, String> subscriptions = subscriptionRegistry.findSubscriptions(message);
		return !subscriptions.isEmpty();
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
			webSocketStompClient.setMessageConverter(new MappingJackson2MessageConverter());
			webSocketStompClient.setTaskScheduler(taskScheduler);
			return webSocketStompClient;
		}

		@Bean
		public StompSessionManager stompSessionManager(WebSocketStompClient stompClient) {
			WebSocketStompSessionManager webSocketStompSessionManager =
					new WebSocketStompSessionManager(stompClient, server().getWsBaseUrl() + "/ws");
			webSocketStompSessionManager.setAutoReceipt(true);
			webSocketStompSessionManager.setRecoveryInterval(1000);
			StompHeaders stompHeaders = new StompHeaders();
			stompHeaders.setHeartbeat(new long[] {10000, 10000});
			webSocketStompSessionManager.setConnectHeaders(stompHeaders);
			return webSocketStompSessionManager;
		}

		@Bean
		public PollableChannel stompInputChannel() {
			return new QueueChannel();
		}

		@Bean
		public PollableChannel errorChannel() {
			return new QueueChannel();
		}

		@Bean
		public StompInboundChannelAdapter stompInboundChannelAdapter(StompSessionManager stompSessionFactory) {
			StompInboundChannelAdapter adapter = new StompInboundChannelAdapter(stompSessionFactory, "/topic/myTopic");
			adapter.setPayloadType(Map.class);
			adapter.setOutputChannel(stompInputChannel());
			adapter.setErrorChannel(errorChannel());
			return adapter;
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

	@Configuration
	@EnableWebSocketMessageBroker
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

		//TODO SimpleBrokerMessageHandler doesn't support RECEIPT frame, hence we emulate it this way
		@Bean
		@SuppressWarnings("unchecked")
		public ApplicationListener<SessionSubscribeEvent> webSocketEventListener(
				final AbstractSubscribableChannel clientOutboundChannel) {
			return new ApplicationListener<SessionSubscribeEvent>() {

				@Override
				public void onApplicationEvent(SessionSubscribeEvent event) {
					Message<byte[]> message = event.getMessage();
					StompHeaderAccessor stompHeaderAccessor = StompHeaderAccessor.wrap(message);
					if (stompHeaderAccessor.getReceipt() != null) {
						stompHeaderAccessor.setHeader("stompCommand", StompCommand.RECEIPT);
						stompHeaderAccessor.setReceiptId(stompHeaderAccessor.getReceipt());
						clientOutboundChannel.send(
								MessageBuilder.createMessage(new byte[0], stompHeaderAccessor.getMessageHeaders()));
					}
				}

			};
		}

	}

}
