/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.jpa.util;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

import org.hibernate.search.genericjpa.exception.SearchException;

/**
 * <b>internal, don't use this across different threads!</b>
 *
 * @author Martin Braun
 */
public class JPATransactionWrapper {

	private final EntityTransaction tx;
	private final EntityManager em;
	private boolean ignoreExceptionsForJTATransaction;

	public JPATransactionWrapper(EntityTransaction tx, EntityManager em) {
		this.tx = tx;
		this.em = em;
	}

	public static JPATransactionWrapper get(EntityManager em, boolean useJTATransaction) {
		EntityTransaction tx;
		if ( !useJTATransaction ) {
			tx = em.getTransaction();
		}
		else {
			tx = null;
		}
		return new JPATransactionWrapper( tx, em );
	}

	public void setIgnoreExceptionsForJTATransaction(boolean ignoreExceptionsForJTATransaction) {
		this.ignoreExceptionsForJTATransaction = ignoreExceptionsForJTATransaction;
	}

	public void begin() {
		if ( this.tx != null ) {
			this.tx.begin();
		}
		else {
			try {
				JTALookup.lookup().begin();
				this.em.joinTransaction();
			}
			catch (Exception e) {
				if ( !this.ignoreExceptionsForJTATransaction ) {
					throw new SearchException( e );
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
				JTALookup.lookup().commit();
			}
			catch (Exception e) {
				if ( !this.ignoreExceptionsForJTATransaction ) {
					throw new SearchException( e );
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
				JTALookup.lookup().rollback();
			}
			catch (Exception e) {
				if ( !this.ignoreExceptionsForJTATransaction ) {
					throw new SearchException( e );
				}
			}
		}
	}

}
