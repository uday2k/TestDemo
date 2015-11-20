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

package org.springframework.integration.configuration;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.aopalliance.intercept.MethodInterceptor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import org.springframework.beans.DirectFieldAccessor;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.AbstractFactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.Lifecycle;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.integration.annotation.Aggregator;
import org.springframework.integration.annotation.BridgeFrom;
import org.springframework.integration.annotation.BridgeTo;
import org.springframework.integration.annotation.Gateway;
import org.springframework.integration.annotation.GatewayHeader;
import org.springframework.integration.annotation.InboundChannelAdapter;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.annotation.Poller;
import org.springframework.integration.annotation.Publisher;
import org.springframework.integration.annotation.Role;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.annotation.Transformer;
import org.springframework.integration.channel.AbstractMessageChannel;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.NullChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.channel.interceptor.WireTap;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.config.EnableMessageHistory;
import org.springframework.integration.config.EnablePublisher;
import org.springframework.integration.config.ExpressionControlBusFactoryBean;
import org.springframework.integration.config.GlobalChannelInterceptor;
import org.springframework.integration.config.IntegrationConverter;
import org.springframework.integration.core.MessagingTemplate;
import org.springframework.integration.endpoint.AbstractEndpoint;
import org.springframework.integration.endpoint.MethodInvokingMessageSource;
import org.springframework.integration.endpoint.PollingConsumer;
import org.springframework.integration.gateway.GatewayProxyFactoryBean;
import org.springframework.integration.history.MessageHistory;
import org.springframework.integration.history.MessageHistoryConfigurer;
import org.springframework.integration.scheduling.PollerMetadata;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.integration.support.MutableMessageBuilder;
import org.springframework.integration.support.SmartLifecycleRoleController;
import org.springframework.integration.test.util.LogAdjustingTestSupport;
import org.springframework.integration.test.util.TestUtils;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.PollableChannel;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.ChannelInterceptorAdapter;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Component;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;
import org.springframework.util.MultiValueMap;

import reactor.Environment;
import reactor.rx.Promise;
import reactor.rx.Streams;
import reactor.spring.context.config.EnableReactor;

/**
 * @author Artem Bilan
 * @author Gary Russell
 * @since 4.0
 */
@ContextConfiguration(loader = AnnotationConfigContextLoader.class,
		classes = {EnableIntegrationTests.ContextConfiguration.class, EnableIntegrationTests.ContextConfiguration2.class})
@RunWith(SpringJUnit4ClassRunner.class)
@DirtiesContext
public class EnableIntegrationTests {

	@Autowired
	private ApplicationContext context;

	@Autowired
	private PollableChannel input;

	@Autowired
	private SmartLifecycleRoleController roleController;

	@Autowired
	@Qualifier("annotationTestService.handle.serviceActivator")
	private PollingConsumer serviceActivatorEndpoint;

	@Autowired
	@Qualifier("annotationTestService.handle1.serviceActivator")
	private PollingConsumer serviceActivatorEndpoint1;

	@Autowired
	@Qualifier("annotationTestService.handle2.serviceActivator")
	private PollingConsumer serviceActivatorEndpoint2;

	@Autowired
	@Qualifier("annotationTestService.handle3.serviceActivator")
	private PollingConsumer serviceActivatorEndpoint3;

	@Autowired
	@Qualifier("annotationTestService.handle4.serviceActivator")
	private PollingConsumer serviceActivatorEndpoint4;

	@Autowired
	@Qualifier("annotationTestService.transform.transformer")
	private PollingConsumer transformer;

	@Autowired
	@Qualifier("annotationTestService")
	private Lifecycle annotationTestService;

	@Autowired
	private Trigger myTrigger;

	@Autowired
	private QueueChannel output;

	@Autowired
	private PollableChannel publishedChannel;

	@Autowired
	private PollableChannel wireTapChannel;

	@Autowired
	private MessageHistoryConfigurer configurer;

	@Autowired
	private TestGateway testGateway;

	@Autowired
	private CountDownLatch asyncAnnotationProcessLatch;

	@Autowired
	private AtomicReference<Thread> asyncAnnotationProcessThread;

	@Autowired
	private TestGateway2 testGateway2;

	@Autowired
	private TestChannelInterceptor testChannelInterceptor;

	@Autowired
	private AtomicInteger fbInterceptorCounter;

	@Autowired
	private MessageChannel numberChannel;

	@Autowired
	private TestConverter testConverter;

	@Autowired
	private MessageChannel bytesChannel;

	@Autowired
	private PollableChannel counterChannel;

	@Autowired
	private PollableChannel messageChannel;

	@Autowired
	private PollableChannel fooChannel;

	@Autowired
	private MessageChannel bridgeInput;

	@Autowired
	private PollableChannel bridgeOutput;

	@Autowired
	private MessageChannel pollableBridgeInput;

	@Autowired
	private PollableChannel pollableBridgeOutput;

	@Autowired
	private MessageChannel metaBridgeInput;

	@Autowired
	private PollableChannel metaBridgeOutput;

	@Autowired
	private MessageChannel bridgeToInput;

	@Autowired
	private PollableChannel bridgeToOutput;

	@Autowired
	private PollableChannel pollableBridgeToInput;

	@Autowired
	private MessageChannel myBridgeToInput;

	@Autowired
	private MessageChannel controlBusChannel;

	@Autowired
	@Qualifier("enableIntegrationTests.ContextConfiguration2.sendAsyncHandler.serviceActivator")
	private AbstractEndpoint sendAsyncHandler;

	@Autowired
	private CountDownLatch inputReceiveLatch;

