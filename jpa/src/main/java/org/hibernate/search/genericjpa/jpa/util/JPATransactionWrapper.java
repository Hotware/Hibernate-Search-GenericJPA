/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.jpa.util;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.transaction.UserTransaction;

/**
 * @author Martin Braun
 */
public class JPATransactionWrapper {

	private final EntityTransaction tx;
	private final UserTransaction utx;
	private final EntityManager em;
	private boolean ignoreExceptionsForUserTransaction;

	public JPATransactionWrapper(EntityTransaction tx, UserTransaction utx, EntityManager em) {
		this.tx = tx;
		this.utx = utx;
		this.em = em;
	}

	public void setIgnoreExceptionsForUserTransaction(boolean ignoreExceptionsForUserTransaction) {
		this.ignoreExceptionsForUserTransaction = ignoreExceptionsForUserTransaction;
	}

	public void begin() {
		if ( this.tx != null ) {
			this.tx.begin();
		}
		else {
			try {
				this.utx.begin();
				this.em.joinTransaction();
			}
			catch (Exception e) {
				if ( !this.ignoreExceptionsForUserTransaction ) {
					throw new RuntimeException( e );
				}
			}
		}
	}

	public void commit() {
		if ( this.tx != null ) {
			this.tx.commit();
		}
		else {
			try {
				this.utx.commit();
			}
			catch (Exception e) {
				if ( !this.ignoreExceptionsForUserTransaction ) {
					throw new RuntimeException( e );
				}
			}
		}
	}

	public void rollback() {
		if ( this.tx != null ) {
			this.tx.rollback();
		}
		else {
			try {
				this.utx.rollback();
			}
			catch (Exception e) {
				if ( !this.ignoreExceptionsForUserTransaction ) {
					throw new RuntimeException( e );
				}
			}
		}
	}

	public static JPATransactionWrapper get(EntityManager em, boolean useUserTransaction) {
		return get( em, useUserTransaction, false );
	}

	public static JPATransactionWrapper get(EntityManager em, boolean useUserTransaction, boolean nullInsteadExceptionUtx) {
		EntityTransaction tx;
		UserTransaction utx;
		if ( !useUserTransaction ) {
			tx = em.getTransaction();
			utx = null;
		}
		else {
			try {
				utx = (UserTransaction) InitialContext.doLookup( "java:comp/UserTransaction" );
			}
			catch (NamingException e) {
				if ( !nullInsteadExceptionUtx ) {
					throw new RuntimeException( e );
				}
				else {
					return null;
				}
			}
			tx = null;
		}
		return new JPATransactionWrapper( tx, utx, em );
	}

}
