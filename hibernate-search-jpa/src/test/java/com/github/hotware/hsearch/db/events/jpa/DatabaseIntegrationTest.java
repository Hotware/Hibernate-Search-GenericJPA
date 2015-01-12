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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;

import com.github.hotware.hsearch.jpa.test.entities.Place;
import com.github.hotware.hsearch.jpa.test.entities.PlaceSorcererUpdates;
import com.github.hotware.hsearch.jpa.test.entities.Sorcerer;

/**
 * @author Martin
 *
 */
public abstract class DatabaseIntegrationTest {

	protected int valinorId = 0;
	protected Place valinor;
	protected EntityManagerFactory emf;

	public void setup(String persistence) {
		this.emf = Persistence.createEntityManagerFactory(persistence);
		EntityManager em = emf.createEntityManager();
		try {
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

			valinorId = valinor.getId();

			this.valinor = valinor;

			em.flush();
			tx.commit();
		} finally {
			if (em != null) {
				em.close();
			}
		}

	}

}
