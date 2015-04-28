/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package com.github.hotware.hsearch.jpa.events;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.PostPersist;
import javax.persistence.PostRemove;
import javax.persistence.PostUpdate;
import javax.persistence.PrePersist;
import javax.persistence.PreRemove;
import javax.persistence.PreUpdate;

import com.github.hotware.hsearch.factory.SubClassSupportInstanceInitializer;

public final class HSearchJPAEventListener {

	static final Set<Listener> listeners = new HashSet<>();

	@PreUpdate
	public void preUpdate(Object entity) {
		for ( Listener listener : listeners ) {
			if ( listener.preEvents() && listener.listenTo().contains( SubClassSupportInstanceInitializer.INSTANCE.getClass( entity ) ) ) {
				listener.update( entity );
			}
		}
	}

	@PostUpdate
	public void postUpdate(Object entity) {
		for ( Listener listener : listeners ) {
			if ( !listener.preEvents() && listener.listenTo().contains( SubClassSupportInstanceInitializer.INSTANCE.getClass( entity ) ) ) {
				listener.update( entity );
			}
		}
	}

	@PrePersist
	public void prePersist(Object entity) {
		for ( Listener listener : listeners ) {
			if ( listener.preEvents() && listener.listenTo().contains( SubClassSupportInstanceInitializer.INSTANCE.getClass( entity ) ) ) {
				listener.persist( entity );
			}
		}
	}

	@PostPersist
	public void postPersist(Object entity) {
		for ( Listener listener : listeners ) {
			if ( !listener.preEvents() && listener.listenTo().contains( SubClassSupportInstanceInitializer.INSTANCE.getClass( entity ) ) ) {
				listener.persist( entity );
			}
		}
	}

	@PreRemove
	public void preRemove(Object entity) {
		for ( Listener listener : listeners ) {
			if ( listener.preEvents() && listener.listenTo().contains( SubClassSupportInstanceInitializer.INSTANCE.getClass( entity ) ) ) {
				listener.remove( entity );
			}
		}
	}

	@PostRemove
	public void postRemove(Object entity) {
		for ( Listener listener : listeners ) {
			if ( !listener.preEvents() && listener.listenTo().contains( SubClassSupportInstanceInitializer.INSTANCE.getClass( entity ) ) ) {
				listener.remove( entity );
			}
		}
	}

	public static void register(Listener listener) {
		listeners.add( listener );
	}

	public static void remove(Listener listener) {
		listeners.remove( listener );
	}

	public static interface Listener {

		public boolean preEvents();

		/**
		 * must NOT be cached!
		 */
		public Set<Class<?>> listenTo();

		public void update(Object entity);

		public void persist(Object entity);

		public void remove(Object entity);

	}

}
