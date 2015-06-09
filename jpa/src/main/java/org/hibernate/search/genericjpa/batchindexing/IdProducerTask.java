/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.batchindexing;

import java.util.concurrent.BlockingQueue;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;

import org.hibernate.search.genericjpa.db.events.UpdateConsumer.UpdateInfo;
import org.hibernate.search.genericjpa.exception.SearchException;
import org.hibernate.search.genericjpa.jpa.util.JPATransactionWrapper;

/**
 * @author Martin Braun
 */
public class IdProducerTask implements Runnable {

	private static final String COUNT_QUERY = "SELECT COUNT(a.%s) FROM %s a ORDER BY a.%s";

	private final BlockingQueue<UpdateInfo> updateInfos;
	private final Class<?> entityClass;
	private final String idProperty;
	private final EntityManagerFactory emf;
	private final boolean useUserTransaction;
	private final int batchSize;
	private final int createNewEntityManagerCount;

	public IdProducerTask(BlockingQueue<UpdateInfo> updateInfos, Class<?> entityClass, String idProperty, EntityManagerFactory emf, boolean useUserTransaction,
			int batchSize, int createNewEntityManagerCount) {
		this.updateInfos = updateInfos;
		this.entityClass = entityClass;
		this.idProperty = idProperty;
		this.emf = emf;
		this.useUserTransaction = useUserTransaction;
		this.batchSize = batchSize;
		this.createNewEntityManagerCount = createNewEntityManagerCount;
	}

	@Override
	public void run() {
		long count = this.getTotalCount();
		long processed = 0;
		EntityManager em = this.emf.createEntityManager();
		JPATransactionWrapper tx = JPATransactionWrapper.get( em, this.useUserTransaction );
		tx.begin();

		tx.commit();
	}

	private long getTotalCount() {
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
