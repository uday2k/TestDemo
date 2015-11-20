/*
 * Copyright 2015 the original author or authors.
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
package org.springframework.integration.zookeeper.config;

import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.curator.framework.CuratorFramework;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.leader.event.AbstractLeaderEvent;
import org.springframework.integration.leader.event.OnGrantedEvent;
import org.springframework.integration.leader.event.OnRevokedEvent;
import org.springframework.integration.zookeeper.ZookeeperTestSupport;
import org.springframework.integration.zookeeper.leader.LeaderInitiator;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * @author Gary Russell
 * @since 4.2
 *
 */
@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
@DirtiesContext
public class LeaderInitiatorFactoryBeanTests extends ZookeeperTestSupport {

	private static CuratorFramework client;

	@Autowired
	private LeaderInitiator leaderInitiator;

	@Autowired
	private Config config;

	@BeforeClass
	public static void getClient() throws Exception {
		client = createNewClient();
	}

	@Test
	public void test() throws Exception {
		assertTrue(this.config.latch1.await(10, TimeUnit.SECONDS));
		assertThat(this.config.events.get(0), instanceOf(OnGrantedEvent.class));
		this.leaderInitiator.stop();
		assertTrue(this.config.latch2.await(10, TimeUnit.SECONDS));
		assertThat(this.config.events.get(1), instanceOf(OnRevokedEvent.class));
	}

	@Configuration
	public static class Config {

		private final List<AbstractLeaderEvent> events = new ArrayList<AbstractLeaderEvent>();

		private final CountDownLatch latch1 = new CountDownLatch(1);

		private final CountDownLatch latch2 = new CountDownLatch(2);

		@Bean
		public LeaderInitiatorFactoryBean leaderInitiator(CuratorFramework client) {
			return new LeaderInitiatorFactoryBean(client, "/siTest/", "foo");
		}

		@Bean
		public CuratorFramework client() {
			return LeaderInitiatorFactoryBeanTests.client;
		}

		@Bean
		public ApplicationListener<?> listener() {
			return new ApplicationListener<AbstractLeaderEvent>() {

				@Override
				public void onApplicationEvent(AbstractLeaderEvent event) {
					events.add(event);
					latch1.countDown();
					latch2.countDown();
				}

			};
		}

	}

}
