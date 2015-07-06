/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.entity;

import javax.persistence.EntityManagerFactory;
import javax.transaction.TransactionManager;
import java.util.List;
import java.util.Map;

/**
 * @author Martin Braun
 */
public class JPAReusableEntityProvider extends TransactionWrappedReusableEntityProvider
		implements ReusableEntityProvider {

	private final Map<Class<?>, String> idProperties;
	private EntityManagerEntityProvider provider;

	public JPAReusableEntityProvider(
			EntityManagerFactory emf,
			Map<Class<?>, String> idProperties) {
		this( emf, idProperties, null );
	}

	public JPAReusableEntityProvider(
			EntityManagerFactory emf,
			Map<Class<?>, String> idProperties, TransactionManager transactionManager) {
		super( emf, transactionManager );
		this.idProperties = idProperties;
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
		super.close();
		this.provider = null;
	}

	@Override
	public void open() {
		super.open();
		this.provider = new EntityManagerEntityProvider( this.getEntityManager(), this.idProperties );
	}

}
