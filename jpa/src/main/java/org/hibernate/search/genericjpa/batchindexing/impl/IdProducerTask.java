/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.batchindexing.impl;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Query;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.hibernate.search.genericjpa.db.events.EventType;
import org.hibernate.search.genericjpa.db.events.UpdateConsumer;
import org.hibernate.search.genericjpa.db.events.UpdateConsumer.UpdateInfo;
import org.hibernate.search.genericjpa.exception.SearchException;
import org.hibernate.search.genericjpa.jpa.util.JPATransactionWrapper;

/**
 * @author Martin Braun
 */
public class IdProducerTask implements Runnable {

	private final Class<?> entityClass;
	private final String idProperty;
	private final EntityManagerFactory emf;
	private final boolean useJTATransaction;
	private final int batchSizeToLoadIds;
	private final int batchSizeToLoadObjects;
	private final UpdateConsumer updateConsumer;
	private final List<UpdateInfo> updateInfoBatch;
	private final NumberCondition numberCondition;
	private final Consumer<Exception> exceptionConsumer;
	private BiConsumer<Class<?>, Integer> progressMonitor;
	private Runnable finishConsumer;

	public IdProducerTask(
			Class<?> entityClass,
			String idProperty,
			EntityManagerFactory emf,
			boolean useJTATransaction,
			int batchSizeToLoadIds,
			int batchSizeToLoadObjects,
			UpdateConsumer updateConsumer,
			Consumer<Exception> exceptionConsumer,
			NumberCondition numberCondition) {
		this.entityClass = entityClass;
		this.idProperty = idProperty;
		this.emf = emf;
		this.useJTATransaction = useJTATransaction;
		this.batchSizeToLoadIds = batchSizeToLoadIds;
		this.batchSizeToLoadObjects = batchSizeToLoadObjects;
		this.updateConsumer = updateConsumer;
		this.updateInfoBatch = new ArrayList<>( this.batchSizeToLoadIds );
		this.exceptionConsumer = exceptionConsumer;
		this.numberCondition = numberCondition;
	}

	@Override
	public void run() {
		try {
			EntityManager em = this.emf.createEntityManager();
			try {
				JPATransactionWrapper tx = JPATransactionWrapper.get( em, this.useJTATransaction );
				long position = 0;
				long totalCount = this.getTotalCount( entityClass );
				if ( this.numberCondition != null ) {
					// hack...
					this.numberCondition.up( (int) totalCount );
					this.numberCondition.initialSetup();
				}
				while ( position < totalCount && !Thread.currentThread().isInterrupted() ) {
					tx.begin();
					try {
						Query query = em.createQuery(
								new StringBuilder().append( "SELECT obj." ).append( this.idProperty ).append( " FROM " )
										.append( em.getMetamodel().entity( this.entityClass ).getName() ).append(
										" obj ORDER BY obj."
								).append( this.idProperty )
										.toString()
						);
						query.setFirstResult( (int) position ).setMaxResults( this.batchSizeToLoadIds ).getResultList();

						@SuppressWarnings("rawtypes")
						List ids = query.getResultList();
						this.enlistToBatch( ids );

						if ( this.progressMonitor != null ) {
							this.progressMonitor.accept( this.entityClass, ids.size() );
						}

						position += ids.size();
						tx.commit();
					}
					catch (Exception e) {
						tx.rollback();
						this.exceptionConsumer.accept( e );
					}
				}
				this.flushBatch();
			}
			finally {
				if ( em != null ) {
					em.close();
				}
			}
		}
		finally {
			if ( this.finishConsumer != null ) {
				this.finishConsumer.run();
			}
		}
	}

	private void enlistToBatch(@SuppressWarnings("rawtypes") List ids) {
		for ( Object id : ids ) {
			this.updateInfoBatch.add( new UpdateInfo( this.entityClass, id, EventType.INSERT ) );
			if ( this.updateInfoBatch.size() >= this.batchSizeToLoadObjects ) {
				this.flushBatch();
			}
		}
	}

	private void flushBatch() {
		if ( this.updateInfoBatch.size() > 0 ) {
			this.updateConsumer.updateEvent( new ArrayList<>( this.updateInfoBatch ) );
			this.updateInfoBatch.clear();
		}
	}

	public void finishConsumer(Runnable finishConsumer) {
		this.finishConsumer = finishConsumer;
	}

	public void progressMonitor(BiConsumer<Class<?>, Integer> progressMonitor) {
		this.progressMonitor = progressMonitor;
	}

	public long getTotalCount(Class<?> entityClass) {
		long count = 0;
		EntityManager em = null;
		try {
			em = this.emf.createEntityManager();
			JPATransactionWrapper tx = JPATransactionWrapper.get( em, this.useJTATransaction );
			tx.begin();
			try {
				CriteriaBuilder cb = em.getCriteriaBuilder();
				CriteriaQuery<Long> countQuery = cb.createQuery( Long.class );
				countQuery.select( cb.count( countQuery.from( entityClass ) ) );
				count = em.createQuery( countQuery ).getSingleResult();
				tx.commit();
			}
			catch (Exception e) {
				tx.rollback();
				throw new SearchException( e );
			}
		}
		finally {
			if ( em != null ) {
				em.close();
			}
		}
		return count;
	}

}
