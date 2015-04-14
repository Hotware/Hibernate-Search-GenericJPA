/*
 * Copyright 2015 Martin Braun
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.hotware.hsearch.jpa;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;

import org.apache.lucene.search.Query;
import org.eclipse.persistence.descriptors.ClassDescriptor;
import org.hibernate.search.query.dsl.QueryBuilder;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import com.github.hotware.hsearch.entity.EntityProvider;
import com.github.hotware.hsearch.entity.jpa.EntityManagerEntityProvider;
import com.github.hotware.hsearch.factory.SearchConfigurationImpl;
import com.github.hotware.hsearch.factory.SearchFactory;
import com.github.hotware.hsearch.factory.SearchFactoryFactory;
import com.github.hotware.hsearch.jpa.events.MetaModelParser;
import com.github.hotware.hsearch.jpa.test.entities.AdditionalPlace;
import com.github.hotware.hsearch.jpa.test.entities.AdditionalPlace2;
import com.github.hotware.hsearch.jpa.test.entities.EmbeddableInfo;
import com.github.hotware.hsearch.jpa.test.entities.Place;
import com.github.hotware.hsearch.jpa.test.entities.Sorcerer;
import com.github.hotware.hsearch.query.HSearchQuery;
import com.github.hotware.hsearch.query.HSearchQuery.Fetch;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class IntegrationTest {

	private int valinorId = 0;
	private Place valinor;
	private EntityManagerFactory emf;

	public void setup(String persistence) {
		this.emf = Persistence.createEntityManagerFactory(persistence);
		EntityManager em = emf.createEntityManager();
		try {
			EntityTransaction tx = em.getTransaction();
			tx.begin();

			@SuppressWarnings("unchecked")
			List<Place> toDelete = new ArrayList<>(em.createQuery(
					"SELECT a FROM Place a").getResultList());
			for (Place place : toDelete) {
				em.remove(place);
			}
			em.flush();

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

	public void shutdown() {
		if (this.emf != null) {
			System.out.println("killing EntityManagerFactory");
			this.emf.close();
		}
	}

	@Test
	public void testEclipseLink() throws IOException {
		this.setup("EclipseLink");
		try {
			this.metaModelParser();
			// this.integration();
		} finally {
			this.shutdown();
		}
	}

	public void eclipseLinkSessionDescriptor(String persistence) {
		EntityManager em = this.emf.createEntityManager();
		try {
			org.eclipse.persistence.sessions.Session session = em
					.unwrap(org.eclipse.persistence.sessions.Session.class);
			for (ClassDescriptor descriptor : session.getDescriptors().values()) {
				System.out.println(descriptor.getTableName());
			}
		} finally {
			if (em != null) {
				em.close();
			}
		}
	}

	public void metaModelParser() throws IOException {
		EntityProvider entityProvider = null;
		SearchFactory searchFactory = null;
		try {
			MetaModelParser parser = new MetaModelParser();
			parser.parse(this.emf.getMetamodel());
			{
				// this test is not sufficient enough
				// see: https://github.com/Hotware/Hibernate-Search-JPA/issues/3
				Sorcerer sorc = this.valinor.getSorcerers().iterator().next();
				Function<Object, Object> func = parser
						.getRootParentAccessorsForClass(Sorcerer.class).get(
								Place.class);
				Place place = (Place) func.apply(sorc);
				assertEquals(this.valinor, place);

				assertEquals(4, parser.getIndexRelevantEntites().size());
			}
		} finally {
			if (entityProvider != null) {
				entityProvider.close();
			}
			if (searchFactory != null) {
				searchFactory.close();
			}
		}
	}

	public void integration() throws IOException {
		EntityProvider entityProvider = null;
		SearchFactory searchFactory = null;
		try {
			EntityManager em = null;
			MetaModelParser parser = new MetaModelParser();
			parser.parse(this.emf.getMetamodel());
			try {
				entityProvider = new EntityManagerEntityProvider(
						em = emf.createEntityManager(),
						parser.getIdProperties());
				EntityTransaction tx = em.getTransaction();
				tx.begin();

				searchFactory = SearchFactoryFactory.createSearchFactory(
						new SearchConfigurationImpl(),
						parser.getIndexRelevantEntites());

				// at first: index all places we can find
				{
					searchFactory.index(em.createQuery("SELECT a FROM Place a")
							.getResultList());
				}

				// TEST BATCH FETCHING
				{
					QueryBuilder qb = searchFactory.buildQueryBuilder()
							.forEntity(Place.class).get();
					Query query = qb
							.bool()
							.should(qb.keyword().onField("sorcerers.name")
									.matching("saruman").createQuery())
							.should(qb.keyword().onField("sorcerers.name")
									.matching("gandalf").createQuery())
							.createQuery();
					HSearchQuery jpaQuery = searchFactory.createQuery(query,
							Place.class);
					@SuppressWarnings("unchecked")
					List<Place> places = jpaQuery.query(entityProvider,
							Fetch.BATCH);
					assertEquals(2, places.size());
				}

				// check whether we not just returned everything in the test
				// before
				// :D
				{
					QueryBuilder qb = searchFactory.buildQueryBuilder()
							.forEntity(Place.class).get();
					Query query = qb
							.bool()
							.should(qb.keyword().onField("sorcerers.name")
									.matching("saruman").createQuery())
							.createQuery();
					HSearchQuery jpaQuery = searchFactory.createQuery(query,
							Place.class);
					@SuppressWarnings("unchecked")
					List<Place> places = (List<Place>) jpaQuery.query(
							entityProvider, Fetch.BATCH);
					assertEquals(1, places.size());
				}

				{
					Place valinorDb = em.find(Place.class, valinorId);
					valinorDb.setName("Valinor123");

					tx.commit();
					tx = em.getTransaction();
					tx.begin();
					// check if we find the renamed version in the index
					{
						List<Place> places = this.findPlaces(searchFactory,
								entityProvider, "name", "valinor123");
						assertEquals(1, places.size());
						assertEquals("Valinor123", places.get(0).getName());
					}

					// the original version should not be found in the index
					{
						List<Place> places = this.findPlaces(searchFactory,
								entityProvider, "name", "valinor");
						assertEquals(0, places.size());
					}

					valinorDb.setName("Valinor");
					tx.commit();
					tx = em.getTransaction();
					tx.begin();

					// we should find it again
					{
						List<Place> places = this.findPlaces(searchFactory,
								entityProvider, "name", "valinor");
						assertEquals(1, places.size());
						assertEquals("Valinor", places.get(0).getName());
					}

					List<EmbeddableInfo> embeddableInfo = new ArrayList<>();
					{
						EmbeddableInfo e1 = new EmbeddableInfo();
						e1.setInfo("random info about valinor");
						e1.setOwnerId(valinorId);
						embeddableInfo.add(e1);
					}
					valinorDb.setInfo(embeddableInfo);
					tx.commit();
					tx = em.getTransaction();
					tx.begin();
				}

				{
					Place place = (Place) em
							.createQuery("SELECT a FROM Place a")
							.getResultList().get(0);

					Sorcerer newSorcerer = new Sorcerer();
					newSorcerer.setName("Odalbort the Unknown");
					newSorcerer.setPlace(place);

					place.getSorcerers().add(newSorcerer);

					// this will trigger a postUpdate on Place :).
					// collections can be handled from the entity owning the
					// entity
					// :)
					place = (Place) em.createQuery("SELECT a FROM Place a")
							.getResultList().get(0);

					// this won't trigger a postUpdate on Place, but on Sorcerer
					newSorcerer.setName("Odalbert");
					tx.commit();
					tx = em.getTransaction();
					tx.begin();

					{
						List<Place> places = this.findPlaces(searchFactory,
								entityProvider, "sorcerers.name", "odalbert");
						assertEquals(1, places.size());
					}

					newSorcerer.setPlace(null);
					tx.commit();
					tx = em.getTransaction();
					tx.begin();
					{
						List<Place> places = this.findPlaces(searchFactory,
								entityProvider, "sorcerers.name", "odalbert");
						// as we set the parent to null
						// but we didn't remove it from the collection
						// of Place this should still be found here
						// from the index of Place, but changes to this Sorcerer
						// will not be propagated up anymore.
						assertEquals(1, places.size());
					}

					newSorcerer.setPlace(place);
					tx.commit();
					tx = em.getTransaction();
					tx.begin();

					place.getSorcerers().remove(newSorcerer);
					tx.commit();
					tx = em.getTransaction();
					tx.begin();
					{
						List<Place> places = this.findPlaces(searchFactory,
								entityProvider, "sorcerers.name", "odalbert");
						assertEquals(0, places.size());
					}

					List<AdditionalPlace> additionalPlace = new ArrayList<>();
					{
						AdditionalPlace a = new AdditionalPlace();
						a.setPlace(new ArrayList<>(Arrays.asList(place)));
						a.setInfo("addi");
						AdditionalPlace2 a2 = new AdditionalPlace2();
						a2.setAdditionalPlace(a);
						a2.setInfo("toast");
						a.setAdditionalPlace2(a2);
						additionalPlace.add(a);
					}
					place.setAdditionalPlace(new ArrayList<>(additionalPlace));
					tx.commit();
					tx = em.getTransaction();
					tx.begin();
					{
						List<Place> places = this.findPlaces(searchFactory,
								entityProvider, "additionalPlace.info", "addi");
						assertEquals(1, places.size());
					}
					{
						List<Place> places = this.findPlaces(searchFactory,
								entityProvider,
								"additionalPlace.additionalPlace2.info",
								"toast");
						assertEquals(1, places.size());
					}

					additionalPlace.get(0).setInfo("addi2");
					tx.commit();
					tx = em.getTransaction();
					tx.begin();
					{
						List<Place> places = this.findPlaces(searchFactory,
								entityProvider, "additionalPlace.info", "addi");
						assertEquals(0, places.size());
					}
					{
						List<Place> places = this
								.findPlaces(searchFactory, entityProvider,
										"additionalPlace.info", "addi2");
						assertEquals(1, places.size());
					}

					additionalPlace.get(0).getAdditionalPlace2()
							.setInfo("goal");
					tx.commit();
					tx = em.getTransaction();
					tx.begin();
					{
						List<Place> places = this
								.findPlaces(
										searchFactory,
										entityProvider,
										"additionalPlace.additionalPlace2.info",
										"goal");
						assertEquals(1, places.size());
					}
					{
						List<Place> places = this.findPlaces(searchFactory,
								entityProvider,
								"additionalPlace.additionalPlace2.info",
								"toast");
						assertEquals(0, places.size());
					}

					additionalPlace.get(0).setInfo("addi");
					additionalPlace.get(0).getAdditionalPlace2()
							.setInfo("toast");
					tx.commit();
					tx = em.getTransaction();
					tx.begin();
					{
						List<Place> places = this.findPlaces(searchFactory,
								entityProvider, "additionalPlace.info", "addi");
						assertEquals(1, places.size());
					}
					{
						List<Place> places = this.findPlaces(searchFactory,
								entityProvider,
								"additionalPlace.additionalPlace2.info",
								"toast");
						assertEquals(1, places.size());
					}

					place.getAdditionalPlace().remove(additionalPlace.get(0));
					additionalPlace.get(0).getPlace().remove(place);
					tx.commit();
					tx = em.getTransaction();
					tx.begin();
					{
						List<Place> places = this.findPlaces(searchFactory,
								entityProvider, "additionalPlace.info", "addi");
						assertEquals(0, places.size());
					}
					{
						List<Place> places = this.findPlaces(searchFactory,
								entityProvider,
								"additionalPlace.additionalPlace2.info",
								"toast");
						assertEquals(0, places.size());
					}
				}

				System.out.println("finished integration test");
				tx.commit();
			} finally {
				if (em != null) {
					em.close();
				}
			}
		} finally {
			if (searchFactory != null) {
				searchFactory.close();
			}
		}
	}

	private List<Place> findPlaces(SearchFactory searchFactory,
			EntityProvider entityProvider, String field, String value) {
		return this.findPlaces(searchFactory, entityProvider, field, value,
				Fetch.FIND_BY_ID);
	}

	private List<Place> findPlaces(SearchFactory searchFactory,
			EntityProvider entityProvider, String field, String value,
			Fetch fetchType) {
		Query query = searchFactory.buildQueryBuilder().forEntity(Place.class)
				.get().keyword().onField(field).matching(value).createQuery();
		HSearchQuery jpaQuery = searchFactory.createQuery(query, Place.class);
		@SuppressWarnings("unchecked")
		List<Place> places = jpaQuery.query(entityProvider, fetchType);
		return places;
	}

}
