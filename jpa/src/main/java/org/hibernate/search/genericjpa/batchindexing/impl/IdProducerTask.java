/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.batchindexing.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Query;

import org.hibernate.search.genericjpa.db.events.EventType;
import org.hibernate.search.genericjpa.db.events.UpdateConsumer;
import org.hibernate.search.genericjpa.db.events.UpdateConsumer.UpdateInfo;
import org.hibernate.search.genericjpa.exception.AssertionFailure;
import org.hibernate.search.genericjpa.jpa.util.JPATransactionWrapper;

/**
 * @author Martin Braun
 */
public class IdProducerTask implements Runnable {

	private final Class<?> entityClass;
	private final String idProperty;
	private final EntityManagerFactory emf;
	private final boolean useUserTransaction;
	private final int batchSizeToLoadIds;
	private final int batchSizeToLoadObjects;
	private final UpdateConsumer updateConsumer;
	private final List<UpdateInfo> updateInfoBatch;

	private long startingPosition;
	private long count;
	private long totalCount;

	private BiConsumer<Class<?>, Integer> progressMonitor;

	private final Consumer<Exception> exceptionConsumer;

	private Runnable finishConsumer;

	public IdProducerTask(Class<?> entityClass, String idProperty, EntityManagerFactory emf, boolean useUserTransaction, int batchSizeToLoadIds,
			int batchSizeToLoadObjects, UpdateConsumer updateConsumer, boolean purgeAllOnStart, boolean optimizeAfterPurge,
			Consumer<Exception> exceptionConsumer) {
		this.entityClass = entityClass;
		this.idProperty = idProperty;
		this.emf = emf;
		this.useUserTransaction = useUserTransaction;
		this.batchSizeToLoadIds = batchSizeToLoadIds;
		this.batchSizeToLoadObjects = batchSizeToLoadObjects;
		this.updateConsumer = updateConsumer;
		this.updateInfoBatch = new ArrayList<>( (int) Math.min( this.batchSizeToLoadIds, this.count ) );
		this.exceptionConsumer = exceptionConsumer;
	}

	@Override
	public void run() {
		try {
			if ( this.totalCount == 0 ) {
				throw new AssertionFailure( "totalCount can not be equal to 0" );
			}
			long position = this.startingPosition;
			EntityManager em = this.emf.createEntityManager();
			try {
				JPATransactionWrapper tx = JPATransactionWrapper.get( em, this.useUserTransaction );
				while ( position < this.totalCount && !Thread.currentThread().isInterrupted() ) {
					tx.begin();
					try {
						Query query = em.createQuery( new StringBuilder().append( "SELECT obj." ).append( this.idProperty ).append( " FROM " )
								.append( em.getMetamodel().entity( this.entityClass ).getName() ).append( " obj ORDER BY obj." ).append( this.idProperty )
								.toString() );
						query.setFirstResult( (int) position ).setMaxResults( (int) Math.min( this.batchSizeToLoadIds, this.count ) ).getResultList();

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
			if(this.finishConsumer != null) {
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

	public void startingPosition(long startingPosition) {
		this.startingPosition = startingPosition;
	}

	public void progressMonitor(BiConsumer<Class<?>, Integer> progressMonitor) {
		this.progressMonitor = progressMonitor;
	}

	public void count(long count) {
		this.count = count;
	}

	public void totalCount(long totalCount) {
		this.totalCount = totalCount;
	}

}
