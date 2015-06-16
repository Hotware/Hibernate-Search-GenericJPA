/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.batchindexing.impl;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.persistence.PersistenceUnitUtil;

import org.hibernate.search.backend.AddLuceneWork;
import org.hibernate.search.backend.spi.BatchBackend;
import org.hibernate.search.bridge.TwoWayFieldBridge;
import org.hibernate.search.bridge.spi.ConversionContext;
import org.hibernate.search.bridge.util.impl.ContextualExceptionBridgeHelper;
import org.hibernate.search.engine.spi.DocumentBuilderIndexedEntity;
import org.hibernate.search.engine.spi.EntityIndexBinding;
import org.hibernate.search.genericjpa.db.events.UpdateConsumer.UpdateInfo;
import org.hibernate.search.genericjpa.entity.EntityProvider;
import org.hibernate.search.genericjpa.exception.SearchException;
import org.hibernate.search.genericjpa.factory.SubClassSupportInstanceInitializer;
import org.hibernate.search.indexes.interceptor.EntityIndexingInterceptor;
import org.hibernate.search.indexes.interceptor.IndexingOverride;
import org.hibernate.search.spi.InstanceInitializer;

/**
 * @author Martin Braun
 */
public class ObjectHandlerTask implements Runnable {

	private final BatchBackend batchBackend;

	private final Class<?> entityClass;
	private final EntityIndexBinding entityIndexBinding;
	private final Supplier<EntityProvider> emProvider;
	private final Consumer<EntityProvider> entityManagerDisposer;
	private final PersistenceUnitUtil peristenceUnitUtil;
	private final CountDownLatch latch;
	private final Consumer<Exception> exceptionConsumer;

	private BiConsumer<Class<?>, Integer> objectLoadedProgressMonitor;
	private BiConsumer<Class<?>, Integer> documentBuiltProgressMonitor;

	private List<UpdateInfo> batch;

	private Runnable finishConsumer;

	private static final InstanceInitializer INITIALIZER = SubClassSupportInstanceInitializer.INSTANCE;

	public ObjectHandlerTask(BatchBackend batchBackend, Class<?> entityClass, EntityIndexBinding entityIndexBinding, Supplier<EntityProvider> emProvider,
			Consumer<EntityProvider> entityManagerDisposer, PersistenceUnitUtil peristenceUnitUtil) {
		this( batchBackend, entityClass, entityIndexBinding, emProvider, entityManagerDisposer, peristenceUnitUtil, null, null );
	}

	public ObjectHandlerTask(BatchBackend batchBackend, Class<?> entityClass, EntityIndexBinding entityIndexBinding, Supplier<EntityProvider> emProvider,
			Consumer<EntityProvider> entityManagerDisposer, PersistenceUnitUtil peristenceUnitUtil, CountDownLatch latch, Consumer<Exception> exceptionConsumer) {
		this.batchBackend = batchBackend;
		this.entityClass = entityClass;
		this.entityIndexBinding = entityIndexBinding;
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

					ContextualExceptionBridgeHelper conversionContext = new ContextualExceptionBridgeHelper();
					for ( Object id : ids ) {
						this.index( idsToEntities.get( id ), INITIALIZER, conversionContext );
					}

					// monitor our progress
					if ( this.documentBuiltProgressMonitor != null ) {
						this.documentBuiltProgressMonitor.accept( this.entityClass, this.batch.size() );
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

	public void documentBuiltProgressMonitor(BiConsumer<Class<?>, Integer> indexProgressMonitor) {
		this.documentBuiltProgressMonitor = indexProgressMonitor;
	}

	public void finishConsumer(Runnable finishConsumer) {
		this.finishConsumer = finishConsumer;
	}

	@SuppressWarnings("unchecked")
	private void index(Object entity, InstanceInitializer sessionInitializer, ConversionContext conversionContext) throws InterruptedException {
		Serializable id = (Serializable) this.peristenceUnitUtil.getIdentifier( entity );

		if ( entityIndexBinding == null ) {
			// it might be possible to receive not-indexes subclasses of the currently indexed type;
			// being not-indexed, we skip them.
			// FIXME for improved performance: avoid loading them in an early phase.
			return;
		}

		@SuppressWarnings("rawtypes")
		EntityIndexingInterceptor interceptor = this.entityIndexBinding.getEntityIndexingInterceptor();
		if ( interceptor != null ) {
			IndexingOverride onAdd = interceptor.onAdd( entity );
			switch ( onAdd ) {
				case REMOVE:
				case SKIP:
					return;
				default:
					break;
			}
			// default: continue indexing this instance
		}

		DocumentBuilderIndexedEntity docBuilder = this.entityIndexBinding.getDocumentBuilder();
		TwoWayFieldBridge idBridge = docBuilder.getIdBridge();
		conversionContext.pushProperty( docBuilder.getIdKeywordName() );
		String idInString = null;
		try {
			idInString = conversionContext.setClass( this.entityClass ).twoWayConversionContext( idBridge ).objectToString( id );
		}
		finally {
			conversionContext.popProperty();
		}
		// depending on the complexity of the object graph going to be indexed it's possible
		// that we hit the database several times during work construction.
		AddLuceneWork addWork = docBuilder.createAddWork( null, this.entityClass, entity, id, idInString, sessionInitializer, conversionContext );
		this.batchBackend.enqueueAsyncWork( addWork );
	}

}