	@Test
	public void testAnnotatedServiceActivator() throws Exception {
		this.serviceActivatorEndpoint.setReceiveTimeout(10000);
		this.serviceActivatorEndpoint.start();
		assertTrue(this.inputReceiveLatch.await(10, TimeUnit.SECONDS));

		assertEquals(10L, TestUtils.getPropertyValue(this.serviceActivatorEndpoint, "maxMessagesPerPoll"));

		Trigger trigger = TestUtils.getPropertyValue(this.serviceActivatorEndpoint, "trigger", Trigger.class);
		assertThat(trigger, Matchers.instanceOf(PeriodicTrigger.class));
		assertEquals(100L, TestUtils.getPropertyValue(trigger, "period"));
		assertFalse(TestUtils.getPropertyValue(trigger, "fixedRate", Boolean.class));

		assertTrue(this.annotationTestService.isRunning());
		Log logger = spy(TestUtils.getPropertyValue(this.serviceActivatorEndpoint, "logger", Log.class));
		when(logger.isDebugEnabled()).thenReturn(true);
		final CountDownLatch pollerInterruptedLatch = new CountDownLatch(1);
		doAnswer(new Answer<Void>() {

			@Override
			public Void answer(InvocationOnMock invocation) throws Throwable {
				pollerInterruptedLatch.countDown();
				invocation.callRealMethod();
				return null;
			}
		}).when(logger).debug("Received no Message during the poll, returning 'false'");
		new DirectFieldAccessor(this.serviceActivatorEndpoint).setPropertyValue("logger", logger);

		this.serviceActivatorEndpoint.stop();
		assertFalse(this.annotationTestService.isRunning());

		// wait until the service activator's poller is interrupted.
		assertTrue(pollerInterruptedLatch.await(10, TimeUnit.SECONDS));
		this.serviceActivatorEndpoint.start();
		assertTrue(this.annotationTestService.isRunning());

		trigger = TestUtils.getPropertyValue(this.serviceActivatorEndpoint1, "trigger", Trigger.class);
		assertThat(trigger, Matchers.instanceOf(PeriodicTrigger.class));
		assertEquals(100L, TestUtils.getPropertyValue(trigger, "period"));
		assertTrue(TestUtils.getPropertyValue(trigger, "fixedRate", Boolean.class));

		trigger = TestUtils.getPropertyValue(this.serviceActivatorEndpoint2, "trigger", Trigger.class);
		assertThat(trigger, Matchers.instanceOf(CronTrigger.class));
		assertEquals("0 5 7 * * *", TestUtils.getPropertyValue(trigger, "sequenceGenerator.expression"));

		trigger = TestUtils.getPropertyValue(this.serviceActivatorEndpoint3, "trigger", Trigger.class);
		assertThat(trigger, Matchers.instanceOf(PeriodicTrigger.class));
		assertEquals(11L, TestUtils.getPropertyValue(trigger, "period"));
		assertFalse(TestUtils.getPropertyValue(trigger, "fixedRate", Boolean.class));

		trigger = TestUtils.getPropertyValue(this.serviceActivatorEndpoint4, "trigger", Trigger.class);
		assertThat(trigger, Matchers.instanceOf(PeriodicTrigger.class));
		assertEquals(1000L, TestUtils.getPropertyValue(trigger, "period"));
		assertFalse(TestUtils.getPropertyValue(trigger, "fixedRate", Boolean.class));
		assertSame(this.myTrigger, trigger);

		trigger = TestUtils.getPropertyValue(this.transformer, "trigger", Trigger.class);
		assertThat(trigger, Matchers.instanceOf(PeriodicTrigger.class));
		assertEquals(10L, TestUtils.getPropertyValue(trigger, "period"));
		assertFalse(TestUtils.getPropertyValue(trigger, "fixedRate", Boolean.class));

		this.input.send(MessageBuilder.withPayload("Foo").build());

		Message<?> interceptedMessage = this.wireTapChannel.receive(10000);
		assertNotNull(interceptedMessage);
		assertEquals("Foo", interceptedMessage.getPayload());

		Message<?> receive = this.output.receive(10000);
		assertNotNull(receive);
		assertEquals("FOO", receive.getPayload());

		MessageHistory messageHistory = receive.getHeaders().get(MessageHistory.HEADER_NAME, MessageHistory.class);
		assertNotNull(messageHistory);
		String messageHistoryString = messageHistory.toString();
		assertThat(messageHistoryString, Matchers.containsString("input"));
		assertThat(messageHistoryString,
				Matchers.containsString("annotationTestService.handle.serviceActivator.handler"));
		assertThat(messageHistoryString, Matchers.not(Matchers.containsString("output")));

		receive = this.publishedChannel.receive(10000);

		assertNotNull(receive);
		assertEquals("foo", receive.getPayload());

		messageHistory = receive.getHeaders().get(MessageHistory.HEADER_NAME, MessageHistory.class);
		assertNotNull(messageHistory);
		messageHistoryString = messageHistory.toString();
		assertThat(messageHistoryString, Matchers.not(Matchers.containsString("input")));
		assertThat(messageHistoryString, Matchers.not(Matchers.containsString("output")));
		assertThat(messageHistoryString, Matchers.containsString("publishedChannel"));

		assertNull(this.wireTapChannel.receive(0));
		assertThat(this.testChannelInterceptor.getInvoked(), Matchers.greaterThan(0));
		assertThat(this.fbInterceptorCounter.get(), Matchers.greaterThan(0));

		assertTrue(this.context.containsBean("annotationTestService.count.inboundChannelAdapter.source"));
		Object messageSource = this.context.getBean("annotationTestService.count.inboundChannelAdapter.source");
		assertThat(messageSource, Matchers.instanceOf(MethodInvokingMessageSource.class));

		assertNull(this.counterChannel.receive(10));

		SmartLifecycle countSA = this.context.getBean("annotationTestService.count.inboundChannelAdapter",
				SmartLifecycle.class);
		assertFalse(countSA.isAutoStartup());
		assertEquals(23, countSA.getPhase());
		countSA.start();

		for (int i = 0; i < 10; i++) {
			Message<?> message = this.counterChannel.receive(1000);
			assertNotNull(message);
			assertEquals(i + 1, message.getPayload());
		}

		Message<?> message = this.fooChannel.receive(1000);
		assertNotNull(message);
		assertEquals("foo", message.getPayload());
		message = this.fooChannel.receive(1000);
		assertNotNull(message);
		assertEquals("foo", message.getPayload());
		assertNull(this.fooChannel.receive(10));

		message = this.messageChannel.receive(1000);
		assertNotNull(message);
		assertEquals("bar", message.getPayload());
		assertTrue(message.getHeaders().containsKey("foo"));
		assertEquals("FOO", message.getHeaders().get("foo"));

		MessagingTemplate messagingTemplate = new MessagingTemplate(this.controlBusChannel);
		assertFalse(messagingTemplate.convertSendAndReceive("@lifecycle.isRunning()", Boolean.class));
		this.controlBusChannel.send(new GenericMessage<String>("@lifecycle.start()"));
		assertTrue(messagingTemplate.convertSendAndReceive("@lifecycle.isRunning()", Boolean.class));
		this.controlBusChannel.send(new GenericMessage<String>("@lifecycle.stop()"));
		assertFalse(messagingTemplate.convertSendAndReceive("@lifecycle.isRunning()", Boolean.class));
	}

