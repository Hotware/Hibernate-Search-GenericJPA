/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.entity.impl;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;

import org.hibernate.search.genericjpa.entity.ReusableEntityProvider;
import org.hibernate.search.genericjpa.exception.SearchException;

/**
 * Created by Martin on 06.07.2015.
 */
public abstract class TransactionWrappedReusableEntityProvider implements ReusableEntityProvider {

	private final EntityManagerFactory emf;
	private final TransactionManager transactionManager;
	private final boolean useJTATransaction;
	private boolean open = false;
	private boolean startedJTA;
	private EntityManager em;

	public TransactionWrappedReusableEntityProvider(
			EntityManagerFactory emf,
			TransactionManager transactionManager) {
		this.emf = emf;
		this.transactionManager = transactionManager;
		this.useJTATransaction = transactionManager != null;
	}

	protected EntityManager getEntityManager() {
		return this.em;
	}

	@Override
	public void close() {
		try {
			if ( !this.open ) {
				throw new IllegalStateException( "already closed!" );
			}
			this.commitTransaction();
			this.em.close();
		}
		finally {
			this.open = false;
			this.em = null;
		}
	}

	@Override
	public void open() {
		try {
			if ( this.open ) {
				throw new IllegalStateException( "already open!" );
			}
			this.em = this.emf.createEntityManager();
			this.beginTransaction();
			this.open = true;
		}
		catch (Throwable e) {
			if ( this.em != null ) {
				this.em.close();
			}
			this.em = null;
			throw e;
		}
	}

	public boolean isOpen() {
		return this.open;
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
