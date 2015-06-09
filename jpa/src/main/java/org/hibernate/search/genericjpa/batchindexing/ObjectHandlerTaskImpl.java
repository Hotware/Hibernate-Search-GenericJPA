/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.batchindexing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import org.hibernate.search.genericjpa.exception.SearchException;
import org.hibernate.search.genericjpa.db.events.IndexUpdater;
import org.hibernate.search.genericjpa.db.events.UpdateConsumer.UpdateInfo;
import org.hibernate.search.genericjpa.entity.EntityManagerEntityProvider;
import org.hibernate.search.genericjpa.jpa.util.JPATransactionWrapper;
import org.hibernate.search.standalone.entity.ReusableEntityProvider;

/**
 * @author Martin Braun
 */
public class ObjectHandlerTaskImpl implements ObjectHandlerTask {

	private final IndexUpdater indexUpdater;
	private final Class<?> entityClass;
	private final String idProperty;
	private final EntityManager em;
	private final boolean useUserTransaction;
	private final Map<Class<?>, String> idProperties;
	private final Consumer<EntityManager> entityManagerDisposer;

	private List<UpdateInfo> batch;

	public ObjectHandlerTaskImpl(IndexUpdater indexUpdater, Class<?> entityClass, String idProperty, EntityManager em, boolean useUserTransaction,
			int createNewEntityManagerCount, Map<Class<?>, String> idProperties, Consumer<EntityManager> entityManagerDisposer) {
		this.indexUpdater = indexUpdater;
		this.entityClass = entityClass;
		this.idProperty = idProperty;
		this.em = em;
		this.useUserTransaction = useUserTransaction;
		this.idProperties = idProperties;
		this.entityManagerDisposer = entityManagerDisposer;
	}

	@Override
	public ObjectHandlerTaskImpl batch(List<UpdateInfo> batch) {
		this.batch = batch;
		return this;
	}

	@Override
	public void run() {
		JPATransactionWrapper tx = JPATransactionWrapper.get( this.em, this.useUserTransaction );
		tx.begin();
		try {
			@SuppressWarnings("resource")
			// this shouldn't be closed in here as the EntityManager will be reused
			EntityManagerEntityProvider providerForBatch = new EntityManagerEntityProvider( this.em, idProperties );
			List<Object> ids = this.batch.stream().map( (updateInfo) -> {
				return updateInfo.getId();
			} ).collect( Collectors.toList() );
			@SuppressWarnings("unchecked")
			Map<Object, Object> idsToEntities = (Map<Object, Object>) providerForBatch.getBatch( this.entityClass, ids ).stream()
					.collect( Collectors.toMap( (entity) -> {
						try {
							return entity.getClass().getMethod( this.idProperty ).invoke( entity );
						}
						catch (Exception e) {
							throw new SearchException( e );
						}
					}, (entity) -> {
						return entity;
					} ) );
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
			tx.commit();
			this.entityManagerDisposer.accept( this.em );
		}
		catch (Exception e) {
			tx.rollback();
			throw new SearchException( e );
		}
	}
}
