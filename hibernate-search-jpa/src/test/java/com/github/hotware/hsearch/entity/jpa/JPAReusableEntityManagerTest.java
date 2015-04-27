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
package com.github.hotware.hsearch.entity.jpa;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Test;

import com.github.hotware.hsearch.db.events.jpa.DatabaseIntegrationTest;
import com.github.hotware.hsearch.db.events.jpa.MetaModelParser;
import com.github.hotware.hsearch.entity.ReusableEntityProvider;
import com.github.hotware.hsearch.jpa.test.entities.Place;

/**
 * @author Martin
 *
 */
public class JPAReusableEntityManagerTest extends DatabaseIntegrationTest {

	@Test
	public void test() throws SQLException, IOException {
		this.setup("EclipseLink");
		MetaModelParser metaModelParser = new MetaModelParser();
		metaModelParser.parse(this.emf.getMetamodel());
		ReusableEntityProvider provider = new JPAReusableEntityProvider(
				this.emf, metaModelParser.getIdProperties(), false);
		for(int i = 0; i < 3; ++i) {
			this.testOnce(provider);
		}
	}
	
	@SuppressWarnings("unchecked")
	private void testOnce(ReusableEntityProvider provider) {
		provider.open();
		assertEquals("Valinor",
				((Place) provider.get(Place.class, this.valinorId)).getName());
		List<Place> batch = (List<Place>) provider.getBatch(Place.class,
				Arrays.asList(this.valinorId, this.helmsDeepId));
		assertEquals(2, batch.size());
		// order is not preserved in getBatch!
		Set<String> names = batch.stream().map((place) -> {
			return place.getName();
		}).collect(Collectors.toSet());
		assertTrue("didn't contain Valinor!", names.contains("Valinor"));
		assertTrue("didn't contain Helm's Deep", names.contains("Helm's Deep"));
		provider.close();
	}

}
