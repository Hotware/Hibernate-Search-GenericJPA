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
package com.github.hotware.hsearch.db.tableInfo.jpa;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.List;

import javax.persistence.Persistence;

import org.eclipse.persistence.internal.jpa.EntityManagerFactoryImpl;
import org.eclipse.persistence.internal.jpa.EntityManagerImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.github.hotware.hsearch.db.tableInfo.TableInfo;
import com.github.hotware.hsearch.jpa.test.entities.AdditionalPlace;
import com.github.hotware.hsearch.jpa.test.entities.AdditionalPlace2;
import com.github.hotware.hsearch.jpa.test.entities.JoinTableOneToOne;
import com.github.hotware.hsearch.jpa.test.entities.OneToManyWithoutTable;
import com.github.hotware.hsearch.jpa.test.entities.Place;
import com.github.hotware.hsearch.jpa.test.entities.Sorcerer;

/**
 * @author Martin Braun
 */
public class EclipseLinkTableInfoSourceTest {

	EntityManagerFactoryImpl emf;

	@Before
	public void setup() {
		this.emf = (EntityManagerFactoryImpl) Persistence
				.createEntityManagerFactory("EclipseLink_MySQL");
	}

	@Test
	public void test() {
		EntityManagerImpl em = (EntityManagerImpl) this.emf
				.createEntityManager();
		try {
			EclipseLinkTableInfoSource tblInfoSrc = new EclipseLinkTableInfoSource(
					em);
			List<TableInfo> tableInfos = tblInfoSrc.getTableInfos(Arrays
					.asList(Place.class, Sorcerer.class, AdditionalPlace.class,
							AdditionalPlace2.class,
							OneToManyWithoutTable.class,
							JoinTableOneToOne.class));
			for (TableInfo tableInfo : tableInfos) {
				switch (tableInfo.getTableNames().get(0)) {
				case "PLACE": {
					assertEquals(1, tableInfo.getUpdateEventRelevantIdInfos()
							.size());
					assertEquals(Place.class, tableInfo
							.getUpdateEventRelevantIdInfos().get(0)
							.getEntityClass());
					assertEquals(1, tableInfo.getUpdateEventRelevantIdInfos()
							.get(0).getIdColumns().size());
					// this is no mapping table so we have to have a explicit
					// name here
					assertEquals("PLACE.ID", tableInfo
							.getUpdateEventRelevantIdInfos().get(0)
							.getIdColumns().get(0));
					assertEquals(1, tableInfo.getUpdateEventRelevantIdInfos()
							.get(0).getIdColumnTypes().size());
					assertEquals(Integer.class, tableInfo
							.getUpdateEventRelevantIdInfos().get(0)
							.getIdColumnTypes().get("PLACE.ID"));
					break;
				}
				case "PLACE_JTOTO": {
					assertEquals(2, tableInfo.getUpdateEventRelevantIdInfos()
							.size());
					boolean[] found = new boolean[2];
					for (TableInfo.IdInfo idInfo : tableInfo
							.getUpdateEventRelevantIdInfos()) {
						if (idInfo.getEntityClass().equals(Place.class)) {
							assertEquals(1, idInfo.getIdColumns().size());
							assertEquals("PLACE_ID",
									idInfo.getIdColumns().get(0));
							assertEquals(Integer.class, idInfo
									.getIdColumnTypes().get("PLACE_ID"));
							found[0] = true;
						} else if (idInfo.getEntityClass().equals(
								JoinTableOneToOne.class)) {
							assertEquals(1, idInfo.getIdColumns().size());
							assertEquals("JTOTO_ID",
									idInfo.getIdColumns().get(0));
							assertEquals(Integer.class, idInfo
									.getIdColumnTypes().get("JTOTO_ID"));
							found[1] = true;
						} else {
							fail("either Place or JoinTableOneToOne were expected!");
						}
					}
					assertTrue_(found);
					break;
				}
				case "PLACE_ADDITIONALPLACE": {
					assertEquals(2, tableInfo.getUpdateEventRelevantIdInfos().size());
					boolean found[] = new boolean[2];
					for(TableInfo.IdInfo idInfo : tableInfo.getUpdateEventRelevantIdInfos()) {
						if (idInfo.getEntityClass().equals(Place.class)) {
							assertEquals(1, idInfo.getIdColumns().size());
							assertEquals("place_ID",
									idInfo.getIdColumns().get(0));
							assertEquals(Integer.class, idInfo
									.getIdColumnTypes().get("place_ID"));
							found[0] = true;
						} else if (idInfo.getEntityClass().equals(
								AdditionalPlace.class)) {
							assertEquals(1, idInfo.getIdColumns().size());
							assertEquals("additionalPlace_ID",
									idInfo.getIdColumns().get(0));
							assertEquals(Integer.class, idInfo
									.getIdColumnTypes().get("additionalPlace_ID"));
							found[1] = true;
						} else {
							fail("either Place or AdditionalPlace were expected!");
						}
					}
					assertTrue_(found);
				}
				}
			}

			System.out.println(tableInfos);
		} finally {
			if (em != null) {
				em.close();
			}
		}
	}

	private static void assertTrue_(boolean[] values) {
		assertTrue_(null, values);
	}

	private static void assertTrue_(String message, boolean[] values) {
		for (boolean value : values) {
			if (message != null) {
				assertTrue(message, value);
			} else {
				assertTrue(value);
			}
		}
	}

	@After
	public void tearDown() {
		this.emf.close();
	}

}
