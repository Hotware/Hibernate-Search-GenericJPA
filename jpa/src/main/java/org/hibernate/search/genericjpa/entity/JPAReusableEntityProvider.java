/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.entity;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;
import java.util.List;
import java.util.Map;

import org.hibernate.search.genericjpa.exception.SearchException;

/**
 * @author Martin Braun
 */
public class JPAReusableEntityProvider implements ReusableEntityProvider {

	private final EntityManagerFactory emf;
	private final Map<Class<?>, String> idProperties;
	private final boolean useJTATransaction;
	private final TransactionManager transactionManager;
	private EntityManager em;
	private EntityManagerEntityProvider provider;
	private boolean startedJTA = false;

	public JPAReusableEntityProvider(
			EntityManagerFactory emf,
			Map<Class<?>, String> idProperties) {
		this( emf, idProperties, null );
	}

	public JPAReusableEntityProvider(
			EntityManagerFactory emf,
			Map<Class<?>, String> idProperties, TransactionManager transactionManager) {
		this.emf = emf;
		this.idProperties = idProperties;
		this.useJTATransaction = transactionManager != null;
		this.transactionManager = transactionManager;
	}

	@Override
	public Object get(Class<?> entityClass, Object id) {
		if ( this.provider == null ) {
			throw new IllegalStateException( "not open!" );
		}
		return this.provider.get( entityClass, id );
	}

	@Override
	@SuppressWarnings("rawtypes")
	public List getBatch(Class<?> entityClass, List<Object> ids) {
		if ( this.provider == null ) {
			throw new IllegalStateException( "not open!" );
		}
		return this.provider.getBatch( entityClass, ids );
	}

	@Override
	public void close() {
		try {
			if ( this.provider == null ) {
				throw new IllegalStateException( "already closed!" );
			}
			this.commitTransaction();
			this.em.close();
		}
		finally {
			this.em = null;
			this.provider = null;
		}
	}

	@Override
	public void open() {
		try {
			if ( this.provider != null ) {
				throw new IllegalStateException( "already open!" );
			}
			this.em = this.emf.createEntityManager();
			this.provider = new EntityManagerEntityProvider( this.em, this.idProperties );
			this.beginTransaction();
		}
		catch (Throwable e) {
			if ( this.em != null ) {
				this.em.close();
			}
			this.em = null;
			this.provider = null;
			throw e;
		}
	}

	public void clearEm() {
		this.em.clear();
	}

	private void beginTransaction() {
		if ( !this.useJTATransaction ) {
			this.em.getTransaction().begin();
		}
		else {
			try {
				if ( this.transactionManager.getStatus() == Status.STATUS_NO_TRANSACTION ) {
					this.transactionManager.begin();
					this.startedJTA = true;
				}
			}
			catch (NotSupportedException | SystemException e) {
				throw new SearchException( "couldn't start a JTA Transaction", e );
			}
		}
	}

	private void commitTransaction() {
		if ( !this.useJTATransaction ) {
			this.em.getTransaction().commit();
		}
		else {
			try {
				if ( this.startedJTA ) {
					this.startedJTA = false;
					this.transactionManager.commit();
				}
			}
			catch (SecurityException | IllegalStateException | RollbackException | HeuristicMixedException | HeuristicRollbackException | SystemException e) {
				throw new SearchException( "couldn't commit a JTA Transaction", e );
			}
		}
	}

}