	@Test
	@DirtiesContext
	public void testChangePatterns() {
		try {
			this.configurer.setComponentNamePatterns(new String[] {"*"});
			fail("ExpectedException");
		}
		catch (IllegalStateException e) {
			assertThat(e.getMessage(), containsString("cannot be changed"));
		}
		this.configurer.stop();
		this.configurer.setComponentNamePatterns(new String[] {"*"});
		assertEquals("*", TestUtils.getPropertyValue(this.configurer, "componentNamePatterns", String[].class)[0]);
	}

	@Test
	public void testMessagingGateway() throws InterruptedException {
		String payload = "bar";
		assertEquals(payload.toUpperCase(), this.testGateway.echo(payload));
		assertEquals(payload.toUpperCase() + "2", this.testGateway2.echo2(payload));
		this.testGateway.sendAsync("foo");
		assertTrue(this.asyncAnnotationProcessLatch.await(1, TimeUnit.SECONDS));
		assertNotSame(Thread.currentThread(), this.asyncAnnotationProcessThread.get());
	}

	@Test
	public void testParentChildAnnotationConfiguration() {
		AnnotationConfigApplicationContext child = new AnnotationConfigApplicationContext();
		child.register(ChildConfiguration.class);
		child.setParent(this.context);
		child.refresh();
		AbstractMessageChannel foo = child.getBean("foo", AbstractMessageChannel.class);
		ChannelInterceptor baz = child.getBean("baz", ChannelInterceptor.class);
		assertTrue(foo.getChannelInterceptors().contains(baz));
		assertFalse(this.output.getChannelInterceptors().contains(baz));
		child.close();
	}

	@Test
	public void testParentChildAnnotationConfigurationFromAnotherPackage() {
		AnnotationConfigApplicationContext child = new AnnotationConfigApplicationContext();
		child.register(org.springframework.integration.configuration2.ChildConfiguration.class);
		child.setParent(this.context);
		child.refresh();
		AbstractMessageChannel foo = child.getBean("foo", AbstractMessageChannel.class);
		ChannelInterceptor baz = child.getBean("baz", ChannelInterceptor.class);
		assertTrue(foo.getChannelInterceptors().contains(baz));
		assertFalse(this.output.getChannelInterceptors().contains(baz));
		child.close();
	}

	@Test
	public void testIntegrationConverter() {
		this.numberChannel.send(new GenericMessage<Integer>(10));
		this.numberChannel.send(new GenericMessage<Boolean>(true));
		assertThat(this.testConverter.getInvoked(), Matchers.greaterThan(0));

		assertTrue(this.bytesChannel.send(new GenericMessage<byte[]>("foo".getBytes())));
		assertTrue(this.bytesChannel.send(new GenericMessage<>(MutableMessageBuilder.withPayload("").build())));

	}

