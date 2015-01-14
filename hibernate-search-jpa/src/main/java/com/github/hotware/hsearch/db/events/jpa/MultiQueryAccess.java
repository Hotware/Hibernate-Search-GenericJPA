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
package com.github.hotware.hsearch.db.events.jpa;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.persistence.Query;

/**
 * @author Martin
 *
 */
public class MultiQueryAccess {

	private final Map<Class<?>, Long> currentCountMap;
	private final Map<Class<?>, Query> queryMap;
	private final Comparator<ObjectClassWrapper> comparator;
	private final int batchSize;

	private final Map<Class<?>, Long> processed;
	private final Map<Class<?>, LinkedList<Object>> values;

	private Object scheduled;
	private Class<?> entityClass;

	public static class ObjectClassWrapper {

		public final Object object;
		public final Class<?> clazz;

		public ObjectClassWrapper(Object object, Class<?> clazz) {
			super();
			this.object = object;
			this.clazz = clazz;
		}

	}

	/**
	 * this doesn't do real batching as it has a batchSize of 1
	 */
	public MultiQueryAccess(Map<Class<?>, Long> countMap,
			Map<Class<?>, Query> queryMap,
			Comparator<ObjectClassWrapper> comparator) {
		this(countMap, queryMap, comparator, 1);
	}

	/**
	 * this does batching
	 */
	public MultiQueryAccess(Map<Class<?>, Long> countMap,
			Map<Class<?>, Query> queryMap,
			Comparator<ObjectClassWrapper> comparator, int batchSize) {
		if (countMap.size() != queryMap.size()) {
			throw new IllegalArgumentException(
					"countMap.size() must be equal to queryMap.size()");
		}
		this.currentCountMap = countMap;
		this.queryMap = queryMap;
		this.comparator = comparator;
		this.batchSize = batchSize;
		this.processed = new HashMap<>();
		this.values = new HashMap<>();
		for (Class<?> clazz : queryMap.keySet()) {
			this.values.put(clazz, new LinkedList<>());
			this.processed.put(clazz, 0L);
		}
	}

	public boolean next() {
		this.scheduled = null;
		this.entityClass = null;
		List<ObjectClassWrapper> tmp = new ArrayList<>(this.queryMap.size());
		for (Map.Entry<Class<?>, Query> entry : this.queryMap.entrySet()) {
			Class<?> entityClass = entry.getKey();
			Query query = entry.getValue();
			if (!this.currentCountMap.get(entityClass).equals(0L)) {
				if (this.values.get(entityClass).size() == 0) {
					//the last batch is empty. get a new one
					Long processed = this.processed.get(entityClass);
					// yay JPA...
					query.setFirstResult(toInt(processed));
					query.setMaxResults(this.batchSize);
					@SuppressWarnings("unchecked")
					List<Object> list = query.getResultList();
					this.values.get(entityClass).addAll(list);
				}
				Object val = this.values.get(entityClass).getFirst();
				tmp.add(new ObjectClassWrapper(val, entityClass));
			}
		}
		tmp.sort(this.comparator);
		if (tmp.size() > 0) {
			ObjectClassWrapper arr = tmp.get(0);
			this.scheduled = arr.object;
			this.entityClass = arr.clazz;
			this.values.get(entityClass).pop();
			Long processed = this.processed.get(arr.clazz);
			Long newProcessed = this.processed.computeIfPresent(arr.clazz, (
					clazz, old) -> {
				return old + 1;
			});
			if (Math.abs(newProcessed - processed) != 1L) {
				throw new AssertionError(
						"the new processed count should be exactly 1 "
								+ "greater than the old one");
			}
			Long count = this.currentCountMap.get(arr.clazz);
			Long newCount = this.currentCountMap.computeIfPresent(arr.clazz, (
					clazz, old) -> {
				return old - 1;
			});
			if (Math.abs(count - newCount) != 1L) {
				throw new AssertionError(
						"the new old remaining count should be exactly 1 "
								+ "greater than the new one");
			}
		}
		return this.scheduled != null;
	}

	public void addToNextValuePosition(Class<?> clazz, Long change) {
		Long oldValue = this.processed.get(clazz);
		Long newValue = oldValue + change;
		if (newValue < 0L) {
			throw new IllegalArgumentException(
					"change would set the next values"
							+ " position to something less than 0");
		}
		this.processed.put(clazz, newValue);
	}

	public Object get() {
		if (this.scheduled == null) {
			throw new IllegalStateException(
					"either empty or next() has not been called");
		}
		return this.scheduled;
	}

	public Class<?> entityClass() {
		if (this.entityClass == null) {
			throw new IllegalStateException(
					"either empty or next() has not been called");
		}
		return this.entityClass;
	}

	private static int toInt(Long l) {
		return (int) (long) l;
	}

}
