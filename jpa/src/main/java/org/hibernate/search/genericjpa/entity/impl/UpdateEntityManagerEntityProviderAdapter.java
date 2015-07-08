/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.entity.impl;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.transaction.TransactionManager;
import java.util.List;
import java.util.Map;

import org.hibernate.search.genericjpa.entity.EntityManagerEntityProvider;

/**
 * Base Adaption class for EntityProviders used for updating in applications that want to access
 * the database via the EntityManager to provide their own logic
 * <p>
 * For basic usage this should be fine though.
 *
 * @hsearch.experimental
 */
public final class UpdateEntityManagerEntityProviderAdapter extends TransactionWrappedReusableEntityProvider {

	private final Map<Class<?>, EntityManagerEntityProvider> providers;

	public UpdateEntityManagerEntityProviderAdapter(
			EntityManagerFactory emf,
			TransactionManager transactionManager,
			Map<Class<?>, EntityManagerEntityProvider> providers) {
		super( emf, transactionManager );
		this.providers = providers;
	}

	@Override
	public Object get(Class<?> entityClass, Object id) {
		if ( this.isOpen() ) {
			//this class is meant for users that want a specific behaviour
			//in Index-Updating, but they might want to reuse the implementations
			this.open();
			try {
				return this.get( this.getEntityManager(), entityClass, id );
			}
			finally {
				this.close();
			}
		}
		else {
			return this.get( this.getEntityManager(), entityClass, id );
		}
	}

	@Override
	public List getBatch(Class<?> entityClass, List<Object> id) {
		if ( this.isOpen() ) {
			//this class is meant for users that want a specific behaviour
			//in Index-Updating, but they might want to reuse the implementations
			this.open();
			try {
				return this.getBatch( this.getEntityManager(), entityClass, id );
			}
			finally {
				this.close();
			}
		}
		else {
			return this.getBatch( this.getEntityManager(), entityClass, id );
		}
	}

	public Object get(EntityManager em, Class<?> entityClass, Object id) {
		return this.providers.get( entityClass ).get( em, entityClass, id );
	}

	public List getBatch(EntityManager em, Class<?> entityClass, List<Object> id) {
		return this.providers.get( entityClass ).getBatch( em, entityClass, id );
	}

}
