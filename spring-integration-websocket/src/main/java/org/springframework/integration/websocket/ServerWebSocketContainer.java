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

package org.springframework.integration.websocket;

import java.util.Arrays;

import org.springframework.scheduling.TaskScheduler;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.SockJsServiceRegistration;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;
import org.springframework.web.socket.server.HandshakeHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.sockjs.frame.SockJsMessageCodec;
import org.springframework.web.socket.sockjs.transport.TransportHandler;

/**
 * The {@link IntegrationWebSocketContainer} implementation for the {@code server}
 * {@link org.springframework.web.socket.WebSocketHandler} registration.
 * <p>
 * Registers an internal {@code IntegrationWebSocketContainer.IntegrationWebSocketHandler}
 * for provided {@link #paths} with the {@link WebSocketHandlerRegistry}.
 * <p>
 * The real registration is based on Spring Web-Socket infrastructure via {@link WebSocketConfigurer}
 * implementation of this class.
 *
 * @author Artem Bilan
 * @author Gary Russell
 * @since 4.1
 */
public class ServerWebSocketContainer extends IntegrationWebSocketContainer implements WebSocketConfigurer {

	private final String[] paths;

	private HandshakeHandler handshakeHandler;

	private HandshakeInterceptor[] interceptors;

	private WebSocketHandlerDecoratorFactory[] decoratorFactories;

	private SockJsServiceOptions sockJsServiceOptions;

	public ServerWebSocketContainer(String... paths) {
		this.paths = paths;
	}

	public ServerWebSocketContainer setHandshakeHandler(HandshakeHandler handshakeHandler) {
		this.handshakeHandler = handshakeHandler;
		return this;
	}

	public ServerWebSocketContainer setInterceptors(HandshakeInterceptor... interceptors) {
		Assert.notNull(interceptors, "'interceptors' must not be null");
		Assert.noNullElements(interceptors, "'interceptors' must not contain null elements");
		this.interceptors = Arrays.copyOf(interceptors, interceptors.length);
		return this;
	}


	/**
	 * Configure one or more factories to decorate the handler used to process
	 * WebSocket messages. This may be useful in some advanced use cases, for
	 * example to allow Spring Security to forcibly close the WebSocket session
	 * when the corresponding HTTP session expires.
	 * @param factories the WebSocketHandlerDecoratorFactory array to use
	 * @return the current ServerWebSocketContainer
	 * @since 4.2
	 */
	public ServerWebSocketContainer setDecoratorFactories(WebSocketHandlerDecoratorFactory... factories) {
		Assert.notNull(factories, "'factories' must not be null");
		Assert.noNullElements(factories, "'factories' must not contain null elements");
		this.decoratorFactories = Arrays.copyOf(factories, factories.length);
		return this;
	}

	public ServerWebSocketContainer withSockJs(SockJsServiceOptions... sockJsServiceOptions) {
		if (ObjectUtils.isEmpty(sockJsServiceOptions)) {
			this.sockJsServiceOptions = new SockJsServiceOptions();
		}
		else {
			Assert.state(sockJsServiceOptions.length == 1, "Only one 'sockJsServiceOptions' is applicable.");
			this.sockJsServiceOptions = sockJsServiceOptions[0];
		}
		return this;
	}

