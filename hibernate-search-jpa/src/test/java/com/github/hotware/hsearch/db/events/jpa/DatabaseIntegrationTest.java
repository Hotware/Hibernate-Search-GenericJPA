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

import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;

import org.junit.After;

import com.github.hotware.hsearch.db.events.EventModelInfo;
import com.github.hotware.hsearch.db.events.EventModelParser;
import com.github.hotware.hsearch.db.events.EventType;
import com.github.hotware.hsearch.db.events.MySQLTriggerSQLStringSource;
import com.github.hotware.hsearch.jpa.test.entities.Place;
import com.github.hotware.hsearch.jpa.test.entities.PlaceSorcererUpdates;
import com.github.hotware.hsearch.jpa.test.entities.PlaceUpdates;
import com.github.hotware.hsearch.jpa.test.entities.Sorcerer;
import com.github.hotware.hsearch.jpa.test.entities.SorcererUpdates;

/**
 * @author Martin
 *
 */
public abstract class DatabaseIntegrationTest {

	protected int valinorId = 0;
	protected int helmsDeepId = 0;
	protected Place valinor;
	protected EntityManagerFactory emf;
	protected EventModelParser parser = new EventModelParser();
	protected String exceptionString;
	protected List<String> dropStrings;

	public void setup(String persistence) throws SQLException {
		this.emf = Persistence.createEntityManagerFactory(persistence);
		EntityManager em = emf.createEntityManager();
		try {
			this.deleteAllData(em);
			this.setupData(em);
		} finally {
			if (em != null) {
				em.close();
			}
		}

	}

	protected void setupData(EntityManager em) {
		EntityTransaction tx = em.getTransaction();
		tx.begin();
		Sorcerer gandalf = new Sorcerer();
		gandalf.setName("Gandalf");
		em.persist(gandalf);

		Sorcerer saruman = new Sorcerer();
		saruman.setName("Saruman");
		em.persist(saruman);

		Sorcerer radagast = new Sorcerer();
		radagast.setName("Radagast");
		em.persist(radagast);

		Sorcerer alatar = new Sorcerer();
		alatar.setName("Alatar");
		em.persist(alatar);

		Sorcerer pallando = new Sorcerer();
		pallando.setName("Pallando");
		em.persist(pallando);

		// populate this database with some stuff
		Place helmsDeep = new Place();
		helmsDeep.setName("Helm's Deep");
		Set<Sorcerer> sorcerersAtHelmsDeep = new HashSet<>();
		sorcerersAtHelmsDeep.add(gandalf);
		gandalf.setPlace(helmsDeep);
		helmsDeep.setSorcerers(sorcerersAtHelmsDeep);
		em.persist(helmsDeep);

		Place valinor = new Place();
		valinor.setName("Valinor");
		Set<Sorcerer> sorcerersAtValinor = new HashSet<>();
		sorcerersAtValinor.add(saruman);
		saruman.setPlace(valinor);
		valinor.setSorcerers(sorcerersAtValinor);
		em.persist(valinor);

		this.valinorId = valinor.getId();
		this.helmsDeepId = helmsDeep.getId();

		this.valinor = valinor;

		em.flush();
		tx.commit();
	}

	protected void deleteAllData(EntityManager em) {
		EntityTransaction tx = em.getTransaction();
		tx.begin();

		{
			@SuppressWarnings("unchecked")
			List<Place> toDelete = new ArrayList<>(em.createQuery(
					"SELECT a FROM Place a").getResultList());
			for (Place place : toDelete) {
				em.remove(place);
			}
			em.flush();
		}

		{
			@SuppressWarnings("unchecked")
			List<Sorcerer> toDelete = new ArrayList<>(em.createQuery(
					"SELECT a FROM Sorcerer a").getResultList());
			for (Sorcerer place : toDelete) {
				em.remove(place);
			}
			em.flush();
		}

		{
			@SuppressWarnings("unchecked")
			List<PlaceSorcererUpdates> toDelete2 = new ArrayList<>(em
					.createQuery("SELECT a FROM PlaceSorcererUpdates a")
					.getResultList());
			for (PlaceSorcererUpdates val : toDelete2) {
				em.remove(val);
			}
			em.flush();
		}
		tx.commit();
	}

