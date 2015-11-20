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
package org.springframework.integration.mqtt.support;

import org.eclipse.paho.client.mqttv3.MqttMessage;

import org.springframework.messaging.Message;
import org.springframework.messaging.converter.MessageConverter;

/**
 * Extension of {@link MessageConverter} allowing the topic to be added as
 * a header.
 *
 * @author Gary Russell
 * @since 4.0
 *
 */
public interface MqttMessageConverter extends MessageConverter {

	/**
	 * Convert to a Message.
	 *
	 * @param topic The topic.
	 * @param mqttMessage The MQTT message.
	 * @return The Message.
	 */
	Message<?> toMessage(String topic, MqttMessage mqttMessage);
}
