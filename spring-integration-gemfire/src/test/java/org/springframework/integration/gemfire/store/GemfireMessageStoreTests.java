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

package org.springframework.integration.gemfire.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.springframework.data.gemfire.CacheFactoryBean;
import org.springframework.data.gemfire.RegionFactoryBean;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.history.MessageHistory;
import org.springframework.integration.store.MessageGroup;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.integration.test.util.TestUtils;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.util.Assert;

import com.gemstone.gemfire.cache.Cache;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.Scope;

/**
 * @author Mark Fisher
 * @author David Turanski
 * @author Gary Russell
 * @since 2.1
 */
public class GemfireMessageStoreTests {

	private Cache cache;

	private Region<Object, Object> region;

	@Test
	public void addAndGetMessage() throws Exception {
		GemfireMessageStore store = new GemfireMessageStore(this.region);
		store.afterPropertiesSet();

		Message<?> message = MessageBuilder.withPayload("test").build();
		store.addMessage(message);
		Message<?> retrieved = store.getMessage(message.getHeaders().getId());
		assertEquals(message, retrieved);
	}

	@Test
	public void testRegionConstructor() throws Exception {
		RegionFactoryBean<Object, Object> region = new RegionFactoryBean<Object, Object>() {};
		region.setName("someRegion");
		region.setCache(this.cache);
		region.afterPropertiesSet();

		GemfireMessageStore store = new GemfireMessageStore(region.getObject());
		store.afterPropertiesSet();
		assertSame(region.getObject(), TestUtils.getPropertyValue(store, "messageStoreRegion"));
	}

	@Test
	public void testWithMessageHistory() throws Exception {
		GemfireMessageStore store = new GemfireMessageStore(this.region);
		store.afterPropertiesSet();

		Message<?> message = new GenericMessage<String>("Hello");
		DirectChannel fooChannel = new DirectChannel();
		fooChannel.setBeanName("fooChannel");
		DirectChannel barChannel = new DirectChannel();
		barChannel.setBeanName("barChannel");

		message = MessageHistory.write(message, fooChannel);
		message = MessageHistory.write(message, barChannel);
		store.addMessage(message);
		message = store.getMessage(message.getHeaders().getId());
		MessageHistory messageHistory = MessageHistory.read(message);
		assertNotNull(messageHistory);
		assertEquals(2, messageHistory.size());
		Properties fooChannelHistory = messageHistory.get(0);
		assertEquals("fooChannel", fooChannelHistory.get("name"));
		assertEquals("channel", fooChannelHistory.get("type"));
	}

	@Test
	public void testAddAndRemoveMessagesFromMessageGroup() throws Exception {
		GemfireMessageStore messageStore = new GemfireMessageStore(this.region);
		messageStore.afterPropertiesSet();

		String groupId = "X";
		List<Message<?>> messages = new ArrayList<Message<?>>();
		for (int i = 0; i < 25; i++) {
			Message<String> message = MessageBuilder.withPayload("foo").setCorrelationId(groupId).build();
			messageStore.addMessageToGroup(groupId, message);
			messages.add(message);
		}
		MessageGroup group = messageStore.getMessageGroup(groupId);
		assertEquals(25, group.size());
		messageStore.removeMessagesFromGroup(groupId, messages);
		group = messageStore.getMessageGroup(groupId);
		assertEquals(0, group.size());
	}

	@Before
	public void init() throws Exception {
		CacheFactoryBean cacheFactoryBean = new CacheFactoryBean();
		this.cache = cacheFactoryBean.getObject();
		this.region = cache.createRegionFactory().setScope(Scope.LOCAL).create("sig-tests");
	}

	@After
	public void cleanup() {
		this.cache.close();
		Assert.isTrue(this.cache.isClosed(), "Cache did not close after close() call");
	}

}
