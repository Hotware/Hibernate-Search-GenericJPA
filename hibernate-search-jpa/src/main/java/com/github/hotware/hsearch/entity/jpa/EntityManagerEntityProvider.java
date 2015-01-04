/*
 * Copyright 2015 Martin Braun
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
	//TODO: add support for fetch profiles?

	public EntityManagerEntityProvider(EntityManager em,
			Map<Class<?>, String> idProperties) {
		this.em = em;
		this.idProperties = idProperties;
	}

	@Override
	public void close() throws IOException {
		this.em.close();
	}

	@Override
	public <T> T get(Class<T> entityClass, Object id) {
		return this.em.find(entityClass, id);
	}

	@Override
	public <T> List<T> getBatch(Class<T> entityClass, List<Object> ids) {
		List<T> ret = new ArrayList<>(ids.size());
		if (ids.size() > 0) {
			CriteriaBuilder cb = this.em.getCriteriaBuilder();
			CriteriaQuery<T> q = cb.createQuery(entityClass);
			Root<T> ent = q.from(entityClass);
			String idProperty = this.idProperties.get(entityClass);
			In<Object> in = cb.in(ent.get(idProperty));
			for (Object id : ids) {
				in.value(id);
			}
			ret.addAll(this.em.createQuery(q.select(ent).where(in))
					.getResultList());
		}
		return ret;
	}
}
