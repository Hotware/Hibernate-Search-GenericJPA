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

import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import org.junit.Before;
import org.junit.Test;

import com.github.hotware.hsearch.db.events.EventModelParser;
import com.github.hotware.hsearch.db.events.EventType;
import com.github.hotware.hsearch.jpa.test.entities.PlaceSorcererUpdates;
import com.github.hotware.hsearch.jpa.test.entities.PlaceUpdates;

/**
 * @author Martin
 */
public class MultiQueryAccessTest extends DatabaseIntegrationTest {

	@Before
	public void setup() throws NoSuchFieldException, SecurityException,
			SQLException {
		this.setup("EclipseLink");

		EntityManager em = null;
		try {
			em = this.emf.createEntityManager();

			EntityTransaction tx = em.getTransaction();

			// hacky, this doesn't belong in the setup method, but whatever
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
				up.setEventType(EventType.DELETE);
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
		} finally {
			if (em != null) {
				em.close();
			}
		}
	}

	@Test
	public void test() throws NoSuchFieldException, SecurityException,
			SQLException, IllegalAccessException, IllegalArgumentException,
			InvocationTargetException, NoSuchMethodException {
		EntityManager em = null;
		try {
			em = this.emf.createEntityManager();
			EntityTransaction tx = em.getTransaction();

			List<Integer> eventOder = new ArrayList<>(Arrays.asList(
					EventType.INSERT, EventType.DELETE, EventType.UPDATE));

			tx.begin();
			{
				MultiQueryAccess access = this.query(em);
				int cnt = 0;
				while (access.next()) {
					Object obj = access.get();
					if (obj instanceof PlaceUpdates) {
						assertEquals(eventOder.remove(0),
								((PlaceUpdates) obj).getEventType());
					} else if (obj instanceof PlaceSorcererUpdates) {
						assertEquals(eventOder.remove(0),
								((PlaceSorcererUpdates) obj).getEventType());
					}
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

	/**
	 * this uses the behaviour of JPAUpdateSource to validate it
	 */
	private MultiQueryAccess query(EntityManager em)
			throws NoSuchFieldException, SecurityException {
		EventModelParser parser = new EventModelParser();
		JPAUpdateSource updateSource = new JPAUpdateSource(
				parser.parse(new HashSet<>(Arrays.asList(
						PlaceSorcererUpdates.class, PlaceUpdates.class))), emf,
				1, TimeUnit.SECONDS, 2, 2);
		return updateSource.query(em);
	}

}
