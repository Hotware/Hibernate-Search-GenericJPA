/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package com.github.hotware.hsearch.entity.jpa;

import java.util.List;
import java.util.Map;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.TransactionSynchronizationRegistry;
import javax.transaction.UserTransaction;

import com.github.hotware.hsearch.entity.ReusableEntityProvider;

/**
 * @author Martin Braun
 */
public class JPAReusableEntityProvider implements ReusableEntityProvider {

	private final EntityManagerFactory emf;
	private final Map<Class<?>, String> idProperties;
	private final boolean useJTATransaction;
	private EntityManager em;
	private EntityManagerEntityProvider provider;
	private UserTransaction utx;

	public JPAReusableEntityProvider(EntityManagerFactory emf, Map<Class<?>, String> idProperties, boolean useJTATransaction) {
		this.emf = emf;
		this.idProperties = idProperties;
		this.useJTATransaction = useJTATransaction;
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
			this.utx = null;
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
			this.em = new EntityManagerCloseable( this.emf.createEntityManager() );
			this.provider = new EntityManagerEntityProvider( this.em, this.idProperties );
			this.beginTransaction();
		}
		catch (Throwable e) {
			if ( this.em != null ) {
				this.em.close();
			}
			this.utx = null;
			this.em = null;
			this.provider = null;
			throw e;
		}
	}

	// TODO: fix the hacky stuff here

	private void beginTransaction() {
		if ( !this.useJTATransaction ) {
			this.em.getTransaction().begin();
		}
		else {
			try {
				TransactionSynchronizationRegistry registry = InitialContext.doLookup( "java:comp/TransactionSynchronizationRegistry" );
				if ( registry.getTransactionStatus() == Status.STATUS_NO_TRANSACTION ) {
					this.utx = InitialContext.doLookup( "java:comp/UserTransaction" );
					this.utx.begin();
					this.em.joinTransaction();
				}
				else {
					// we didn't start the currently active transaction, so we don't have to handle it here
					this.utx = null;
				}
			}
			catch (NamingException | NotSupportedException | SystemException e1) {
				throw new RuntimeException( "couldn't start a JTA UserTransaction", e1 );
			}
		}
	}

	private void commitTransaction() {
		if ( !this.useJTATransaction ) {
			this.em.getTransaction().commit();
		}
		else {
			try {
				if ( this.utx != null ) {
					// only commit this transaction if it was
					this.utx.commit();
				}
			}
			catch (SecurityException | IllegalStateException | RollbackException | HeuristicMixedException | HeuristicRollbackException | SystemException e1) {
				throw new RuntimeException( "couldn't commit a JTA UserTransaction", e1 );
			}
		}
	}

}
