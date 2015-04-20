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

import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

import org.junit.Test;

import com.github.hotware.hsearch.db.events.EventType;
import com.github.hotware.hsearch.db.events.UpdateConsumer;
import com.github.hotware.hsearch.jpa.test.entities.Place;
import com.github.hotware.hsearch.jpa.test.entities.PlaceSorcererUpdates;
import com.github.hotware.hsearch.jpa.test.entities.PlaceUpdates;
import com.github.hotware.hsearch.jpa.test.entities.Sorcerer;

/**
 * @author Martin
 */
public class MySQLTriggerIntegrationTest extends DatabaseIntegrationTest {

	@Test
	public void testMySQLIntegration() throws SQLException,
			InterruptedException {
		this.setup("EclipseLink_MySQL");
		this.setupTriggers();

		EntityManager em = null;
		try {
			em = this.emf.createEntityManager();
			EntityTransaction tx = em.getTransaction();
			tx.begin();
			java.sql.Connection connection = em
					.unwrap(java.sql.Connection.class);
			connection.setAutoCommit(false);

			tx.commit();

			tx.begin();
			int countBefore = em
					.createQuery("SELECT a FROM PlaceSorcererUpdates a")
					.getResultList().size();
			em.flush();
			tx.commit();

			tx.begin();
			Place valinorDb = em.find(Place.class, this.valinorId);
			Sorcerer randomNewGuy = new Sorcerer();
			randomNewGuy.setId(-42);
			randomNewGuy.setName("randomNewGuy");
			randomNewGuy.setPlace(valinorDb);
			em.persist(randomNewGuy);
			valinorDb.getSorcerers().add(randomNewGuy);
			tx.commit();

			tx.begin();
			assertEquals(countBefore + 1,
					em.createQuery("SELECT a FROM PlaceSorcererUpdates a")
							.getResultList().size());
			tx.commit();

			tx.begin();
			assertEquals(
					1,
					em.createQuery(
							"SELECT a FROM PlaceSorcererUpdates a WHERE a.eventType = "
									+ EventType.INSERT).getResultList().size());
			tx.commit();

			tx.begin();
			valinorDb.getSorcerers().remove(randomNewGuy);
			em.remove(randomNewGuy);
			tx.commit();

			tx.begin();
			assertEquals(countBefore + 2,
					em.createQuery("SELECT a FROM PlaceSorcererUpdates a")
							.getResultList().size());
			tx.commit();

			tx.begin();
			assertEquals(
					1,
					em.createQuery(
							"SELECT a FROM PlaceSorcererUpdates a WHERE a.eventType = "
									+ EventType.DELETE).getResultList().size());
			tx.commit();

			JPAUpdateSource updateSource = new JPAUpdateSource(
					parser.parse(new HashSet<>(Arrays.asList(
							PlaceSorcererUpdates.class, PlaceUpdates.class))),
					emf, false, 1, TimeUnit.SECONDS, 1, 1);
			updateSource.setUpdateConsumers(Arrays.asList(new UpdateConsumer() {

				@Override
				public void updateEvent(List<UpdateInfo> arg0) {

				}

			}));

			updateSource.start();
			Thread.sleep(1000);
			tx.begin();
			assertEquals(0,
					em.createQuery("SELECT a FROM PlaceSorcererUpdates a")
							.getResultList().size());
			tx.commit();

			if (exceptionString != null) {
				fail(exceptionString);
			}
		} finally {
			if (em != null) {
				em.close();
			}
			this.tearDownTriggers();
		}
	}
}
