/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.batchindexing.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.persistence.PersistenceUnitUtil;

import org.hibernate.search.genericjpa.db.events.IndexUpdater;
import org.hibernate.search.genericjpa.db.events.UpdateConsumer.UpdateInfo;
import org.hibernate.search.genericjpa.entity.EntityProvider;
import org.hibernate.search.genericjpa.entity.ReusableEntityProvider;
import org.hibernate.search.genericjpa.exception.SearchException;

/**
 * @author Martin Braun
 */
public class ObjectHandlerTask implements Runnable {

	private final IndexUpdater indexUpdater;
	private final Class<?> entityClass;
	private final Supplier<EntityProvider> emProvider;
	private final Consumer<EntityProvider> entityManagerDisposer;
	private final PersistenceUnitUtil peristenceUnitUtil;
	private final CountDownLatch latch;
	private final Consumer<Exception> exceptionConsumer;

	private BiConsumer<Class<?>, Integer> objectLoadedProgressMonitor;
	private BiConsumer<Class<?>, Integer> indexProgressMonitor;

	private List<UpdateInfo> batch;

	private Runnable finishConsumer;

	public ObjectHandlerTask(IndexUpdater indexUpdater, Class<?> entityClass, Supplier<EntityProvider> emProvider,
			Consumer<EntityProvider> entityManagerDisposer, PersistenceUnitUtil peristenceUnitUtil) {
		this( indexUpdater, entityClass, emProvider, entityManagerDisposer, peristenceUnitUtil, null, null );
	}

	public ObjectHandlerTask(IndexUpdater indexUpdater, Class<?> entityClass, Supplier<EntityProvider> emProvider,
			Consumer<EntityProvider> entityManagerDisposer, PersistenceUnitUtil peristenceUnitUtil, CountDownLatch latch, Consumer<Exception> exceptionConsumer) {
		this.indexUpdater = indexUpdater;
		this.entityClass = entityClass;
		this.emProvider = emProvider;
		this.entityManagerDisposer = entityManagerDisposer;
		this.peristenceUnitUtil = peristenceUnitUtil;
		this.latch = latch;
		this.exceptionConsumer = exceptionConsumer;
	}

	public ObjectHandlerTask batch(List<UpdateInfo> batch) {
		this.batch = batch;
		return this;
	}

	@Override
	public void run() {
		try {
			EntityProvider entityProvider = this.emProvider.get();
			try {
				try {
					List<Object> ids = this.batch.stream().map( (updateInfo) -> {
						return updateInfo.getId();
					} ).collect( Collectors.toList() );

					@SuppressWarnings("unchecked")
					Map<Object, Object> idsToEntities = (Map<Object, Object>) entityProvider.getBatch( this.entityClass, ids ).stream()
							.collect( Collectors.toMap( (entity) -> {
								return this.peristenceUnitUtil.getIdentifier( entity );
							}, (entity) -> {
								return entity;
							} ) );

					// monitor our progress
					if ( this.objectLoadedProgressMonitor != null ) {
						this.objectLoadedProgressMonitor.accept( this.entityClass, this.batch.size() );
					}

					// signal the updater to update
					// and give it a EntityProvider that already has all the data available
					this.indexUpdater.updateEvent( this.batch, new ReusableEntityProvider() {

						@Override
						public List getBatch(Class<?> entityClass, List<Object> ids) {
							List<Object> ret = new ArrayList<Object>();
							for ( Object id : ids ) {
								ret.add( idsToEntities.get( id ) );
							}
							return ret;
						}

						@Override
						public Object get(Class<?> entityClass, Object id) {
							return idsToEntities.get( id );
						}

						@Override
						public void open() {
							// no-op
						}

						@Override
						public void close() {
							// no-op
						}

					} );
					// monitor our progress
					if ( this.indexProgressMonitor != null ) {
						this.indexProgressMonitor.accept( this.entityClass, this.batch.size() );
					}

					for ( int i = 0; i < this.batch.size(); ++i ) {
						this.latch.countDown();
					}
				}
				catch (Exception e) {
					throw new SearchException( e );
				}
			}
			catch (Exception e) {
				if ( this.exceptionConsumer != null ) {
					this.exceptionConsumer.accept( e );
				}
				// TODO: should throw this?
			}
			finally {
				if ( entityProvider != null ) {
					this.entityManagerDisposer.accept( entityProvider );
				}
			}
		}
		finally {
			if ( this.finishConsumer != null ) {
				this.finishConsumer.run();
			}
		}
	}

	public void objectLoadedProgressMonitor(BiConsumer<Class<?>, Integer> objectLoadedProgressMonitor) {
		this.objectLoadedProgressMonitor = objectLoadedProgressMonitor;
	}

	public void indexProgressMonitor(BiConsumer<Class<?>, Integer> indexProgressMonitor) {
		this.indexProgressMonitor = indexProgressMonitor;
	}

	public void finishConsumer(Runnable finishConsumer) {
		this.finishConsumer = finishConsumer;
	}

}
