/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.entity;

import javax.persistence.EntityManager;
import javax.transaction.TransactionManager;
import java.io.IOException;
import java.util.List;

import org.hibernate.search.genericjpa.exception.SearchException;
import org.hibernate.search.genericjpa.jpa.util.JPATransactionWrapper;

/**
 * Created by Martin on 08.07.2015.
 */
public final class EntityManagerEntityProviderAdapter {

	private EntityManagerEntityProviderAdapter() {
		//can't touch this!
	}

	public static EntityProvider adapt(
			EntityManagerEntityProvider provider,
			EntityManager em) {
		return new AdapterProvider( provider, em, null, false );
	}

	public static EntityProvider adapt(
			EntityManagerEntityProvider provider,
			EntityManager em, TransactionManager transactionManager) {
		return new AdapterProvider( provider, em, transactionManager, true );
	}

	public static EntityProvider adapt(
			Class<? extends EntityManagerEntityProvider> providerClass,
			EntityManager em,
			TransactionManager transactionManager) {
		try {
			return new AdapterProvider( providerClass.newInstance(), em, transactionManager, true );
		}
		catch (Exception e) {
			throw new SearchException( e );
		}
	}

	private static class AdapterProvider implements EntityProvider {
		private final EntityManagerEntityProvider provider;
		private final EntityManager em;
		private final TransactionManager transactionManager;
		private final boolean wrapInTransaction;

		public AdapterProvider(
				EntityManagerEntityProvider provider,
				EntityManager em,
				TransactionManager transactionManager,
				boolean wrapInTransaction) {
			this.provider = provider;
			this.em = em;
			this.transactionManager = transactionManager;
			this.wrapInTransaction = wrapInTransaction;
		}

		@Override
		public Object get(Class<?> entityClass, Object id) {
			if ( this.wrapInTransaction ) {
				JPATransactionWrapper tx =
						JPATransactionWrapper.get( this.em, this.transactionManager );
				tx.begin();
				try {
					return this.provider.get( this.em, entityClass, id );
				}
				finally {
					tx.commit();
				}
			}
			else {
				return this.provider.get( this.em, entityClass, id );
			}
		}

		@Override
		public List getBatch(Class<?> entityClass, List<Object> id) {
			if ( this.wrapInTransaction ) {
				JPATransactionWrapper tx =
						JPATransactionWrapper.get( this.em, this.transactionManager );
				tx.begin();
				try {
					return this.provider.getBatch( this.em, entityClass, id );
				}
				finally {
					tx.commit();
				}
			}
			else {
				return this.provider.getBatch( this.em, entityClass, id );
			}
		}

		@Override
		public void close() throws IOException {
			this.em.close();
		}
	}

}
