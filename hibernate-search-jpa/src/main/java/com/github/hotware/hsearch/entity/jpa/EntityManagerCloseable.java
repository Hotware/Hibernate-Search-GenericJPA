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

import javax.persistence.EntityGraph;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.FlushModeType;
import javax.persistence.LockModeType;
import javax.persistence.Query;
import javax.persistence.StoredProcedureQuery;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaDelete;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.CriteriaUpdate;
import javax.persistence.metamodel.Metamodel;

/**
 * @author Martin Braun
 */
public class EntityManagerCloseable implements EntityManager {
	
	private final EntityManager em;
	
	public EntityManagerCloseable(EntityManager em) {
		this.em = em;
	}

	public void persist(Object entity) {
		em.persist(entity);
	}

	public <T> T merge(T entity) {
		return em.merge(entity);
	}

	public void remove(Object entity) {
		em.remove(entity);
	}

	public <T> T find(Class<T> entityClass, Object primaryKey) {
		return em.find(entityClass, primaryKey);
	}

	public <T> T find(Class<T> entityClass, Object primaryKey,
			Map<String, Object> properties) {
		return em.find(entityClass, primaryKey, properties);
	}

	public <T> T find(Class<T> entityClass, Object primaryKey,
			LockModeType lockMode) {
		return em.find(entityClass, primaryKey, lockMode);
	}

	public <T> T find(Class<T> entityClass, Object primaryKey,
			LockModeType lockMode, Map<String, Object> properties) {
		return em.find(entityClass, primaryKey, lockMode, properties);
	}

	public <T> T getReference(Class<T> entityClass, Object primaryKey) {
		return em.getReference(entityClass, primaryKey);
	}

	public void flush() {
		em.flush();
	}

	public void setFlushMode(FlushModeType flushMode) {
		em.setFlushMode(flushMode);
	}

	public FlushModeType getFlushMode() {
		return em.getFlushMode();
	}

	public void lock(Object entity, LockModeType lockMode) {
		em.lock(entity, lockMode);
	}

	public void lock(Object entity, LockModeType lockMode,
			Map<String, Object> properties) {
		em.lock(entity, lockMode, properties);
	}

	public void refresh(Object entity) {
		em.refresh(entity);
	}

	public void refresh(Object entity, Map<String, Object> properties) {
		em.refresh(entity, properties);
	}

	public void refresh(Object entity, LockModeType lockMode) {
		em.refresh(entity, lockMode);
	}

	public void refresh(Object entity, LockModeType lockMode,
			Map<String, Object> properties) {
		em.refresh(entity, lockMode, properties);
	}

	public void clear() {
		em.clear();
	}

	public void detach(Object entity) {
		em.detach(entity);
	}

	public boolean contains(Object entity) {
		return em.contains(entity);
	}

	public LockModeType getLockMode(Object entity) {
		return em.getLockMode(entity);
	}

	public void setProperty(String propertyName, Object value) {
		em.setProperty(propertyName, value);
	}

	public Map<String, Object> getProperties() {
		return em.getProperties();
	}

	public Query createQuery(String qlString) {
		return em.createQuery(qlString);
	}

	public <T> TypedQuery<T> createQuery(CriteriaQuery<T> criteriaQuery) {
		return em.createQuery(criteriaQuery);
	}

	public Query createQuery(CriteriaUpdate updateQuery) {
		return em.createQuery(updateQuery);
	}

	public Query createQuery(CriteriaDelete deleteQuery) {
		return em.createQuery(deleteQuery);
	}

	public <T> TypedQuery<T> createQuery(String qlString, Class<T> resultClass) {
		return em.createQuery(qlString, resultClass);
	}

	public Query createNamedQuery(String name) {
		return em.createNamedQuery(name);
	}

	public <T> TypedQuery<T> createNamedQuery(String name, Class<T> resultClass) {
		return em.createNamedQuery(name, resultClass);
	}

	public Query createNativeQuery(String sqlString) {
		return em.createNativeQuery(sqlString);
	}

	public Query createNativeQuery(String sqlString, Class resultClass) {
		return em.createNativeQuery(sqlString, resultClass);
	}

	public Query createNativeQuery(String sqlString, String resultSetMapping) {
		return em.createNativeQuery(sqlString, resultSetMapping);
	}

	public StoredProcedureQuery createNamedStoredProcedureQuery(String name) {
		return em.createNamedStoredProcedureQuery(name);
	}

	public StoredProcedureQuery createStoredProcedureQuery(String procedureName) {
		return em.createStoredProcedureQuery(procedureName);
	}

	public StoredProcedureQuery createStoredProcedureQuery(
			String procedureName, Class... resultClasses) {
		return em.createStoredProcedureQuery(procedureName, resultClasses);
	}

	public StoredProcedureQuery createStoredProcedureQuery(
			String procedureName, String... resultSetMappings) {
		return em.createStoredProcedureQuery(procedureName, resultSetMappings);
	}

	public void joinTransaction() {
		em.joinTransaction();
	}

	public boolean isJoinedToTransaction() {
		return em.isJoinedToTransaction();
	}

	public <T> T unwrap(Class<T> cls) {
		return em.unwrap(cls);
	}

	public Object getDelegate() {
		return em.getDelegate();
	}

	public void close() {
		try {
			em.close();
		} catch(IllegalStateException e) {
			//yay, JPA...
		}
	}

	public boolean isOpen() {
		return em.isOpen();
	}

	public EntityTransaction getTransaction() {
		return em.getTransaction();
	}

	public EntityManagerFactory getEntityManagerFactory() {
		return em.getEntityManagerFactory();
	}

	public CriteriaBuilder getCriteriaBuilder() {
		return em.getCriteriaBuilder();
	}

	public Metamodel getMetamodel() {
		return em.getMetamodel();
	}

	public <T> EntityGraph<T> createEntityGraph(Class<T> rootType) {
		return em.createEntityGraph(rootType);
	}

	public EntityGraph<?> createEntityGraph(String graphName) {
		return em.createEntityGraph(graphName);
	}

	public EntityGraph<?> getEntityGraph(String graphName) {
		return em.getEntityGraph(graphName);
	}

	public <T> List<EntityGraph<? super T>> getEntityGraphs(Class<T> entityClass) {
		return em.getEntityGraphs(entityClass);
	}
	
	

}
