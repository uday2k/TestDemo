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

package org.springframework.integration.support;

import java.io.Serializable;
import java.util.Map;

import org.springframework.integration.store.SimpleMessageStore;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

/**
 * An implementation of {@link Message} with a generic payload. Unlike
 * {@link GenericMessage}, this message (or its headers) can be modified
 * after creation. Great care must be taken, when mutating messages, that
 * some other element/thread is not concurrently using the message. Also note
 * that any in-memory stores (such as {@link SimpleMessageStore}) may have
 * a reference to the message and changes will be reflected there too.
 *
 * @author Gary Russell
 * @author Artem Bilan
 * @author Stuart Williams
 * @author David Turanski
 * @since 4.0
 *
 */
public class MutableMessage<T> implements Message<T>, Serializable {

	private static final long serialVersionUID = -636635024258737500L;

	private final T payload;

	private final MutableMessageHeaders headers;

	public MutableMessage(T payload) {
		this(payload, null);
	}

	public MutableMessage(T payload, Map<String, Object> headers) {
		Assert.notNull(payload, "payload must not be null");
		this.payload = payload;

		this.headers = new MutableMessageHeaders(headers);

		if (headers != null) {
			this.headers.put(MessageHeaders.ID, headers.get(MessageHeaders.ID));
			this.headers.put(MessageHeaders.TIMESTAMP, headers.get(MessageHeaders.TIMESTAMP));
		}
	}


	@Override
	public MutableMessageHeaders getHeaders() {
		return this.headers;
	}

	@Override
	public T getPayload() {
		return this.payload;
	}

	Map<String, Object> getRawHeaders() {
		return this.headers.getRawHeaders();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		if (this.payload instanceof byte[]) {
			sb.append("[Payload byte[").append(((byte[]) this.payload).length).append("]]");
		}
		else {
			sb.append("[Payload ").append(this.payload.getClass().getSimpleName());
			sb.append(" content=").append(this.payload).append("]");
		}
		sb.append("[Headers=").append(this.headers).append("]");
		return sb.toString();
	}

	@Override
	public int hashCode() {
		return this.headers.hashCode() * 23 + ObjectUtils.nullSafeHashCode(this.payload);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj != null && obj instanceof MutableMessage<?>) {
			MutableMessage<?> other = (MutableMessage<?>) obj;
			return (this.headers.getId().equals(other.headers.getId()) &&
					this.headers.equals(other.headers) && this.payload.equals(other.payload));
		}
		return false;
	}

}
