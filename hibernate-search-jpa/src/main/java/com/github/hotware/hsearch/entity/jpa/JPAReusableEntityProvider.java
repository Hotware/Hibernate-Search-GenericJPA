/*
 * Copyright 2015 Martin Braun
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.hotware.hsearch.entity.jpa;

import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import com.github.hotware.hsearch.entity.ReusableEntityProvider;

/**
 * @author Martin Braun
 */
public class JPAReusableEntityProvider implements ReusableEntityProvider {

	private final EntityManagerFactory emf;
	private final Map<Class<?>, String> idProperties;
	private EntityManager em;
	private EntityManagerEntityProvider provider;

	public JPAReusableEntityProvider(EntityManagerFactory emf,
			Map<Class<?>, String> idProperties) {
		this.emf = emf;
		this.idProperties = idProperties;
	}

	@Override
	public Object get(Class<?> entityClass, Object id) {
		if (this.provider == null) {
			throw new IllegalStateException("not open!");
		}
		return this.provider.get(entityClass, id);
	}

	@Override
	@SuppressWarnings("rawtypes")
	public List getBatch(Class<?> entityClass, List<Object> ids) {
		if (this.provider == null) {
			throw new IllegalStateException("not open!");
		}
		return this.provider.getBatch(entityClass, ids);
	}

	@Override
	public void close() {
		try {
			if (this.provider == null) {
				throw new IllegalStateException("already closed!");
			}
			this.commitTransaction();
			this.em.close();
		} finally {
			this.em = null;
			this.provider = null;
		}
	}

	@Override
	public void open() {
		try {
			if (this.provider != null) {
				throw new IllegalStateException("already open!");
			}
			this.em = this.emf.createEntityManager();
			this.provider = new EntityManagerEntityProvider(this.em,
					this.idProperties);
			this.beginTransaction();
		} catch (Throwable e) {
			if(this.em != null) {
				this.em.close();
			}
			this.em = null;
			this.provider = null;
			throw e;
		}
	}

	private void beginTransaction() {
		try {
			this.em.getTransaction().begin();
		} catch (IllegalStateException e) {
			// maybe hacky, but consume exception if managed by JTA
		}
	}

	private void commitTransaction() {
		try {
			this.em.getTransaction().commit();
		} catch (IllegalStateException e) {
			// maybe hacky, but consume exception if managed by JTA
		}
	}

}