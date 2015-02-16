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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

import org.hibernate.search.backend.SingularTermQuery;
import org.hibernate.search.cfg.spi.SearchConfiguration;
import org.hibernate.search.engine.integration.impl.ExtendedSearchIntegrator;
import org.hibernate.search.spi.SearchIntegratorBuilder;
import org.junit.Before;
import org.junit.Test;

import com.github.hotware.hsearch.db.events.EventModelInfo;
import com.github.hotware.hsearch.db.events.EventModelParser;
import com.github.hotware.hsearch.db.events.IndexUpdater;
import com.github.hotware.hsearch.db.events.IndexUpdater.IndexInformation;
import com.github.hotware.hsearch.entity.ReusableEntityProvider;
import com.github.hotware.hsearch.entity.jpa.JPAReusableEntityProvider;
import com.github.hotware.hsearch.factory.SearchConfigurationImpl;
import com.github.hotware.hsearch.jpa.events.MetaModelParser;
import com.github.hotware.hsearch.jpa.test.entities.Place;
import com.github.hotware.hsearch.jpa.test.entities.PlaceSorcererUpdates;
import com.github.hotware.hsearch.jpa.test.entities.PlaceUpdates;
import com.github.hotware.hsearch.jpa.test.entities.Sorcerer;
import com.github.hotware.hsearch.jpa.test.entities.SorcererUpdates;

/**
 * @author Martin Braun
 *
 */
public class ManualUpdateIntegrationTest extends DatabaseIntegrationTest {

	Map<Class<?>, List<String>> idsForEntities;
	Map<Class<?>, IndexInformation> indexInformations;
	Map<Class<?>, List<Class<?>>> containedInIndexOf;
	Map<Class<?>, SingularTermQuery.Type> idTypesForEntities;
	ReusableEntityProvider entityProvider;
	MetaModelParser metaModelParser;

	@Before
	public void setup() throws SQLException {
		this.idsForEntities = new HashMap<>();
		this.idsForEntities.put(Place.class, Arrays.asList("id"));
		this.idsForEntities.put(Sorcerer.class, Arrays.asList("sorcerers.id"));
		this.indexInformations = new HashMap<>();
		this.indexInformations.put(Place.class, new IndexInformation(
				Place.class, this.idsForEntities));
		this.containedInIndexOf = new HashMap<>();
		this.containedInIndexOf.put(Sorcerer.class, Arrays.asList(Place.class));
		this.containedInIndexOf.put(Place.class, Arrays.asList(Place.class));
		this.idTypesForEntities = new HashMap<>();
		this.idTypesForEntities.put(Place.class, SingularTermQuery.Type.STRING);
		this.idTypesForEntities.put(Sorcerer.class,
				SingularTermQuery.Type.STRING);
		this.setup("EclipseLink_MySQL");
		this.metaModelParser = new MetaModelParser();
		this.metaModelParser.parse(this.emf.getMetamodel());
	}

	@Test
	public void test() throws SQLException, InterruptedException {
		this.setupTriggers();
		try {
			if (this.exceptionString != null) {
				fail(exceptionString);
			}
			SearchConfiguration searchConfiguration = new SearchConfigurationImpl();

			SearchIntegratorBuilder builder = new SearchIntegratorBuilder();
			// we have to build an integrator here (but we don't need it
			// afterwards)
			builder.configuration(searchConfiguration).buildSearchIntegrator();
			metaModelParser.getIndexRelevantEntites().forEach((clazz) -> {
				builder.addClass(clazz);
			});
			ExtendedSearchIntegrator impl = (ExtendedSearchIntegrator) builder
					.buildSearchIntegrator();
			JPAReusableEntityProvider entityProvider = new JPAReusableEntityProvider(
					this.emf, this.metaModelParser.getIdProperties());
			IndexUpdater indexUpdater = new IndexUpdater(
					this.indexInformations, this.containedInIndexOf,
					this.idTypesForEntities, entityProvider, impl);
			EventModelParser eventModelParser = new EventModelParser();
			List<EventModelInfo> eventModelInfos = eventModelParser
					.parse(new HashSet<>(Arrays.asList(PlaceUpdates.class,
							SorcererUpdates.class, PlaceSorcererUpdates.class)));
			JPAUpdateSource updateSource = new JPAUpdateSource(eventModelInfos,
					this.emf, 500, TimeUnit.MILLISECONDS, 10);
			updateSource.setUpdateConsumer(indexUpdater);
			updateSource.start();

			// database already contains stuff, so clear everything out here
			EntityManager em = this.emf.createEntityManager();
			try {
				this.assertCount(impl, 0);
				this.deleteAllData(em);

				Thread.sleep(3000);
				this.assertCount(impl, 0);

				this.writeAllIntoIndex(em, impl);

				this.deleteAllData(em);
				Thread.sleep(3000);
				this.assertCount(impl, 0);

				this.writeAllIntoIndex(em, impl);

				{
					List<Integer> places = this.queryPlaceIds(impl, "name",
							"Valinor");
					assertEquals(
							"this test expects to have exactly one Place named Valinor!",
							1, places.size());
					Integer valinorId = places.get(0);

					{
						EntityTransaction tx = em.getTransaction();
						tx.begin();
						Place valinor = em.find(Place.class, valinorId);
						valinor.setName("Alinor");
						em.persist(valinor);
						tx.commit();
					}
					Thread.sleep(3000);
					assertEquals(
							"shouldn't have found \"Valinor\" in the index anymore!",
							0, this.queryPlaceIds(impl, "name", "Valinor")
									.size());
					this.assertCount(impl, 2);

					{
						String oldName;
						{
							EntityTransaction tx = em.getTransaction();
							tx.begin();
							Place valinor = em.find(Place.class, valinorId);
							Sorcerer someSorcerer = valinor.getSorcerers()
									.iterator().next();
							oldName = someSorcerer.getName();
							assertEquals("should have found \"" + oldName
									+ "\" in the index!", 1, this
									.queryPlaceIds(impl, "sorcerers.name", oldName).size());
							someSorcerer.setName("Odalbert");
							tx.commit();
						}
						Thread.sleep(3000);
						assertEquals("shouldn't have found \"" + oldName
								+ "\" in the index anymore!", 0, this
								.queryPlaceIds(impl, "sorcerers.name", oldName).size());
						this.assertCount(impl, 2);
					}
				}

			} finally {
				if (em != null) {
					em.close();
				}
			}
		} finally {
			this.tearDownTriggers();
		}
	}

	private void writeAllIntoIndex(EntityManager em,
			ExtendedSearchIntegrator impl) throws InterruptedException {
		// and write data in the index again
		this.setupData(em);
		// wait a bit until the UpdateSource sent the appropriate events
		Thread.sleep(3000);
		this.assertCount(impl, 2);
	}

	private void assertCount(ExtendedSearchIntegrator impl, int count) {
		assertEquals(
				count,
				impl.createHSQuery()
						.targetedEntities(Arrays.asList(Place.class))
						.luceneQuery(
								impl.buildQueryBuilder().forEntity(Place.class)
										.get().all().createQuery())
						.queryResultSize());
	}

	private List<Integer> queryPlaceIds(ExtendedSearchIntegrator impl,
			String field, String value) {
		return impl
				.createHSQuery()
				.targetedEntities(Arrays.asList(Place.class))
				.luceneQuery(
						impl.buildQueryBuilder().forEntity(Place.class).get()
								.keyword().onField(field).matching(value)
								.createQuery()).queryEntityInfos().stream()
				.map((entInfo) -> {
					return (Integer) entInfo.getId();
				}).collect(Collectors.toList());
	}

}
