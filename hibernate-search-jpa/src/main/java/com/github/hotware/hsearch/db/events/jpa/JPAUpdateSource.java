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
 * @author Martin Braun
 *
 */
public class JPAUpdateSource implements UpdateSource {

	private final List<EventModelInfo> eventModelInfos;
	private final EntityManagerFactory emf;
	private final long timeOut;
	private final TimeUnit timeUnit;
	private final int batchSizeForUpdates;
	private final int batchSizeForDatabaseQueries;

	private final List<Class<?>> updateClasses;
	private final Map<Class<?>, EventModelInfo> updateClassToEventModelInfo;
	private final Map<Class<?>, Function<Object, Object>> idAccessorMap;

	private UpdateConsumer updateConsumer;
	private ScheduledExecutorService exec;

	/**
	 * this doesn't do real batching for the databasequeries
	 */
	public JPAUpdateSource(List<EventModelInfo> eventModelInfos,
			EntityManagerFactory emf, long timeOut, TimeUnit timeUnit,
			int batchSizeForUpdates) {
		this(eventModelInfos, emf, timeOut, timeUnit, batchSizeForUpdates, 1);
	}

	/**
	 * this does batching for databaseQueries according to what you set
	 */
	public JPAUpdateSource(List<EventModelInfo> eventModelInfos,
			EntityManagerFactory emf, long timeOut, TimeUnit timeUnit,
			int batchSizeForUpdates, int batchSizeForDatabaseQueries) {
		this.eventModelInfos = eventModelInfos;
		this.emf = emf;
		if (timeOut <= 0) {
			throw new IllegalArgumentException("timeout must be greater than 0");
		}
		this.timeOut = timeOut;
		this.timeUnit = timeUnit;
		if (batchSizeForUpdates <= 0) {
			throw new IllegalArgumentException(
					"batchSize must be greater than 0");
		}
		this.batchSizeForUpdates = batchSizeForUpdates;
		this.batchSizeForDatabaseQueries = batchSizeForDatabaseQueries;
		this.updateClasses = new ArrayList<>();
		this.updateClassToEventModelInfo = new HashMap<>();
		for (EventModelInfo info : eventModelInfos) {
			this.updateClasses.add(info.getUpdateClass());
			this.updateClassToEventModelInfo.put(info.getUpdateClass(), info);
		}
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
				throw new RuntimeException(
						"could not access the \"getId()\" method of class: "
								+ evi.getUpdateClass());
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
		this.exec
				.scheduleWithFixedDelay(() -> {
					EntityManager em = null;
					try {
						em = JPAUpdateSource.this.emf.createEntityManager();
						EntityTransaction tx;
						try {
							// YAY JPA...
							// throwing unchecked exceptions without having a
							// valid getter present is so cool.
						tx = em.getTransaction();
					} catch (IllegalStateException e) {
						tx = null;
					}
					if (tx != null) {
						tx.begin();
					}
					MultiQueryAccess query = this.query(em);
					List<Object[]> toRemove = new ArrayList<>(
							this.batchSizeForUpdates);
					List<UpdateInfo> updateInfos = new ArrayList<>(
							this.batchSizeForUpdates);
					long processed = 0;
					while (query.next()) {
						Object val = query.get();
						toRemove.add(new Object[] { query.entityClass(), val });
						EventModelInfo evi = this.updateClassToEventModelInfo
								.get(query.entityClass());
						for (IdInfo info : evi.getIdInfos()) {
							updateInfos.add(new UpdateInfo(info
									.getEntityClass(), info.getIdAccessor()
									.apply(val), evi.getEventTypeAccessor()
									.apply(val)));
						}
						if (++processed % this.batchSizeForUpdates == 0) {
							this.updateConsumer.updateEvent(updateInfos);
							for (Object[] rem : toRemove) {
								// the class is in rem[0], the entity is in
								// rem[1]
								query.addToNextValuePosition((Class<?>) rem[0],
										-1L);
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
				} catch (Exception e) {
					throw new RuntimeException(
							"Error occured during Update processing!");
				} finally {
					if (em != null) {
						em.close();
					}
				}

			}, 0, this.timeOut, this.timeUnit);
	}

	private MultiQueryAccess query(EntityManager em) {
		Map<Class<?>, Long> countMap = new HashMap<>();
		Map<Class<?>, Query> queryMap = new HashMap<>();
		for (EventModelInfo evi : this.eventModelInfos) {
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
				}, this.batchSizeForDatabaseQueries);
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