	@Test
	public void testMetaAnnotations() {

		assertEquals(2, this.context.getBeanNamesForType(GatewayProxyFactoryBean.class).length);

		PollingConsumer consumer = this.context.getBean("annotationTestService.annCount.serviceActivator",
				PollingConsumer.class);
		assertFalse(TestUtils.getPropertyValue(consumer, "autoStartup", Boolean.class));
		assertEquals(23, TestUtils.getPropertyValue(consumer, "phase"));
		assertSame(context.getBean("annInput"), TestUtils.getPropertyValue(consumer, "inputChannel"));
		assertEquals("annOutput", TestUtils.getPropertyValue(consumer, "handler.outputChannelName"));
		assertSame(context.getBean("annAdvice"), TestUtils.getPropertyValue(consumer,
				"handler.adviceChain", List.class).get(0));
		assertEquals(1000L, TestUtils.getPropertyValue(consumer, "trigger.period"));

		consumer = this.context.getBean("annotationTestService.annCount1.serviceActivator",
				PollingConsumer.class);
		consumer.stop();
		assertTrue(TestUtils.getPropertyValue(consumer, "autoStartup", Boolean.class));
		assertEquals(23, TestUtils.getPropertyValue(consumer, "phase"));
		assertSame(context.getBean("annInput1"), TestUtils.getPropertyValue(consumer, "inputChannel"));
		assertEquals("annOutput", TestUtils.getPropertyValue(consumer, "handler.outputChannelName"));
		assertSame(context.getBean("annAdvice1"), TestUtils.getPropertyValue(consumer,
				"handler.adviceChain", List.class).get(0));
		assertEquals(2000L, TestUtils.getPropertyValue(consumer, "trigger.period"));

		consumer = this.context.getBean("annotationTestService.annCount2.serviceActivator",
				PollingConsumer.class);
		assertFalse(TestUtils.getPropertyValue(consumer, "autoStartup", Boolean.class));
		assertEquals(23, TestUtils.getPropertyValue(consumer, "phase"));
		assertSame(context.getBean("annInput"), TestUtils.getPropertyValue(consumer, "inputChannel"));
		assertEquals("annOutput", TestUtils.getPropertyValue(consumer, "handler.outputChannelName"));
		assertSame(context.getBean("annAdvice"), TestUtils.getPropertyValue(consumer,
				"handler.adviceChain", List.class).get(0));
		assertEquals(1000L, TestUtils.getPropertyValue(consumer, "trigger.period"));

		// Tests when the channel is in a "middle" annotation
		consumer = this.context.getBean("annotationTestService.annCount5.serviceActivator", PollingConsumer.class);
		assertFalse(TestUtils.getPropertyValue(consumer, "autoStartup", Boolean.class));
		assertEquals(23, TestUtils.getPropertyValue(consumer, "phase"));
		assertSame(context.getBean("annInput3"), TestUtils.getPropertyValue(consumer, "inputChannel"));
		assertEquals("annOutput", TestUtils.getPropertyValue(consumer, "handler.outputChannelName"));
		assertSame(context.getBean("annAdvice"), TestUtils.getPropertyValue(consumer,
				"handler.adviceChain", List.class).get(0));
		assertEquals(1000L, TestUtils.getPropertyValue(consumer, "trigger.period"));

		consumer = this.context.getBean("annotationTestService.annAgg1.aggregator", PollingConsumer.class);
		assertFalse(TestUtils.getPropertyValue(consumer, "autoStartup", Boolean.class));
		assertEquals(23, TestUtils.getPropertyValue(consumer, "phase"));
		assertSame(context.getBean("annInput"), TestUtils.getPropertyValue(consumer, "inputChannel"));
		assertEquals("annOutput", TestUtils.getPropertyValue(consumer, "handler.outputChannelName"));
		assertEquals("annOutput", TestUtils.getPropertyValue(consumer, "handler.discardChannelName"));
		assertEquals(1000L, TestUtils.getPropertyValue(consumer, "trigger.period"));
		assertEquals(-1L, TestUtils.getPropertyValue(consumer, "handler.messagingTemplate.sendTimeout"));
		assertFalse(TestUtils.getPropertyValue(consumer, "handler.sendPartialResultOnExpiry", Boolean.class));

		consumer = this.context.getBean("annotationTestService.annAgg2.aggregator", PollingConsumer.class);
		assertFalse(TestUtils.getPropertyValue(consumer, "autoStartup", Boolean.class));
		assertEquals(23, TestUtils.getPropertyValue(consumer, "phase"));
		assertSame(context.getBean("annInput"), TestUtils.getPropertyValue(consumer, "inputChannel"));
		assertEquals("annOutput", TestUtils.getPropertyValue(consumer, "handler.outputChannelName"));
		assertEquals("annOutput", TestUtils.getPropertyValue(consumer, "handler.discardChannelName"));
		assertEquals(1000L, TestUtils.getPropertyValue(consumer, "trigger.period"));
		assertEquals(75L, TestUtils.getPropertyValue(consumer, "handler.messagingTemplate.sendTimeout"));
		assertTrue(TestUtils.getPropertyValue(consumer, "handler.sendPartialResultOnExpiry", Boolean.class));
	}

	@Test
	public void testBridgeAnnotations() {
		GenericMessage<?> testMessage = new GenericMessage<Object>("foo");
		this.bridgeInput.send(testMessage);
		Message<?> receive = this.bridgeOutput.receive(2000);
		assertNotNull(receive);
		assertSame(receive, testMessage);
		assertNull(this.bridgeOutput.receive(10));

		this.pollableBridgeInput.send(testMessage);
		receive = this.pollableBridgeOutput.receive(2000);
		assertNotNull(receive);
		assertSame(receive, testMessage);
		assertNull(this.pollableBridgeOutput.receive(10));

		try {
			this.metaBridgeInput.send(testMessage);
			fail("MessageDeliveryException expected");
		}
		catch (Exception e) {
			assertThat(e, Matchers.instanceOf(MessageDeliveryException.class));
			assertThat(e.getMessage(), Matchers.containsString("Dispatcher has no subscribers"));
		}

		this.context.getBean("enableIntegrationTests.ContextConfiguration.metaBridgeOutput.bridgeFrom",
				Lifecycle.class).start();

		this.metaBridgeInput.send(testMessage);
		receive = this.metaBridgeOutput.receive(2000);
		assertNotNull(receive);
		assertSame(receive, testMessage);
		assertNull(this.metaBridgeOutput.receive(10));

		this.bridgeToInput.send(testMessage);
		receive = this.bridgeToOutput.receive(2000);
		assertNotNull(receive);
		assertSame(receive, testMessage);
		assertNull(this.bridgeToOutput.receive(10));

		PollableChannel replyChannel = new QueueChannel();
		Message<?> bridgeMessage = MessageBuilder.fromMessage(testMessage).setReplyChannel(replyChannel).build();
		this.pollableBridgeToInput.send(bridgeMessage);
		receive = replyChannel.receive(2000);
		assertNotNull(receive);
		assertSame(receive, bridgeMessage);
		assertNull(replyChannel.receive(10));

		try {
			this.myBridgeToInput.send(testMessage);
			fail("MessageDeliveryException expected");
		}
		catch (Exception e) {
			assertThat(e, Matchers.instanceOf(MessageDeliveryException.class));
			assertThat(e.getMessage(), Matchers.containsString("Dispatcher has no subscribers"));
		}

		this.context.getBean("enableIntegrationTests.ContextConfiguration.myBridgeToInput.bridgeTo",
				Lifecycle.class).start();

		this.myBridgeToInput.send(bridgeMessage);
		receive = replyChannel.receive(2000);
		assertNotNull(receive);
		assertSame(receive, bridgeMessage);
		assertNull(replyChannel.receive(10));
	}

