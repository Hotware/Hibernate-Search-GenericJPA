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
import static org.junit.Assert.fail;

import java.util.List;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;

import org.junit.Test;

import com.github.hotware.hsearch.db.events.EventModelParser;
import com.github.hotware.hsearch.db.events.EventType;
import com.github.hotware.hsearch.db.events.UpdateConsumer;
import com.github.hotware.hsearch.jpa.test.entities.Place;
import com.github.hotware.hsearch.jpa.test.entities.PlaceSorcererUpdates;
import com.github.hotware.hsearch.jpa.test.entities.Sorcerer;

/**
 * @author Martin
 *
 */
public class JPAUpdateSourceTest {

	@Test
	public void test() throws InterruptedException {
		EntityManagerFactory emf = Persistence
				.createEntityManagerFactory("EclipseLink");
		try {
			EventModelParser parser = new EventModelParser();
			JPAUpdateSource updateSource = new JPAUpdateSource(
					parser.parse(Arrays.asList(PlaceSorcererUpdates.class)),
					emf, 1, TimeUnit.SECONDS, 100);

			{
				EntityManager em = null;
				try {
					em = emf.createEntityManager();
					EntityTransaction tx = em.getTransaction();
					tx.begin();
					PlaceSorcererUpdates update = new PlaceSorcererUpdates();
					update.setEventType(EventType.INSERT);
					update.setId(1);
					update.setPlaceId(2);
					update.setSorcererId(3);
					em.persist(update);
					tx.commit();

				} finally {
					if (em != null) {
						em.close();
					}
				}
			}

			final boolean[] gotEvent = new boolean[2];
			updateSource.setUpdateConsumer(new UpdateConsumer() {

				@Override
				public void updateEvent(Class<?> entityClass,
						List<UpdateInfo> updateInfos) {
					for (UpdateInfo updateInfo : updateInfos) {
						Object id = updateInfo.getId();
						int eventType = updateInfo.getEventType();
						if (id.equals(2) && entityClass.equals(Place.class)
								&& eventType == EventType.INSERT) {
							gotEvent[0] = true;
						} else if (id.equals(3)
								&& entityClass.equals(Sorcerer.class)
								&& eventType == EventType.INSERT) {
							gotEvent[1] = true;
						}
					}
				}
			});
			updateSource.start();
			Thread.sleep(1000 * 3);
			updateSource.stop();
			for (boolean ev : gotEvent) {
				if (!ev) {
					fail("didn't get all events that were expected");
				}
			}

			EntityManager em = null;
			try {
				em = emf.createEntityManager();
				EntityTransaction tx = em.getTransaction();
				tx.begin();
				assertEquals(null, em.find(PlaceSorcererUpdates.class, 1));
				tx.commit();
			} finally {
				if (em != null) {
					em.close();
				}
			}

		} finally {
			emf.close();
		}
	}
}