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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import org.junit.Test;

import com.github.hotware.hsearch.db.events.EventModelInfo;
import com.github.hotware.hsearch.db.events.EventModelParser;
import com.github.hotware.hsearch.db.events.EventType;
import com.github.hotware.hsearch.db.events.jpa.MultiQueryAccess.ObjectClassWrapper;
import com.github.hotware.hsearch.jpa.test.entities.PlaceSorcererUpdates;
import com.github.hotware.hsearch.jpa.test.entities.PlaceUpdates;

/**
 * @author Martin
 */
public class MultiQueryAccessTest extends DatabaseIntegrationTest {

	@Test
	public void test() throws NoSuchFieldException, SecurityException,
			SQLException {
		this.setup("EclipseLink");

		EntityManager em = null;
		try {
			em = this.emf.createEntityManager();

			EntityTransaction tx = em.getTransaction();

			tx.begin();
			{
				MultiQueryAccess access = this.query(em);
				assertFalse(access.next());
				try {
					access.get();
					fail("expected IllegalStateException");
				} catch (IllegalStateException e) {
					// nothing to see here.
				}
			}
			tx.commit();

			List<Object> del = new ArrayList<>();

			tx.begin();
			{
				PlaceSorcererUpdates up = new PlaceSorcererUpdates();
				up.setEventType(EventType.INSERT);
				up.setId(1L);
				up.setPlaceId(123123);
				up.setSorcererId(123);
				em.persist(up);
			}
			em.flush();

			{
				PlaceSorcererUpdates up = new PlaceSorcererUpdates();
				up.setEventType(EventType.INSERT);
				up.setId(2L);
				up.setPlaceId(123123);
				up.setSorcererId(123);
				em.persist(up);
				del.add(up);
			}
			em.flush();

			{
				PlaceSorcererUpdates up = new PlaceSorcererUpdates();
				up.setEventType(EventType.UPDATE);
				up.setId(3L);
				up.setPlaceId(123123);
				up.setSorcererId(123);
				em.persist(up);
			}
			em.flush();

			{
				PlaceUpdates up = new PlaceUpdates();
				up.setEventType(EventType.INSERT);
				up.setId(1);
				up.setPlaceId(233);
				em.persist(up);
				del.add(up);
			}
			em.flush();

			{
				PlaceUpdates up = new PlaceUpdates();
				up.setEventType(EventType.INSERT);
				up.setId(2);
				up.setPlaceId(233);
				em.persist(up);
			}
			em.flush();

			tx.commit();

			tx.begin();

			// we have to delete stuff here because of the autoincrement thingy
			// in the Updates classes if this is changed, this Test is still
			// correct because we set the ids right
			for (Object obj : del) {
				em.remove(obj);
			}

			tx.commit();

			tx.begin();
			{
				MultiQueryAccess access = this.query(em);
				int cnt = 0;
				while (access.next()) {
					access.get();
					++cnt;
				}
				assertEquals(3, cnt);
			}
			tx.commit();

		} finally {
			if (em != null) {
				em.close();
			}
		}
	}

	private MultiQueryAccess query(EntityManager em)
			throws NoSuchFieldException, SecurityException {
		Map<Class<?>, Long> countMap = new HashMap<>();
		Map<Class<?>, Query> queryMap = new HashMap<>();
		EventModelParser parser = new EventModelParser();
		List<EventModelInfo> infos = parser.parse(Arrays.asList(
				PlaceSorcererUpdates.class, PlaceUpdates.class));
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
		try {
			Field idField = val.clazz.getDeclaredField("id");
			idField.setAccessible(true);
			return ((Number) idField.get(val.object)).longValue();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