	@Autowired
	private Environment environment;

	@Test
	public void testPromiseGateway() throws Exception {

		final AtomicReference<List<Integer>> ref = new AtomicReference<List<Integer>>();
		final CountDownLatch consumeLatch = new CountDownLatch(1);

		Streams.just("1", "2", "3", "4", "5")
				.dispatchOn(this.environment)
				.map(Integer::parseInt)
				.flatMap(this.testGateway::multiply)
				.toList()
				.onSuccess(integers -> {
					ref.set(integers);
					consumeLatch.countDown();
				});


		assertTrue(consumeLatch.await(2, TimeUnit.SECONDS));

		List<Integer> integers = ref.get();
		assertEquals(5, integers.size());

		assertThat(integers, Matchers.<Integer>contains(2, 4, 6, 8, 10));
	}

	@Test
	@DirtiesContext
	public void testRoles() {
		this.roleController.stopLifecyclesInRole("foo");
		@SuppressWarnings("unchecked")
		MultiValueMap<String, SmartLifecycle> lifecycles = TestUtils.getPropertyValue(this.roleController,
				"lifecycles", MultiValueMap.class);
		assertEquals(2, lifecycles.size());
		assertEquals(2, lifecycles.get("foo").size());
		assertEquals(1, lifecycles.get("bar").size());
		assertFalse(this.serviceActivatorEndpoint.isRunning());
		assertFalse(this.sendAsyncHandler.isRunning());
		assertEquals(2, lifecycles.size());
		assertEquals(2, lifecycles.get("foo").size());
	}

	@Configuration
	@ComponentScan
	@IntegrationComponentScan
	@EnableIntegration
//	INT-3853 @PropertySource("classpath:org/springframework/integration/configuration/EnableIntegrationTests.properties")
	@EnableMessageHistory({"input", "publishedChannel", "annotationTestService*"})
	public static class ContextConfiguration {

		@Bean
		public CountDownLatch inputReceiveLatch() {
			return new CountDownLatch(1);
		}

		@Bean
		public QueueChannel input() {
			return new QueueChannel() {

				@Override
				protected Message<?> doReceive(long timeout) {
					inputReceiveLatch().countDown();
					return super.doReceive(timeout);
				}
			};
		}

		@Bean
		public QueueChannel input1() {
			return new QueueChannel();
		}

		@Bean
		public QueueChannel input2() {
			return new QueueChannel();
		}

		@Bean
		public QueueChannel input3() {
			return new QueueChannel();
		}

		@Bean
		public QueueChannel input4() {
			return new QueueChannel();
		}

		@Bean
		public Trigger myTrigger() {
			return new PeriodicTrigger(1000L);
		}

		@Bean
		public Trigger onlyOnceTrigger() {
			return new Trigger() {

				private final AtomicBoolean invoked = new AtomicBoolean();

				@Override
				public Date nextExecutionTime(TriggerContext triggerContext) {
					return this.invoked.getAndSet(true) ? null : new Date();
				}
			};
		}

		@Bean
		public PollableChannel output() {
			return new QueueChannel();
		}

		@Bean
		public PollableChannel wireTapChannel() {
			return new QueueChannel();
		}


		@Bean
		@GlobalChannelInterceptor(patterns = "input")
		public WireTap wireTap() {
			return new WireTap(this.wireTapChannel());
		}

		@Bean
		public AtomicInteger fbInterceptorCounter() {
			return new AtomicInteger();
		}

		@Bean
		@GlobalChannelInterceptor
		public FactoryBean<ChannelInterceptor> ciFactoryBean() {
			return new AbstractFactoryBean<ChannelInterceptor>() {

				@Override
				public Class<?> getObjectType() {
					return ChannelInterceptor.class;
				}

				@Override
				protected ChannelInterceptor createInstance() throws Exception {
					return new ChannelInterceptorAdapter() {

						@Override
						public Message<?> preSend(Message<?> message, MessageChannel channel) {
							fbInterceptorCounter().incrementAndGet();
							return super.preSend(message, channel);
						}
					};
				}
			};
		}

		@Bean
		@BridgeFrom("bridgeInput")
		public QueueChannel bridgeOutput() {
			return new QueueChannel();
		}

		@Bean
		public PollableChannel pollableBridgeInput() {
			return new QueueChannel();
		}


		@Bean
		@BridgeFrom(value = "pollableBridgeInput", poller = @Poller(fixedDelay = "1000"))
		public QueueChannel pollableBridgeOutput() {
			return new QueueChannel();
		}

		@Bean
		@MyBridgeFrom
		public QueueChannel metaBridgeOutput() {
			return new QueueChannel();
		}

		@Bean
		public QueueChannel bridgeToOutput() {
			return new QueueChannel();
		}

		@Bean
		@BridgeTo("bridgeToOutput")
		public MessageChannel bridgeToInput() {
			return new DirectChannel();
		}

		@Bean
		@BridgeTo(poller = @Poller(fixedDelay = "500"))
		public QueueChannel pollableBridgeToInput() {
			return new QueueChannel();
		}

		@Bean
		@MyBridgeTo
		public MessageChannel myBridgeToInput() {
			return new DirectChannel();
		}

		// Error because @Bridge* annotations are only for MessageChannel beans.
		/*@Bean
		@BridgeTo
		public String invalidBridgeAnnotation() {
			return "invalidBridgeAnnotation";
		}*/

		// Error because @Bridge* annotations are mutually exclusive.
		/*@Bean
		@BridgeTo
		@BridgeFrom("foo")
		public MessageChannel invalidBridgeAnnotation2() {
			return new DirectChannel();
		}*/

		// beans for metaAnnotation tests

		@Bean
		public MethodInterceptor annAdvice() {
			return mock(MethodInterceptor.class);
		}

