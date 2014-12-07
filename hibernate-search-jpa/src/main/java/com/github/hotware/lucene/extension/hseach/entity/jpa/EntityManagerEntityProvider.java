package com.github.hotware.lucene.extension.hseach.entity.jpa;

import java.io.IOException;

import javax.persistence.EntityManager;

import com.github.hotware.lucene.extension.hsearch.entity.EntityProvider;

public class EntityManagerEntityProvider implements EntityProvider {
	
	private final EntityManager em;
	
	public EntityManagerEntityProvider(EntityManager em) {
		this.em = em;
	}

	@Override
	public void close() throws IOException {
		this.em.close();
	}

	@Override
	public <T> T get(Class<T> entityClass, Object id) {
		return this.em.find(entityClass, id);
	}

}
