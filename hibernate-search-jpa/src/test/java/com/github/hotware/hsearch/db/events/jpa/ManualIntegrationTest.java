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

import javax.persistence.EntityManager;

import org.hibernate.search.backend.SingularTermQuery;
import org.hibernate.search.cfg.spi.SearchConfiguration;
import org.hibernate.search.engine.integration.impl.ExtendedSearchIntegrator;
import org.hibernate.search.spi.SearchIntegratorBuilder;
import org.junit.Before;
import org.junit.Test;

import com.github.hotware.hsearch.db.events.EventModelInfo;
import com.github.hotware.hsearch.db.events.EventModelParser;
import com.github.hotware.hsearch.db.events.jpa.IndexUpdater.IndexInformation;
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
public class ManualIntegrationTest extends DatabaseIntegrationTest {

	Map<Class<?>, String> idsForEntities;
	Map<Class<?>, IndexInformation> indexInformations;
	Map<Class<?>, List<Class<?>>> containedInIndexOf;
	Map<Class<?>, SingularTermQuery.Type> idTypesForEntities;
	ReusableEntityProvider entityProvider;
	MetaModelParser metaModelParser;

	@Before
	public void setup() throws SQLException {
		this.idsForEntities = new HashMap<>();
		this.idsForEntities.put(Place.class, "id");
		this.idsForEntities.put(Sorcerer.class, "sorcerers.id");
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
					this.emf, 1, TimeUnit.SECONDS, 10);
			updateSource.setUpdateConsumer(indexUpdater);
			updateSource.start();

			// database already contains stuff, so clear everything out here
			EntityManager em = this.emf.createEntityManager();
			try {
				this.deleteAllData(em);

				Thread.sleep(2000);

				this.assertCount(impl, 0);

				this.setupData(em);

				Thread.sleep(2000);
				this.assertCount(impl, 2);
			} finally {
				if (em != null) {
					em.close();
				}
			}
		} finally {
			this.tearDownTriggers();
		}
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
}
