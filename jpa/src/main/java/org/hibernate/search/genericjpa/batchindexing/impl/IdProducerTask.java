/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.batchindexing.impl;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Query;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;

import org.hibernate.search.genericjpa.db.events.EventType;
import org.hibernate.search.genericjpa.db.events.UpdateConsumer;
import org.hibernate.search.genericjpa.db.events.UpdateConsumer.UpdateInfo;
import org.hibernate.search.genericjpa.exception.SearchException;
import org.hibernate.search.genericjpa.factory.StandaloneSearchFactory;
import org.hibernate.search.genericjpa.jpa.util.JPATransactionWrapper;

/**
 * @author Martin Braun
 */
public class IdProducerTask implements Runnable {

	private final Class<?> entityClass;
	private final String idProperty;
	private final StandaloneSearchFactory searchFactory;
	private final EntityManagerFactory emf;
	private final boolean useUserTransaction;
	private final int batchSizeToLoadIds;
	private final int batchSizeToLoadObjects;
	private final int createNewEntityManagerCount;
	private final UpdateConsumer updateConsumer;
	private final List<UpdateInfo> updateInfoBatch;

	// yeah, we this is no real IdProducerTask anymore if we do this here, but whatever
	private final boolean purgeAllOnStart;
	private final boolean optimizeAfterPurge;

	public IdProducerTask(Class<?> entityClass, String idProperty, StandaloneSearchFactory searchFactory, EntityManagerFactory emf, boolean useUserTransaction,
			int batchSizeToLoadIds, int batchSizeToLoadObjects, int createNewEntityManagerCount, UpdateConsumer updateConsumer, boolean purgeAllOnStart,
			boolean optimizeAfterPurge) {
		this.entityClass = entityClass;
		this.idProperty = idProperty;
		this.searchFactory = searchFactory;
		this.emf = emf;
		this.useUserTransaction = useUserTransaction;
		this.batchSizeToLoadIds = batchSizeToLoadIds;
		this.batchSizeToLoadObjects = batchSizeToLoadObjects;
		this.createNewEntityManagerCount = createNewEntityManagerCount;
		this.updateConsumer = updateConsumer;
		this.purgeAllOnStart = purgeAllOnStart;
		this.optimizeAfterPurge = optimizeAfterPurge;
		this.updateInfoBatch = new ArrayList<>( batchSizeToLoadObjects );
	}

	@Override
	public void run() {
		if ( this.purgeAllOnStart ) {
			this.searchFactory.purgeAll( this.entityClass );
			if ( this.optimizeAfterPurge ) {
				this.searchFactory.optimize( this.entityClass );
			}
		}
		long count = this.getTotalCount();
		long processed = 0;
		EntityManager em = null;
		JPATransactionWrapper tx = null;
		while ( processed < count && !Thread.currentThread().isInterrupted() ) {
			if ( em == null ) {
				em = this.emf.createEntityManager();
				tx = JPATransactionWrapper.get( em, this.useUserTransaction );
			}
			tx.begin();
			try {
				Query query = em.createQuery( new StringBuilder().append( "SELECT obj." ).append( this.idProperty ).append( " FROM " )
						.append( em.getMetamodel().entity( this.entityClass ).getName() ).append( " obj ORDER BY obj." ).append( this.idProperty ).toString() );
				query.setFirstResult( (int) processed ).setMaxResults( this.batchSizeToLoadIds ).getResultList();

				this.enlistToBatch( query.getResultList() );

				processed += this.batchSizeToLoadIds;
				tx.commit();
			}
			catch (Exception e) {
				tx.rollback();
				throw new SearchException( e );
			}
			if ( processed % this.createNewEntityManagerCount == 0 ) {
				em.close();
				em = null;
			}
		}
		this.flushBatch();
		if ( em != null ) {
			em.close();
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
			this.updateConsumer.updateEvent( new ArrayList<>( updateInfoBatch ) );
			this.updateInfoBatch.clear();
		}
	}

	public long getTotalCount() {
		long count = 0;
		EntityManager em = null;
		try {
			em = this.emf.createEntityManager();
			JPATransactionWrapper tx = JPATransactionWrapper.get( em, this.useUserTransaction );
			tx.begin();
			try {
				CriteriaBuilder cb = em.getCriteriaBuilder();
				CriteriaQuery<Long> countQuery = cb.createQuery( Long.class );
				countQuery.select( cb.count( countQuery.from( this.entityClass ) ) );
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
