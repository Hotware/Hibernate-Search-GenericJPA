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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import com.github.hotware.hsearch.db.events.EventModelInfo;
import com.github.hotware.hsearch.db.events.UpdateConsumer;
import com.github.hotware.hsearch.db.events.UpdateConsumer.UpdateInfo;
import com.github.hotware.hsearch.db.events.UpdateSource;
import com.github.hotware.hsearch.db.events.EventModelInfo.IdInfo;
import com.github.hotware.hsearch.db.events.jpa.MultiQueryAccess.ObjectClassWrapper;

/**
 * @author Martin
 *
 */
public class JPAUpdateSource implements UpdateSource {

	private final List<EventModelInfo> eventModelInfos;
	private final List<Class<?>> updateClasses;
	private final Map<Class<?>, EventModelInfo> updateClassToEventModelInfo;
	private UpdateConsumer updateConsumer;
	private final EntityManagerFactory emf;
	private ScheduledExecutorService exec;
	private final long timeOut;
	private final TimeUnit timeUnit;
	private final int batchSize;
	private final Map<Class<?>, Function<Object, Object>> idAccessorMap;

	/**
	 * 
	 */
	public JPAUpdateSource(List<EventModelInfo> eventModelInfos,
			EntityManagerFactory emf, long timeOut, TimeUnit timeUnit,
			int batchSize) {
		this.eventModelInfos = eventModelInfos;
		this.updateClasses = new ArrayList<>();
		this.updateClassToEventModelInfo = new HashMap<>();
		for (EventModelInfo info : eventModelInfos) {
			this.updateClasses.add(info.getUpdateClass());
			this.updateClassToEventModelInfo.put(info.getUpdateClass(), info);
		}
		this.emf = emf;
		if (timeOut <= 0) {
			throw new IllegalArgumentException("timeout must be greater than 0");
		}
		this.timeOut = timeOut;
		this.timeUnit = timeUnit;
		if (batchSize <= 0) {
			throw new IllegalArgumentException(
					"batchSize must be greater than 0");
		}
		this.batchSize = batchSize;
		this.idAccessorMap = new HashMap<>();
		for (EventModelInfo evi : eventModelInfos) {
			try {
				Method idMethod = evi.getUpdateClass().getDeclaredMethod(
						"getId");
				idMethod.setAccessible(true);
				idAccessorMap.put(evi.getUpdateClass(), (obj) -> {
					try {
						return idMethod.invoke(obj);
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				});
			} catch (SecurityException | NoSuchMethodException e) {
				throw new RuntimeException(e);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.github.hotware.hsearch.db.events.UpdateSource#setUpdateConsumer(com
	 * .github.hotware.hsearch.db.events.UpdateConsumer)
	 */
	@Override
	public void setUpdateConsumer(UpdateConsumer updateConsumer) {
		this.updateConsumer = updateConsumer;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.github.hotware.hsearch.db.events.UpdateSource#start()
	 */
	@Override
	public void start() {
		if (this.updateConsumer == null) {
			throw new IllegalStateException("updateConsumer was null!");
		}
		this.exec = Executors.newScheduledThreadPool(1);
		this.exec.scheduleWithFixedDelay(() -> {
			EntityManager em = null;
			try {
				em = JPAUpdateSource.this.emf.createEntityManager();
				EntityTransaction tx;
				try {
					// YAY JPA...
					// throwing unchecked exceptions without having a valid
					// getter present is so cool.
				tx = em.getTransaction();
			} catch (IllegalStateException e) {
				tx = null;
			}
			if (tx != null) {
				tx.begin();
			}
			MultiQueryAccess query = this.query(this.eventModelInfos, em);
			List<Object[]> toRemove = new ArrayList<>(this.batchSize);
			List<UpdateInfo> updateInfos = new ArrayList<>(this.batchSize);
			long processed = 0;
			while (query.next()) {
				Object val = query.get();
				toRemove.add(new Object[] { query.entityClass(), val });
				EventModelInfo evi = this.updateClassToEventModelInfo.get(query
						.entityClass());
				for (IdInfo info : evi.getIdInfos()) {
					updateInfos.add(new UpdateInfo(info.getEntityClass(), info
							.getIdAccessor().apply(val), evi
							.getEventTypeAccessor().apply(val)));
				}
				if (++processed % this.batchSize == 0) {
					this.updateConsumer.updateEvent(updateInfos);
					for (Object[] rem : toRemove) {
						// the class is in rem[0], the entity is in rem[1]
						query.addToNextValuePosition((Class<?>) rem[0], -1L);
						em.remove(rem[1]);
					}
					toRemove.clear();
					updateInfos.clear();
					em.flush();
					// clear memory :)
					em.clear();
				}
			}
			if (updateInfos.size() > 0) {
				this.updateConsumer.updateEvent(updateInfos);
				for (Object[] rem : toRemove) {
					// the class is in rem[0], the entity is in rem[1]
					query.addToNextValuePosition((Class<?>) rem[0], -1L);
					em.remove(rem[1]);
				}
				toRemove.clear();
				updateInfos.clear();
				em.flush();
				// clear memory :)
				em.clear();
			}

			if (tx != null) {
				tx.commit();
			}
		} finally {
			if (em != null) {
				em.close();
			}
		}

	}, 0, this.timeOut, this.timeUnit);
	}

	private MultiQueryAccess query(List<EventModelInfo> infos, EntityManager em) {
		Map<Class<?>, Long> countMap = new HashMap<>();
		Map<Class<?>, Query> queryMap = new HashMap<>();
		for (EventModelInfo evi : infos) {
			CriteriaBuilder cb = em.getCriteriaBuilder();
			long count;
			{
				CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
				countQuery
						.select(cb.count(countQuery.from(evi.getUpdateClass())));
				count = em.createQuery(countQuery).getSingleResult();
			}
			countMap.put(evi.getUpdateClass(), count);

			{
				CriteriaQuery<?> q = cb.createQuery(evi.getUpdateClass());
				Root<?> ent = q.from(evi.getUpdateClass());
				q = q.orderBy(cb.asc(ent.get("id")));
				TypedQuery<?> query = em.createQuery(q.multiselect(ent));
				queryMap.put(evi.getUpdateClass(), query);
			}
		}
		MultiQueryAccess access = new MultiQueryAccess(
				countMap,
				queryMap,
				(first, second) -> {
					int res = Long.compare(this.id(first), this.id(second));
					if (res == 0) {
						throw new IllegalStateException(
								"database contained two update entries with the same id!");
					}
					return res;
				});
		return access;
	}

	private Long id(ObjectClassWrapper val) {
		return ((Number) this.idAccessorMap.get(val.clazz).apply(val.object))
				.longValue();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.github.hotware.hsearch.db.events.UpdateSource#stop()
	 */
	@Override
	public void stop() {
		if (this.exec != null) {
			this.exec.shutdown();
		}
	}

}
