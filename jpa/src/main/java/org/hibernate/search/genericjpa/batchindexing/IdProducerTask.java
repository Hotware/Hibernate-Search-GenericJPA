/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.batchindexing;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Query;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;

import org.hibernate.search.genericjpa.db.events.EventType;
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
	private final boolean useUserTransaction;
	private final int batchSize;
	private final int createNewEntityManagerCount;
	private final ExecutorService executorService;
	private final Function<Class<?>, ObjectHandlerTask> objectHandlerSupplier;

	public IdProducerTask(Class<?> entityClass, String idProperty, EntityManagerFactory emf, boolean useUserTransaction, int batchSize,
			int createNewEntityManagerCount, ExecutorService executorService, Function<Class<?>, ObjectHandlerTask> objectHandlerSupplier) {
		this.entityClass = entityClass;
		this.idProperty = idProperty;
		this.emf = emf;
		this.useUserTransaction = useUserTransaction;
		this.batchSize = batchSize;
		this.createNewEntityManagerCount = createNewEntityManagerCount;
		this.executorService = executorService;
		this.objectHandlerSupplier = objectHandlerSupplier;
	}

	@Override
	public void run() {
		long count = this.getTotalCount();
		long processed = 0;
		EntityManager em = null;
		JPATransactionWrapper tx = null;
		while ( processed < count ) {
			if ( em == null ) {
				em = this.emf.createEntityManager();
				tx = JPATransactionWrapper.get( em, this.useUserTransaction );
			}
			tx.begin();
			try {
				Query query = em.createQuery( new StringBuilder().append( "SELECT obj." ).append( this.idProperty ).append( " FROM " )
						.append( em.getMetamodel().entity( this.entityClass ).getName() ).append( " obj ORDER BY obj." ).append( this.idProperty ).toString() );
				query.setFirstResult( (int) processed ).setMaxResults( this.batchSize ).getResultList();
				List<UpdateInfo> currentBatch = new ArrayList<>();
				for ( Object id : query.getResultList() ) {
					currentBatch.add( new UpdateInfo( this.entityClass, id, EventType.INSERT ) );
				}
				ObjectHandlerTask task = this.objectHandlerSupplier.apply( this.entityClass ).batch( currentBatch );
				if ( this.executorService != null ) {
					this.executorService.submit( task );
				}
				else {
					task.run();
				}
				processed += this.batchSize;
				tx.commit();
			}
			catch (Exception e) {
				tx.rollback();
				throw new SearchException( e );
			}
			if ( processed % this.createNewEntityManagerCount == 0 || processed >= this.batchSize ) {
				em.close();
				em = null;
			}
		}
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