		@Bean
		public QueueChannel annInput() {
			return new QueueChannel();
		}

		@Bean
		public QueueChannel annOutput() {
			return new QueueChannel();
		}

		@Bean
		public MethodInterceptor annAdvice1() {
			return mock(MethodInterceptor.class);
		}

		@Bean
		public QueueChannel annInput1() {
			return new QueueChannel();
		}

		@Bean
		public QueueChannel annInput3() {
			return new QueueChannel();
		}

	}

	@Component
	@GlobalChannelInterceptor
	public static class TestChannelInterceptor extends ChannelInterceptorAdapter {

		private final Log logger = LogFactory.getLog(TestChannelInterceptor.class);

		private final AtomicInteger invoked = new AtomicInteger();

		@Override
		public Message<?> preSend(Message<?> message, MessageChannel channel) {
			this.invoked.incrementAndGet();
			return message;
		}

		public Integer getInvoked() {
			return invoked.get();
		}

	}

	@Component
	@IntegrationConverter
	public static class TestConverter implements Converter<Boolean, Number> {

		private final AtomicInteger invoked = new AtomicInteger();

		@Override
		public Number convert(Boolean source) {
			this.invoked.incrementAndGet();
			return source ? 1 : 0;
		}

		public Integer getInvoked() {
			return invoked.get();
		}
	}

	@Configuration
	@EnableIntegration
	@ImportResource("classpath:org/springframework/integration/configuration/EnableIntegrationTests-context.xml")
	@EnableMessageHistory("${message.history.tracked.components}")
	@EnablePublisher("publishedChannel")
	@EnableAsync
	@EnableReactor
	public static class ContextConfiguration2 {

		/*
		INT-3853
		@Bean
		public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
			return new PropertySourcesPlaceholderConfigurer();
		}*/

		@Bean
		public MessageChannel sendAsyncChannel() {
			return new DirectChannel();
		}

		@Bean
		public CountDownLatch asyncAnnotationProcessLatch() {
			return new CountDownLatch(1);
		}

		@Bean
		public AtomicReference<Thread> asyncAnnotationProcessThread() {
			return new AtomicReference<Thread>();
		}

		@Bean
		@ServiceActivator(inputChannel = "sendAsyncChannel")
		@Role("foo")
		public MessageHandler sendAsyncHandler() {
			return new MessageHandler() {

				@Override
				public void handleMessage(Message<?> message) throws MessagingException {
					asyncAnnotationProcessLatch().countDown();
					asyncAnnotationProcessThread().set(Thread.currentThread());
				}

			};
		}

		@Bean
		@ServiceActivator(inputChannel = "controlBusChannel")
		@Role("bar")
		public ExpressionControlBusFactoryBean controlBus() throws Exception {
			return new ExpressionControlBusFactoryBean();
		}

		@Bean
		public Lifecycle lifecycle() {
			return new Lifecycle() {

				private volatile boolean running;

				@Override
				public void start() {
					this.running = true;
				}

				@Override
				public void stop() {
					this.running = false;
				}

				@Override
				public boolean isRunning() {
					return this.running;
				}

			};
		}

		@Bean
		public PollableChannel publishedChannel() {
			return new QueueChannel();
		}

		@Bean
		public PollableChannel gatewayChannel() {
			return new QueueChannel();
		}

		@Bean
		public PollableChannel gatewayChannel2() {
			return new QueueChannel();
		}

		@Bean
		public PollableChannel counterChannel() {
			return new QueueChannel();
		}

		@Bean
		public PollableChannel fooChannel() {
			return new QueueChannel();
		}

		@Bean
		public PollableChannel messageChannel() {
			return new QueueChannel();
		}

		@Bean
		public QueueChannel numberChannel() {
			QueueChannel channel = new QueueChannel();
			channel.setDatatypes(Number.class);
			return channel;
		}

		@Bean
		public QueueChannel bytesChannel() {
			QueueChannel channel = new QueueChannel();
			channel.setDatatypes(byte[].class);
			return channel;
		}

		@Bean(name = PollerMetadata.DEFAULT_POLLER)
		public PollerMetadata defaultPoller() {
			PollerMetadata pollerMetadata = new PollerMetadata();
			pollerMetadata.setTrigger(new PeriodicTrigger(10));
			return pollerMetadata;
		}

		@Bean
		public PollerMetadata myPoller() {
			PollerMetadata pollerMetadata = new PollerMetadata();
			pollerMetadata.setTrigger(new PeriodicTrigger(11));
			return pollerMetadata;
		}

		@Bean
		@IntegrationConverter
		public SerializingConverter serializingConverter() {
			return new SerializingConverter();
		}

		@Bean
		public DirectChannel promiseChannel() {
			return new DirectChannel();
		}

	}

	@Configuration
	@EnableIntegration
	public static class ChildConfiguration {

		@Bean
		public MessageChannel foo() {
			return new DirectChannel();
		}

		@Bean
		@GlobalChannelInterceptor(patterns = "*")
		public WireTap baz() {
			return new WireTap(new NullChannel());
		}

	}

	public interface AnnotationTestService {

		String handle(String payload);

		String handle1(String payload);

		String handle2(String payload);

		String handle3(String payload);

		String handle4(String payload);

		String transform(Message<String> message);

		String transform2(Message<String> message);

		Integer count();

		String foo();

		Message<?> message();

		Integer annCount();

		Integer annCount1();

		Integer annCount2();

		Integer annCount5();

		Integer annCount8();

		Integer annAgg1(List<?> messages);

		Integer annAgg2(List<?> messages);

		Integer multiply(Integer value);

	}

	@MessageEndpoint("annotationTestService")
	public static class AnnotationTestServiceImpl implements Lifecycle, AnnotationTestService {

		private final AtomicInteger counter = new AtomicInteger();

		private boolean running;

