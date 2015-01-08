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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import com.github.hotware.hsearch.db.events.EventModelInfo;
import com.github.hotware.hsearch.db.events.UpdateConsumer;
import com.github.hotware.hsearch.db.events.UpdateConsumer.UpdateInfo;
import com.github.hotware.hsearch.db.events.UpdateSource;
import com.github.hotware.hsearch.db.events.EventModelInfo.IdInfo;

/**
 * @author Martin
 *
 */
public class JPAUpdateSource implements UpdateSource {

	private final List<EventModelInfo> eventModelInfos;
	private UpdateConsumer updateConsumer;
	private final EntityManagerFactory emf;
	private ScheduledExecutorService exec;
	private final long timeOut;
	private final TimeUnit timeUnit;
	private final int batchSize;

	/**
	 * 
	 */
	public JPAUpdateSource(List<EventModelInfo> eventModelInfos,
			EntityManagerFactory emf, long timeOut, TimeUnit timeUnit,
			int batchSize) {
		this.eventModelInfos = eventModelInfos;
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
		this.exec.scheduleWithFixedDelay(new Runnable() {

			@Override
			public void run() {
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
					for (EventModelInfo evi : JPAUpdateSource.this.eventModelInfos) {
						CriteriaBuilder cb = em.getCriteriaBuilder();
						long count;
						{
							CriteriaQuery<Long> countQuery = cb
									.createQuery(Long.class);
							countQuery.select(cb.count(countQuery.from(evi
									.getUpdateClass())));
							count = em.createQuery(countQuery)
									.getSingleResult();
						}

						CriteriaQuery<?> q = cb.createQuery(evi
								.getUpdateClass());
						Root<?> ent = q.from(evi.getUpdateClass());
						TypedQuery<?> query = em
								.createQuery(q.multiselect(ent));
						query.setMaxResults(JPAUpdateSource.this.batchSize);

						long processed = 0;
						while (processed < count) {
							// ... who designed this API???
							query.setFirstResult((int) processed);
							List<Object> toRemove = new ArrayList<>(
									JPAUpdateSource.this.batchSize);

							Map<Class<?>, List<UpdateInfo>> updatesPerEntity = new HashMap<>();
							for (Object update : query.getResultList()) {
								Integer eventCase = evi.getEventTypeAccessor()
										.apply(update);
								for (IdInfo idInfo : evi.getIdInfos()) {
									Class<?> entityClass = idInfo
											.getEntityClass();
									Object id = idInfo.getIdAccessor().apply(
											update);
									updatesPerEntity
											.computeIfAbsent(
													entityClass,
													(clazz) -> {
														return new ArrayList<>(
																JPAUpdateSource.this.batchSize);
													}).add(
													new UpdateInfo(id,
															eventCase));
								}
								toRemove.add(update);
							}
							for (Map.Entry<Class<?>, List<UpdateInfo>> entry : updatesPerEntity
									.entrySet()) {
								JPAUpdateSource.this.updateConsumer
										.updateEvent(entry.getKey(),
												entry.getValue());
							}
							for (Object rem : toRemove) {
								em.remove(rem);
							}
							em.flush();
							processed += JPAUpdateSource.this.batchSize;
							// clear memory :)
							em.clear();
						}
					}
					if (tx != null) {
						tx.commit();
					}
				} finally {
					if (em != null) {
						em.close();
					}
				}
			}

		}, 0, this.timeOut, this.timeUnit);
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
