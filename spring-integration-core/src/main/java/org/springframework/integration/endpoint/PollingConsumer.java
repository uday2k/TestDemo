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

package org.springframework.integration.endpoint;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

import org.springframework.context.Lifecycle;
import org.springframework.integration.channel.ExecutorChannelInterceptorAware;
import org.springframework.integration.transaction.IntegrationResourceHolder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.PollableChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * Message Endpoint that connects any {@link MessageHandler} implementation
 * to a {@link PollableChannel}.
 *
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 * @author Gary Russell
 * @author Artem Bilan
 */
public class PollingConsumer extends AbstractPollingEndpoint {

	private final PollableChannel inputChannel;

	private final MessageHandler handler;

	private final List<ChannelInterceptor> channelInterceptors;

	private volatile long receiveTimeout = 1000;

	public PollingConsumer(PollableChannel inputChannel, MessageHandler handler) {
		Assert.notNull(inputChannel, "inputChannel must not be null");
		Assert.notNull(handler, "handler must not be null");
		this.inputChannel = inputChannel;
		this.handler = handler;
		if (this.inputChannel instanceof ExecutorChannelInterceptorAware) {
			this.channelInterceptors = ((ExecutorChannelInterceptorAware) this.inputChannel).getChannelInterceptors();
		}
		else {
			channelInterceptors = null;
		}
	}


	public void setReceiveTimeout(long receiveTimeout) {
		this.receiveTimeout = receiveTimeout;
	}

	@Override
	protected void doStart() {
		if (this.handler instanceof Lifecycle) {
			((Lifecycle) this.handler).start();
		}
		super.doStart();
	}

	@Override
	protected void doStop() {
		if (this.handler instanceof Lifecycle) {
			((Lifecycle) this.handler).stop();
		}
		super.doStop();
	}

	@Override
	protected void handleMessage(Message<?> message) {
		Message<?> theMessage = message;
		Deque<ExecutorChannelInterceptor> interceptorStack = null;
		try {
			if (this.channelInterceptors != null
					&& ((ExecutorChannelInterceptorAware) this.inputChannel).hasExecutorInterceptors()) {
				interceptorStack = new ArrayDeque<ExecutorChannelInterceptor>();
				theMessage = applyBeforeHandle(theMessage, interceptorStack);
				if (theMessage == null) {
					return;
				}
			}
			this.handler.handleMessage(theMessage);
			if (!CollectionUtils.isEmpty(interceptorStack)) {
				triggerAfterMessageHandled(theMessage, null, interceptorStack);
			}
		}
		catch (Exception ex) {
			if (!CollectionUtils.isEmpty(interceptorStack)) {
				triggerAfterMessageHandled(theMessage, ex, interceptorStack);
			}
			if (ex instanceof MessagingException) {
				throw (MessagingException) ex;
			}
			String description = "Failed to handle " + theMessage + " to " + this + " in " + this.handler;
			throw new MessageDeliveryException(theMessage, description, ex);
		}
		catch (Error ex) {//NOSONAR - ok, we re-throw below
			if (!CollectionUtils.isEmpty(interceptorStack)) {
				String description = "Failed to handle " + theMessage + " to " + this + " in " + this.handler;
				triggerAfterMessageHandled(theMessage,
						new MessageDeliveryException(theMessage, description, ex),
						interceptorStack);
			}
			throw ex;
		}
	}

	private Message<?> applyBeforeHandle(Message<?> message, Deque<ExecutorChannelInterceptor> interceptorStack) {
		Message<?> theMessage = message;
		for (ChannelInterceptor interceptor : this.channelInterceptors) {
			if (interceptor instanceof ExecutorChannelInterceptor) {
				ExecutorChannelInterceptor executorInterceptor = (ExecutorChannelInterceptor) interceptor;
				theMessage = executorInterceptor.beforeHandle(theMessage, this.inputChannel, this.handler);
				if (message == null) {
					if (logger.isDebugEnabled()) {
						logger.debug(executorInterceptor.getClass().getSimpleName()
								+ " returned null from beforeHandle, i.e. precluding the send.");
					}
					triggerAfterMessageHandled(null, null, interceptorStack);
					return null;
				}
				interceptorStack.add(executorInterceptor);
			}
		}
		return theMessage;
	}

	private void triggerAfterMessageHandled(Message<?> message, Exception ex,
	                                        Deque<ExecutorChannelInterceptor> interceptorStack) {
		Iterator<ExecutorChannelInterceptor> iterator = interceptorStack.descendingIterator();
		while (iterator.hasNext()) {
			ExecutorChannelInterceptor interceptor = iterator.next();
			try {
				interceptor.afterMessageHandled(message, this.inputChannel, this.handler, ex);
			}
			catch (Throwable ex2) {//NOSONAR
				logger.error("Exception from afterMessageHandled in " + interceptor, ex2);
			}
		}
	}

	@Override
	protected Message<?> receiveMessage() {
		return (this.receiveTimeout >= 0)
				? this.inputChannel.receive(this.receiveTimeout)
				: this.inputChannel.receive();
	}

	@Override
	protected Object getResourceToBind() {
		return this.inputChannel;
	}

	@Override
	protected String getResourceKey() {
		return IntegrationResourceHolder.INPUT_CHANNEL;
	}

}