		@Override
		@ServiceActivator(inputChannel = "input", outputChannel = "output", autoStartup = "false",
				poller = @Poller(maxMessagesPerPoll = "${poller.maxMessagesPerPoll}", fixedDelay = "${poller.interval}"))
		@Publisher
		@Payload("#args[0].toLowerCase()")
		@Role("foo")
		public String handle(String payload) {
			return payload.toUpperCase();
		}

		@Override
		@ServiceActivator(inputChannel = "input1", outputChannel = "output",
				poller = @Poller(maxMessagesPerPoll = "${poller.maxMessagesPerPoll}", fixedRate = "${poller.interval}"))
		@Publisher
		@Payload("#args[0].toLowerCase()")
		public String handle1(String payload) {
			return payload.toUpperCase();
		}

		@Override
		@ServiceActivator(inputChannel = "input2", outputChannel = "output",
				poller = @Poller(maxMessagesPerPoll = "${poller.maxMessagesPerPoll}", cron = "0 5 7 * * *"))
		@Publisher
		@Payload("#args[0].toLowerCase()")
		public String handle2(String payload) {
			return payload.toUpperCase();
		}

		@Override
		@ServiceActivator(inputChannel = "input3", outputChannel = "output", poller = @Poller("myPoller"))
		@Publisher
		@Payload("#args[0].toLowerCase()")
		public String handle3(String payload) {
			return payload.toUpperCase();
		}

		@Override
		@ServiceActivator(inputChannel = "input4", outputChannel = "output",
				poller = @Poller(trigger = "myTrigger"))
		@Publisher
		@Payload("#args[0].toLowerCase()")
		public String handle4(String payload) {
			return payload.toUpperCase();
		}

		/*
		 * This is an error because input5 is not defined and is therefore a DirectChannel.
		 */
		/*@ServiceActivator(inputChannel = "input5", outputChannel = "output", poller = @Poller("defaultPollerMetadata"))
		@Publisher
		@Payload("#args[0].toLowerCase()")
		public String handle5(String payload) {
			return payload.toUpperCase();
		}*/

		@Override
		@Transformer(inputChannel = "gatewayChannel")
		public String transform(Message<String> message) {
			assertTrue(message.getHeaders().containsKey("foo"));
			assertEquals("FOO", message.getHeaders().get("foo"));
			assertTrue(message.getHeaders().containsKey("calledMethod"));
			assertEquals("echo", message.getHeaders().get("calledMethod"));
			return this.handle(message.getPayload());
		}

		@Override
		@Transformer(inputChannel = "gatewayChannel2")
		public String transform2(Message<String> message) {
			assertTrue(message.getHeaders().containsKey("foo"));
			assertEquals("FOO", message.getHeaders().get("foo"));
			assertTrue(message.getHeaders().containsKey("calledMethod"));
			assertEquals("echo2", message.getHeaders().get("calledMethod"));
			return this.handle(message.getPayload()) + "2";
		}

		@Override
		@MyInboundChannelAdapter1
		public Integer count() {
			return this.counter.incrementAndGet();
		}

		@Override
		@InboundChannelAdapter(value = "fooChannel",
				poller = @Poller(trigger = "onlyOnceTrigger", maxMessagesPerPoll = "2"))
		public String foo() {
			return "foo";
		}

		@Override
		@InboundChannelAdapter(value = "messageChannel", poller = @Poller(fixedDelay = "${poller.interval}",
				maxMessagesPerPoll = "1"))
		public Message<?> message() {
			return MessageBuilder.withPayload("bar").setHeader("foo", "FOO").build();
		}

		/*
		 * This is an error because 'InboundChannelAdapter' method must not have any arguments.
		 */
		/*@InboundChannelAdapter("errorChannel")
		public String error1(Object arg) {
			return "foo";
		}*/

		/*
		 * This is an error because 'InboundChannelAdapter' return type must not be 'void'.
		 */
		/*@InboundChannelAdapter("errorChannel")
		public void error2() {
		}*/

		// metaAnnotation tests

		@Override
		@MyServiceActivator
		public Integer annCount() {
			return 0;
		}

		@Override
		@MyServiceActivator1(inputChannel = "annInput1", autoStartup = "true",
				adviceChain = {"annAdvice1"}, poller = @Poller(fixedRate = "2000"))
		public Integer annCount1() {
			return 0;
		}

		@Override
		@MyServiceActivatorNoLocalAtts()
		public Integer annCount2() {
			return 0;
		}

		@Override
		@MyServiceActivator5
		public Integer annCount5() {
			return 0;
		}

		@Override
		@MyServiceActivator8
		public Integer annCount8() {
			return 0;
		}

		@Override
		@MyAggregator
		public Integer annAgg1(List<?> messages) {
			return 42;
		}

		@Override
		@MyAggregatorDefaultOverrideDefaults
		public Integer annAgg2(List<?> messages) {
			return 42;
		}

		// Error because @Bridge* annotations are only for @Bean methods.
		/*@BridgeFrom("")
		public void invalidBridgeAnnotationMethod(Object payload) {}*/

		@Override
		@ServiceActivator(inputChannel = "promiseChannel")
		public Integer multiply(Integer value) {
			return value * 2;
		}

		@Override
		public void start() {
			this.running = true;
		}

		@Override
		public void stop() {
			this.running = false;
		}

		@Override
		public boolean isRunning() {
			return this.running;
		}

	}

	@TestMessagingGateway
	public interface TestGateway {

		@Gateway(headers = @GatewayHeader(name = "calledMethod", expression = "#gatewayMethod.name"))
		String echo(String payload);

		@Gateway(requestChannel = "sendAsyncChannel")
		@Async
		void sendAsync(String payload);

		@Gateway(requestChannel = "promiseChannel")
		Promise<Integer> multiply(Integer value);

	}

	@TestMessagingGateway2
	public interface TestGateway2 {