	public void setSockJsServiceOptions(SockJsServiceOptions sockJsServiceOptions) {
		this.sockJsServiceOptions = sockJsServiceOptions;
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		WebSocketHandler webSocketHandler = this.webSocketHandler;

		if (this.decoratorFactories != null) {
			for (WebSocketHandlerDecoratorFactory factory : this.decoratorFactories) {
				webSocketHandler = factory.decorate(webSocketHandler);
			}
		}

		WebSocketHandlerRegistration registration = registry.addHandler(webSocketHandler, this.paths)
				.setHandshakeHandler(this.handshakeHandler)
				.addInterceptors(this.interceptors);

		if (this.sockJsServiceOptions != null) {
			SockJsServiceRegistration sockJsServiceRegistration = registration.withSockJS();
			if (this.sockJsServiceOptions.webSocketEnabled != null) {
				sockJsServiceRegistration.setWebSocketEnabled(this.sockJsServiceOptions.webSocketEnabled);
			}
			if (this.sockJsServiceOptions.clientLibraryUrl != null) {
				sockJsServiceRegistration.setClientLibraryUrl(this.sockJsServiceOptions.clientLibraryUrl);
			}
			if (this.sockJsServiceOptions.disconnectDelay != null) {
				sockJsServiceRegistration.setDisconnectDelay(this.sockJsServiceOptions.disconnectDelay);
			}
			if (this.sockJsServiceOptions.heartbeatTime != null) {
				sockJsServiceRegistration.setHeartbeatTime(this.sockJsServiceOptions.heartbeatTime);
			}
			if (this.sockJsServiceOptions.httpMessageCacheSize != null) {
				sockJsServiceRegistration.setHttpMessageCacheSize(this.sockJsServiceOptions.httpMessageCacheSize);
			}
			if (this.sockJsServiceOptions.heartbeatTime != null) {
				sockJsServiceRegistration.setHeartbeatTime(this.sockJsServiceOptions.heartbeatTime);
			}
			if (this.sockJsServiceOptions.sessionCookieNeeded != null) {
				sockJsServiceRegistration.setSessionCookieNeeded(this.sockJsServiceOptions.sessionCookieNeeded);
			}
			if (this.sockJsServiceOptions.streamBytesLimit != null) {
				sockJsServiceRegistration.setStreamBytesLimit(this.sockJsServiceOptions.streamBytesLimit);
			}
			if (this.sockJsServiceOptions.transportHandlers != null) {
				sockJsServiceRegistration.setTransportHandlers(this.sockJsServiceOptions.transportHandlers);
			}
			if (this.sockJsServiceOptions.taskScheduler != null) {
				sockJsServiceRegistration.setTaskScheduler(this.sockJsServiceOptions.taskScheduler);
			}
			if (this.sockJsServiceOptions.messageCodec != null) {
				sockJsServiceRegistration.setMessageCodec(this.sockJsServiceOptions.messageCodec);
			}
		}

	}

	/**
	 * @see org.springframework.web.socket.config.annotation.SockJsServiceRegistration
	 */
	public static class SockJsServiceOptions {

		private TaskScheduler taskScheduler;

		private String clientLibraryUrl;

		private Integer streamBytesLimit;

		private Boolean sessionCookieNeeded;

		private Long heartbeatTime;

		private Long disconnectDelay;

		private Integer httpMessageCacheSize;

		private Boolean webSocketEnabled;

		private TransportHandler[] transportHandlers;

		private SockJsMessageCodec messageCodec;

		public SockJsServiceOptions setTaskScheduler(TaskScheduler taskScheduler) {
			this.taskScheduler = taskScheduler;
			return this;
		}

		public SockJsServiceOptions setClientLibraryUrl(String clientLibraryUrl) {
			this.clientLibraryUrl = clientLibraryUrl;
			return this;
		}

		public SockJsServiceOptions setStreamBytesLimit(int streamBytesLimit) {
			this.streamBytesLimit = streamBytesLimit;
			return this;
		}

		public SockJsServiceOptions setSessionCookieNeeded(boolean sessionCookieNeeded) {
			this.sessionCookieNeeded = sessionCookieNeeded;
			return this;
		}

		public SockJsServiceOptions setHeartbeatTime(long heartbeatTime) {
			this.heartbeatTime = heartbeatTime;
			return this;
		}

		public SockJsServiceOptions setDisconnectDelay(long disconnectDelay) {
			this.disconnectDelay = disconnectDelay;
			return this;
		}

		public SockJsServiceOptions setHttpMessageCacheSize(int httpMessageCacheSize) {
			this.httpMessageCacheSize = httpMessageCacheSize;
			return this;
		}

		public SockJsServiceOptions setWebSocketEnabled(boolean webSocketEnabled) {
			this.webSocketEnabled = webSocketEnabled;
			return this;
		}

		public SockJsServiceOptions setTransportHandlers(TransportHandler... transportHandlers) {
			this.transportHandlers = transportHandlers;
			return this;
		}

		public SockJsServiceOptions setMessageCodec(SockJsMessageCodec messageCodec) {
			this.messageCodec = messageCodec;
			return this;
		}

	}

}
