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

package org.springframework.integration.redis.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.redis.inbound.RedisQueueInboundGateway;
import org.springframework.integration.redis.outbound.RedisQueueOutboundGateway;
import org.springframework.integration.redis.rules.RedisAvailable;
import org.springframework.integration.redis.rules.RedisAvailableTests;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * @author David Liu
 * @since 4.1
 */
@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
@DirtiesContext
public class RedisQueueGatewayIntegrationTests extends RedisAvailableTests {

	@Autowired
	@Qualifier("sendChannel")
	private DirectChannel sendChannel;

	@Autowired
	@Qualifier("outputChannel")
	private QueueChannel outputChannel;

	@Autowired
	private RedisQueueInboundGateway inboundGateway;

	@Autowired
	private RedisQueueOutboundGateway outboundGateway;

	@Test
	@RedisAvailable
	public void testRequestWithReply() throws Exception {
		this.sendChannel.send(new GenericMessage<String>("test1"));
		Message<?> receive = this.outputChannel.receive(10000);
		assertNotNull(receive);
		assertEquals("test1".toUpperCase(), receive.getPayload());
	}

	@Test
	@RedisAvailable
	public void testInboundGatewayStop() throws Exception {
		this.inboundGateway.stop();
		try {
			this.sendChannel.send(new GenericMessage<String>("test1"));
		}
		catch(Exception e) {
			assertTrue(e.getMessage().contains("No reply produced"));
		}
		finally {
			this.inboundGateway.start();
		}
	}

	@Test
	@RedisAvailable
	public void testNullSerializer() throws Exception {
		this.inboundGateway.setSerializer(null);
		try {
			this.sendChannel.send(new GenericMessage<String>("test1"));
		}
		catch(Exception e) {
			assertTrue(e.getMessage().contains("No reply produced"));
		}
		finally {
			this.inboundGateway.setSerializer(new StringRedisSerializer());
		}
	}

	@Test
	@RedisAvailable
	public void testRequestReplyWithMessage() throws Exception {
		this.inboundGateway.setSerializer(new JdkSerializationRedisSerializer());
		this.inboundGateway.setExtractPayload(false);
		this.outboundGateway.setSerializer(new JdkSerializationRedisSerializer());
		this.outboundGateway.setExtractPayload(false);
		this.sendChannel.send(new GenericMessage<String>("test1"));
		Message<?> receive = this.outputChannel.receive(10000);
		assertNotNull(receive);
		assertEquals("test1".toUpperCase(), receive.getPayload());
		this.inboundGateway.setSerializer(new StringRedisSerializer());
		this.inboundGateway.setExtractPayload(true);
		this.outboundGateway.setSerializer(new StringRedisSerializer());
		this.outboundGateway.setExtractPayload(true);
	}

}
