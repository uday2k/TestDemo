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

package org.springframework.integration.stomp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.SmartLifecycle;
import org.springframework.integration.stomp.event.StompConnectionFailedEvent;
import org.springframework.integration.stomp.event.StompSessionConnectedEvent;
import org.springframework.messaging.simp.stomp.StompClientSupport;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandler;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.util.Assert;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;

/**
 * Base {@link StompSessionManager} implementation to manage a single {@link StompSession}
 * over its {@link ListenableFuture} from the target implementation of this class.
 * <p>
 * The connection to the {@link StompSession} is made during {@link #start()}.
 * <p>
 * The {@link #stop()} lifecycle method manages {@link StompSession#disconnect()}.
 * <p>
 * The {@link #connect(StompSessionHandler)} and {@link #disconnect(StompSessionHandler)} method
 * implementations populate/remove the provided {@link StompSessionHandler} to/from an internal
 * {@link AbstractStompSessionManager.CompositeStompSessionHandler}, which delegates all operations
 * to the provided {@link StompSessionHandler}s.
 * This {@link AbstractStompSessionManager.CompositeStompSessionHandler} is used for the
 * {@link StompSession} connection.
 * @author Artem Bilan
 * @since 4.2
 */
public abstract class AbstractStompSessionManager implements StompSessionManager, ApplicationEventPublisherAware,
		SmartLifecycle, DisposableBean, BeanNameAware {

	private static final long DEFAULT_RECOVERY_INTERVAL = 10000;

	protected final Log logger = LogFactory.getLog(getClass());

	private final CompositeStompSessionHandler compositeStompSessionHandler = new CompositeStompSessionHandler();

	private final Object lifecycleMonitor = new Object();

	protected final StompClientSupport stompClient;

	private boolean autoStartup = false;

	private boolean running = false;

	private int phase = Integer.MAX_VALUE / 2;

	private ApplicationEventPublisher applicationEventPublisher;

	private volatile StompHeaders connectHeaders;

	private volatile ListenableFuture<StompSession> stompSessionListenableFuture;

	private volatile boolean autoReceipt;

	private volatile boolean connecting;

	private volatile boolean connected;

	private volatile long recoveryInterval = DEFAULT_RECOVERY_INTERVAL;

	private volatile ScheduledFuture<?> reconnectFuture;

	private String name;

	public AbstractStompSessionManager(StompClientSupport stompClient) {
		Assert.notNull(stompClient, "'stompClient' is required.");
		this.stompClient = stompClient;
	}

	public void setConnectHeaders(StompHeaders connectHeaders) {
		this.connectHeaders = connectHeaders;
	}

	public void setAutoReceipt(boolean autoReceipt) {
		this.autoReceipt = autoReceipt;
	}

	@Override
	public boolean isAutoReceiptEnabled() {
		return this.autoReceipt;
	}

	@Override
	public boolean isConnected() {
		return this.connected;
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher = applicationEventPublisher;
	}

	@Override
	public void setBeanName(String name) {
		this.name = name;
	}

	/**
	 * @param recoveryInterval the reconnect interval in milliseconds in case of lost connection.
	 * @since 4.2.2
	 */
	public void setRecoveryInterval(int recoveryInterval) {
		this.recoveryInterval = recoveryInterval;
	}

	public void setAutoStartup(boolean autoStartup) {
		this.autoStartup = autoStartup;
	}

	public void setPhase(int phase) {
		this.phase = phase;
	}

	public long getRecoveryInterval() {
		return recoveryInterval;
	}

	@Override
	public boolean isAutoStartup() {
		return this.autoStartup;
	}

	@Override
	public boolean isRunning() {
		return this.running;
	}

	@Override
	public int getPhase() {
		return this.phase;
	}

	private void connect() {
		this.connecting = true;
		this.stompSessionListenableFuture = doConnect(this.compositeStompSessionHandler);
		this.stompSessionListenableFuture.addCallback(new ListenableFutureCallback<StompSession>() {

			@Override
			public void onFailure(Throwable e) {
				scheduleReconnect(e);
			}

			@Override
			public void onSuccess(StompSession stompSession) {
				AbstractStompSessionManager.this.connected = true;
				AbstractStompSessionManager.this.connecting = false;
				stompSession.setAutoReceipt(isAutoReceiptEnabled());
				if (AbstractStompSessionManager.this.applicationEventPublisher != null) {
					AbstractStompSessionManager.this.applicationEventPublisher.publishEvent(
							new StompSessionConnectedEvent(this));
				}
				AbstractStompSessionManager.this.reconnectFuture = null;
			}

		});
	}

	private void scheduleReconnect(Throwable e) {
		this.connecting = this.connected = false;
		logger.error("STOMP connect error.", e);
		if (this.applicationEventPublisher != null) {
			this.applicationEventPublisher.publishEvent(
					new StompConnectionFailedEvent(this, e));
		}
		// cancel() after the publish in case we are on that thread; a send to a QueueChannel would fail.
		if (this.reconnectFuture != null) {
			this.reconnectFuture.cancel(true);
			this.reconnectFuture = null;
		}

		this.reconnectFuture = this.stompClient.getTaskScheduler()
				.schedule(new Runnable() {

					@Override
					public void run() {
						connect();
					}

				}, new Date(System.currentTimeMillis() + this.recoveryInterval));
	}

	@Override
	public void destroy() {
		if (this.stompSessionListenableFuture != null) {
			if (this.reconnectFuture != null) {
				this.reconnectFuture.cancel(false);
				this.reconnectFuture = null;
			}
			this.stompSessionListenableFuture.addCallback(new ListenableFutureCallback<StompSession>() {

				@Override
				public void onFailure(Throwable ex) {
					AbstractStompSessionManager.this.connected = false;
				}

				@Override
				public void onSuccess(StompSession session) {
					session.disconnect();
					AbstractStompSessionManager.this.connected = false;
				}

			});
			this.stompSessionListenableFuture = null;
		}
	}

	@Override
	public void start() {
		synchronized (this.lifecycleMonitor) {
			if (!isRunning()) {
				if (logger.isInfoEnabled()) {
					logger.info("Starting " + getClass().getSimpleName());
				}
				connect();
				this.running = true;
			}
		}
	}

	@Override
	public void stop(Runnable callback) {
		synchronized (this.lifecycleMonitor) {
			stop();
			if (callback != null) {
				callback.run();
			}
		}
	}

	@Override
	public void stop() {
		synchronized (this.lifecycleMonitor) {
			if (isRunning()) {
				this.running = false;
				if (logger.isInfoEnabled()) {
					logger.info("Stopping " + getClass().getSimpleName());
				}
				destroy();
			}
		}
	}

	@Override
	public void connect(StompSessionHandler handler) {
		this.compositeStompSessionHandler.addHandler(handler);
		if (!isConnected() && !this.connecting) {
			if (this.reconnectFuture != null) {
				this.reconnectFuture.cancel(true);
				this.reconnectFuture = null;
			}
			connect();
		}
	}

	@Override
	public void disconnect(StompSessionHandler handler) {
		this.compositeStompSessionHandler.removeHandler(handler);
	}

	protected StompHeaders getConnectHeaders() {
		return connectHeaders;
	}

	@Override
	public String toString() {
		return "StompSessionManager{" +
				"connected=" + connected +
				", name='" + name + '\'' +
				'}';
	}

	protected abstract ListenableFuture<StompSession> doConnect(StompSessionHandler handler);


	private class CompositeStompSessionHandler extends StompSessionHandlerAdapter {

		private final List<StompSessionHandler> delegates =
				Collections.synchronizedList(new ArrayList<StompSessionHandler>());

		private volatile StompSession session;

		void addHandler(StompSessionHandler delegate) {
			if (this.session != null) {
				delegate.afterConnected(this.session, getConnectHeaders());
			}
			synchronized (this.delegates) {
				this.delegates.add(delegate);
			}
		}

		void removeHandler(StompSessionHandler delegate) {
			synchronized (this.delegates) {
				this.delegates.remove(delegate);
			}
		}

		@Override
		public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
			this.session = session;
			synchronized (this.delegates) {
				for (StompSessionHandler delegate : this.delegates) {
					delegate.afterConnected(session, connectedHeaders);
				}
			}
		}

		@Override
		public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload,
		                            Throwable exception) {
			synchronized (this.delegates) {
				for (StompSessionHandler delegate : this.delegates) {
					delegate.handleException(session, command, headers, payload, exception);
				}
			}
		}

		@Override
		public void handleTransportError(StompSession session, Throwable exception) {
			logger.error("STOMP transport error for session: [" + session + "]", exception);
			this.session = null;
			scheduleReconnect(exception);
			synchronized (this.delegates) {
				for (StompSessionHandler delegate : this.delegates) {
					delegate.handleTransportError(session, exception);
				}
			}
		}

		@Override
		public void handleFrame(StompHeaders headers, Object payload) {
			synchronized (this.delegates) {
				for (StompSessionHandler delegate : this.delegates) {
					delegate.handleFrame(headers, payload);
				}
			}
		}

	}

}
