/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.entity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import org.hibernate.search.standalone.entity.EntityProvider;

public class EntityManagerEntityProvider implements EntityProvider {

	private final EntityManager em;
	private final Map<Class<?>, String> idProperties;

	private static final String QUERY_FORMAT = "SELECT obj FROM %s obj "
			+ "WHERE obj.%s IN :ids";

	// TODO: add support for fetch profiles?

	public EntityManagerEntityProvider(EntityManager em, Map<Class<?>, String> idProperties) {
		this.em = em;
		this.idProperties = idProperties;
	}

	@Override
	public void close() throws IOException {
		this.em.close();
	}

	@Override
	public Object get(Class<?> entityClass, Object id) {
		return this.em.find( entityClass, id );
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public List getBatch(Class<?> entityClass, List<Object> ids) {
		List<Object> ret = new ArrayList<>( ids.size() );
		if ( ids.size() > 0 ) {
			String idProperty = this.idProperties.get( entityClass );
			String queryString = String.format(QUERY_FORMAT, entityClass.getName(), idProperty);
			Query query = this.em.createQuery( queryString );	
			query.setParameter( "ids", ids );
			ret.addAll( query.getResultList() );
		}
		return ret;
	}
}
