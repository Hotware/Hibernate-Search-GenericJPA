/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.entity;

import javax.persistence.EntityManager;
import java.io.IOException;
import java.util.List;

/**
 * Created by Martin on 08.07.2015.
 */
public final class EntityManagerEntityProviderAdapter {

	private EntityManagerEntityProviderAdapter() {
		//can't touch this!
	}

	public static EntityProvider adapt(EntityManagerEntityProvider provider, EntityManager em) {
		return new Adapter( provider, em );
	}

	private static class Adapter implements EntityProvider {
		private final EntityManagerEntityProvider provider;
		private final EntityManager em;

		public Adapter(EntityManagerEntityProvider provider, EntityManager em) {
			this.provider = provider;
			this.em = em;
		}

		@Override
		public Object get(Class<?> entityClass, Object id) {
			return this.provider.get( this.em, entityClass, id );
		}

		@Override
		public List getBatch(Class<?> entityClass, List<Object> id) {
			return this.provider.getBatch( this.em, entityClass, id );
		}

		@Override
		public void close() throws IOException {
			this.em.close();
		}
	}

}