	public void setupTriggers() throws SQLException {
		EntityManager em = this.emf.createEntityManager();
		try {
			EntityTransaction tx = em.getTransaction();
			tx.begin();
			List<EventModelInfo> infos = parser.parse(new HashSet<>(Arrays
					.asList(PlaceSorcererUpdates.class, PlaceUpdates.class,
							SorcererUpdates.class)));

			java.sql.Connection connection = em
					.unwrap(java.sql.Connection.class);
			connection.setAutoCommit(false);

			dropStrings = new ArrayList<>();
			// this is just for the unit tests to work properly.
			// normally we shouldn'tdelete the unique_id table or we could run
			// into trouble
			{
				Statement statement = connection.createStatement();
				statement
						.addBatch(connection
								.nativeSQL("DROP TABLE IF EXISTS "
										+ MySQLTriggerSQLStringSource.DEFAULT_UNIQUE_ID_TABLE_NAME));
				statement.executeBatch();
				connection.commit();
			}

			MySQLTriggerSQLStringSource triggerSource = new MySQLTriggerSQLStringSource();
			for (String str : triggerSource.getSetupCode()) {
				Statement statement = connection.createStatement();
				statement.addBatch(connection.nativeSQL(str));
				statement.executeBatch();
				connection.commit();
			}
			for (EventModelInfo info : infos) {
				try {
					for (String setupCode : triggerSource
							.getSpecificSetupCode(info)) {
						Statement statement = connection.createStatement();
						statement.addBatch(connection.nativeSQL(setupCode));
						statement.executeBatch();
						connection.commit();
					}
					dropStrings.addAll(Arrays.asList(triggerSource
							.getSpecificUnSetupCode(info)));
					for (int eventType : EventType.values()) {
						String[] triggerCreationStrings = triggerSource
								.getTriggerCreationCode(info, eventType);
						String[] triggerDropStrings = triggerSource
								.getTriggerDropCode(info, eventType);
						for (String triggerCreationString : triggerCreationStrings) {
							System.out.println("CREATE: "
									+ connection
											.nativeSQL(triggerCreationString));
							dropStrings.addAll(Arrays
									.asList(triggerDropStrings));
							Statement statement = connection.createStatement();
							statement.addBatch(connection
									.nativeSQL(triggerCreationString));
							statement.executeBatch();
							connection.commit();
						}
					}
				} catch (Exception e) {
					connection.rollback();
					exceptionString = e.getMessage();
					// for some reason we don't throw the exception here.
					// there is a legit reason for this though
				}
			}
			tx.commit();
		} finally {
			if (em != null) {
				em.close();
			}
		}
	}

	public void tearDownTriggers() throws SQLException {
		EntityManager em = this.emf.createEntityManager();
		try {
			EntityTransaction tx = em.getTransaction();

			// as we are testing on a mapping relation we cannot check whether
			// the UPDATE triggers are set up correctly. but because of the
			// nature how the triggers are created everything should be fine

			tx.begin();

			java.sql.Connection connection = em
					.unwrap(java.sql.Connection.class);
			connection.setAutoCommit(false);

			for (String dropString : dropStrings) {
				Statement statement = connection.createStatement();
				System.out.println("DROP: " + connection.nativeSQL(dropString));
				statement.addBatch(connection.nativeSQL(dropString));
				statement.executeBatch();
			}

			tx.commit();
		} finally {
			if (em != null) {
				em.close();
			}
		}
	}

	@After
	public void __shutDown() {
		if (this.emf != null) {
			this.emf.close();
		}
	}

}
