package com.github.hotware.hsearch.jpa.event;

import java.util.Collections;
import java.util.Set;

import com.github.hotware.hsearch.event.EventConsumer;
import com.github.hotware.hsearch.event.EventSource;

/**
 * this JPAEventProvider calls the eventConsumer as soon as it gets the event.
 * 
 * <br>
 * <br>
 * 
 * <b>for this to work with your JPA provider you have to make sure your
 * provider calls event callbacks like {@link javax.persistence.PostPersist}
 * correctly on your object hierarchy. As of 13.12.2014 the only JPA provider
 * that was tested AND did this was EclipseLink 2.5.0.</b>
 * 
 * <br>
 * <br>
 * 
 * if your JPA provider is not supported by this class, then you can always
 * implement a EventSource for it and supply it to this project's github.
 * 
 * <br>
 * <br>
 * 
 * TODO: maybe implement a batching Provider wrapping this to reduce the amount
 * of requests we get.
 * 
 * @author Martin Braun
 */
public final class JPAEventSource implements EventSource,
		HSearchJPAEventListener.Listener {
	
	private static boolean ALL_DISABLED = false;

	private boolean disable = false;
	private final Set<Class<?>> listenTo;
	private final boolean preEvents;
	private EventConsumer eventConsumer;

	private JPAEventSource(Set<Class<?>> listenTo, boolean preEvents) {
		this.listenTo = Collections.unmodifiableSet(listenTo);
		this.preEvents = preEvents;
	}

	@Override
	public void disable(boolean disable) {
		this.disable = disable;
	}

	@Override
	public void setEventConsumer(EventConsumer eventConsumer) {
		this.eventConsumer = eventConsumer;
	}

	@Override
	public boolean preEvents() {
		return this.preEvents;
	}

	@Override
	public Set<Class<?>> listenTo() {
		if (ALL_DISABLED || this.disable) {
			return Collections.emptySet();
		}
		return this.listenTo;
	}

	@Override
	public void update(Object entity) {
		if (this.eventConsumer != null) {
			this.eventConsumer.update(entity);
		}
	}

	@Override
	public void persist(Object entity) {
		if (this.eventConsumer != null) {
			this.eventConsumer.index(entity);
		}
	}

	@Override
	public void remove(Object entity) {
		if (this.eventConsumer != null) {
			this.eventConsumer.delete(entity);
		}
	}
	
	public static void disableAll(boolean disableAll) {
		ALL_DISABLED = disableAll;
	}

	public static JPAEventSource register(Set<Class<?>> listenTo,
			boolean preEvents) {
		JPAEventSource provider = new JPAEventSource(listenTo, preEvents);
		HSearchJPAEventListener.listeners.add(provider);
		return provider;
	}

	public static void remove(JPAEventSource provider) {
		HSearchJPAEventListener.listeners.remove(provider);
	}

}
