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

package org.springframework.integration.xml.config;

import static org.junit.Assert.*;

import java.util.Properties;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.integration.test.util.TestUtils;
import org.springframework.messaging.MessageHandler;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * @author Artem Bilan
 */
@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
public class XPathSplitterParserTests {

	@Autowired @Qualifier("xpathSplitter.handler")
	private MessageHandler xpathSplitter;

	@Autowired @Qualifier("outputProperties")
	private Properties outputProperties;

	@Test
	public void testXpathSplitterConfig() {
		assertTrue(TestUtils.getPropertyValue(this.xpathSplitter, "createDocuments", Boolean.class));
		assertFalse(TestUtils.getPropertyValue(this.xpathSplitter, "applySequence", Boolean.class));
		assertFalse(TestUtils.getPropertyValue(this.xpathSplitter, "iterator", Boolean.class));
		assertSame(this.outputProperties, TestUtils.getPropertyValue(this.xpathSplitter, "outputProperties"));
		assertEquals("/orders/order",
				TestUtils.getPropertyValue(this.xpathSplitter,
						"xpathExpression.xpathExpression.xpath.m_patternString",
						String.class));
	}

}
