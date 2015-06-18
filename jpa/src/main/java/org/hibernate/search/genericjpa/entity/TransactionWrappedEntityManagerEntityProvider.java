/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.entity;

import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;

import org.hibernate.search.genericjpa.jpa.util.JPATransactionWrapper;

public class TransactionWrappedEntityManagerEntityProvider extends EntityManagerEntityProvider {

	private final boolean useUserTransaction;

	public TransactionWrappedEntityManagerEntityProvider(EntityManager em, Map<Class<?>, String> idProperties, boolean useUserTransaction) {
		super( em, idProperties );
		this.useUserTransaction = useUserTransaction;
	}

	@Override
	public Object get(Class<?> entityClass, Object id) {
		JPATransactionWrapper tx = JPATransactionWrapper.get( this.getEm(), this.useUserTransaction );
		tx.begin();
		try {
			Object ret = super.get( entityClass, id );
			tx.commit();
			return ret;
		}
		catch (Exception e) {
			tx.rollback();
			throw e;
		}
	}

	@SuppressWarnings("rawtypes")
	@Override
	public List getBatch(Class<?> entityClass, List<Object> ids) {
		JPATransactionWrapper tx = JPATransactionWrapper.get( this.getEm(), this.useUserTransaction );
		tx.begin();
		try {
			List ret = super.getBatch( entityClass, ids );
			tx.commit();
			return ret;
		}
		catch (Exception e) {
			tx.rollback();
			throw e;
		}
	}

}