		@Gateway(headers = @GatewayHeader(name = "calledMethod", expression = "#gatewayMethod.name"))
		String echo2(String payload);

	}

	@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
	@Retention(RetentionPolicy.RUNTIME)
	@MessagingGateway(defaultRequestChannel = "gatewayChannel", reactorEnvironment = "reactorEnv",
			defaultRequestTimeout="${default.request.timeout:12300}", defaultReplyTimeout="#{13400}",
			defaultHeaders = @GatewayHeader(name = "foo", value = "FOO"))
	public @interface TestMessagingGateway {

		String defaultRequestChannel() default "";

	}

	@Target(ElementType.TYPE)
	@Retention(RetentionPolicy.RUNTIME)
	@TestMessagingGateway(defaultRequestChannel = "gatewayChannel2")
	public @interface TestMessagingGateway2 {

		String defaultRequestChannel() default "";

	}

	@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
	@Retention(RetentionPolicy.RUNTIME)
	@ServiceActivator(autoStartup = "false",
			phase = "23",
			inputChannel = "annInput",
			outputChannel = "annOutput",
			adviceChain = {"annAdvice"},
			poller = @Poller(fixedDelay = "1000"))
	public @interface MyServiceActivator {

		String inputChannel() default "";

		String outputChannel() default "";

		String[] adviceChain() default {};

		String autoStartup() default "";

		String phase() default "";

		Poller[] poller() default {};
	}

	@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
	@Retention(RetentionPolicy.RUNTIME)
	@MyServiceActivator
	public @interface MyServiceActivator1 {

		String inputChannel() default "";

		String outputChannel() default "";

		String[] adviceChain() default {};

		String autoStartup() default "";

		String phase() default "";

		Poller[] poller() default {};
	}

	@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
	@Retention(RetentionPolicy.RUNTIME)
	@MyServiceActivator1
	public @interface MyServiceActivator2 {

		String inputChannel() default "";

	}

	@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
	@Retention(RetentionPolicy.RUNTIME)
	@MyServiceActivator2
	public @interface MyServiceActivator3 {

		String inputChannel() default "";

	}

	@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
	@Retention(RetentionPolicy.RUNTIME)
	@MyServiceActivator3(inputChannel = "annInput3")
	public @interface MyServiceActivator4 {

		String inputChannel() default "";

	}

	@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
	@Retention(RetentionPolicy.RUNTIME)
	@MyServiceActivator4
	public @interface MyServiceActivator5 {

		String inputChannel() default "";

	}

	// Test prevent infinite recursion

	@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
	@Retention(RetentionPolicy.RUNTIME)
	@MyServiceActivator5
	public @interface MyServiceActivator6 {

		String inputChannel() default "";

	}

	@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
	@Retention(RetentionPolicy.RUNTIME)
	@MyServiceActivator8
	public @interface MyServiceActivator7 {

		String inputChannel() default "";

	}

	@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
	@Retention(RetentionPolicy.RUNTIME)
	@MyServiceActivator7
	public @interface MyServiceActivator8 {

		String inputChannel() default "";

	}
	// end test infinite recursion

	@Target(ElementType.METHOD)
	@Retention(RetentionPolicy.RUNTIME)
	@ServiceActivator(autoStartup = "false",
			phase = "23",
			inputChannel = "annInput",
			outputChannel = "annOutput",
			adviceChain = {"annAdvice"},
			poller = @Poller(fixedDelay = "1000"))
	public @interface MyServiceActivatorNoLocalAtts {
	}

	@Target(ElementType.METHOD)
	@Retention(RetentionPolicy.RUNTIME)
	@Aggregator(autoStartup = "false",
			phase = "23",
			inputChannel = "annInput",
			outputChannel = "annOutput",
			discardChannel = "annOutput",
			poller = @Poller(fixedDelay = "1000"))
	public @interface MyAggregator {

		String inputChannel() default "";

		String outputChannel() default "";

		String discardChannel() default "";

		long sendTimeout() default 1000L;

		boolean sendPartialResultsOnExpiry() default false;

		String autoStartup() default "";

		String phase() default "";

		Poller[] poller() default {};
	}

	@Target(ElementType.METHOD)
	@Retention(RetentionPolicy.RUNTIME)
	@Aggregator(autoStartup = "false",
			phase = "23",
			inputChannel = "annInput",
			outputChannel = "annOutput",
			discardChannel = "annOutput",
			sendPartialResultsOnExpiry = "false",
			sendTimeout = "1000",
			poller = @Poller(fixedDelay = "1000"))
	public @interface MyAggregatorDefaultOverrideDefaults {

		String sendPartialResultsOnExpiry() default "true";

		String sendTimeout() default "75";

	}

	@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
	@Retention(RetentionPolicy.RUNTIME)
	@InboundChannelAdapter(value = "counterChannel", autoStartup = "false", phase = "23")
	public @interface MyInboundChannelAdapter {

		String value() default "";

		String autoStartup() default "";

		String phase() default "";

		Poller[] poller() default {};

	}

	@Target(ElementType.METHOD)
	@Retention(RetentionPolicy.RUNTIME)
	@MyInboundChannelAdapter
	public @interface MyInboundChannelAdapter1 {

	}

	@Target(ElementType.METHOD)
	@Retention(RetentionPolicy.RUNTIME)
	@BridgeFrom(value = "metaBridgeInput", autoStartup = "false")
	public @interface MyBridgeFrom {

		String value() default "";
	}

	@Target(ElementType.METHOD)
	@Retention(RetentionPolicy.RUNTIME)
	@BridgeTo(autoStartup = "false")
	public @interface MyBridgeTo {
	}

	// Error because the annotation is on a class; it must be on an interface
//	@MessagingGateway(defaultRequestChannel = "gatewayChannel", defaultHeaders = @GatewayHeader(name = "foo", value = "FOO"))
//	public static class TestGateway2 { }

}
