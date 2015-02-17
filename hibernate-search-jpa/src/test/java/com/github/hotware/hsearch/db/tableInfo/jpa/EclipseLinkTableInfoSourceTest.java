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

import static org.junit.Assert.assertTrue;

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
				.createEntityManagerFactory("EclipseLink");
	}

	@Test
	public void test() {
		EntityManagerImpl em = (EntityManagerImpl) this.emf
				.createEntityManager();
		try {
			EclipseLinkTableInfoSource tblInfoSrc = new EclipseLinkTableInfoSource(
					em);
			List<TableInfo> tableInfos = tblInfoSrc.getTableInfos(Arrays
					.asList(Place.class, Sorcerer.class, AdditionalPlace.class, AdditionalPlace2.class));
			
			//FIXME: mapping tables have to be tested here as well!
		} finally {
			if (em != null) {
				em.close();
			}
		}
	}

	@After
	public void tearDown() {
		this.emf.close();
	}

}
