/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package com.github.hotware.hsearch.entity.jpa;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaBuilder.In;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import com.github.hotware.hsearch.entity.EntityProvider;

public class EntityManagerEntityProvider implements EntityProvider {

	private final EntityManager em;
	private final Map<Class<?>, String> idProperties;

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

	@SuppressWarnings("rawtypes")
	@Override
	public List getBatch(Class<?> entityClass, List<Object> ids) {
		List<Object> ret = new ArrayList<>( ids.size() );
		if ( ids.size() > 0 ) {
			CriteriaBuilder cb = this.em.getCriteriaBuilder();
			CriteriaQuery<?> q = cb.createQuery( entityClass );
			Root<?> ent = q.from( entityClass );
			String idProperty = this.idProperties.get( entityClass );
			In<Object> in = cb.in( ent.get( idProperty ) );
			for ( Object id : ids ) {
				in.value( id );
			}
			ret.addAll( this.em.createQuery( q.multiselect( ent ).where( in ) ).getResultList() );
		}
		return ret;
	}
}
