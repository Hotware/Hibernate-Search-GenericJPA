package com.github.hotware.hsearch.factory;

import java.util.Collection;
import java.util.Map;

import org.hibernate.search.backend.spi.Work;
import org.hibernate.search.impl.SimpleInitializer;
import org.hibernate.search.spi.InstanceInitializer;

import com.github.hotware.hsearch.annotations.InIndex;

public class CustomSimpleInitializer implements InstanceInitializer {

	public static CustomSimpleInitializer INSTANCE = new CustomSimpleInitializer();
	
	private CustomSimpleInitializer() {
		
	}
	
	private final SimpleInitializer initializer = SimpleInitializer.INSTANCE;

	public Object unproxy(Object entity) {
		return this.initializer.unproxy(entity);
	}

	public Class<?> getClassFromWork(Work work) {
		return work.getEntityClass() != null ? work.getEntityClass()
				: getClass(work.getEntity());
	}

	@SuppressWarnings("unchecked")
	public <T> Class<T> getClass(T entity) {
		// get the first class in the hierarchy that is actually in the index
		Class<T> clazz = (Class<T>) entity.getClass();
		while ((clazz = (Class<T>) clazz.getSuperclass()) != null) {
			if (clazz.isAnnotationPresent(InIndex.class)) {
				break;
			}
		}
		if (clazz != null) {
			return clazz;
		}
		return this.initializer.getClass(entity);
	}

	public <T> Collection<T> initializeCollection(Collection<T> value) {
		return this.initializer.initializeCollection(value);
	}

	public <K, V> Map<K, V> initializeMap(Map<K, V> value) {
		return this.initializer.initializeMap(value);
	}

	public Object[] initializeArray(Object[] value) {
		return this.initializer.initializeArray(value);
	}

	public int hashCode() {
		return this.initializer.hashCode();
	}

	public boolean equals(Object obj) {
		return this.initializer.equals(obj);
	}

	public String toString() {
		return this.initializer.toString();
	}

}
