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
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hibernate.search.backend.SingularTermQuery;
import org.junit.Test;

import com.github.hotware.hsearch.db.events.EventType;
import com.github.hotware.hsearch.db.events.UpdateConsumer.UpdateInfo;
import com.github.hotware.hsearch.db.events.jpa.IndexUpdater.IndexInformation;
import com.github.hotware.hsearch.db.events.jpa.IndexUpdater.IndexWrapper;
import com.github.hotware.hsearch.entity.ReusableEntityProvider;
import com.github.hotware.hsearch.factory.Transaction;
import com.github.hotware.hsearch.jpa.test.entities.Place;
import com.github.hotware.hsearch.jpa.test.entities.Sorcerer;

/**
 * @author Martin Braun
 */
public class IndexUpdaterTest {

	@Test
	public void testWithoutIndex() {
		Map<Class<?>, String> idsForEntities = new HashMap<>();
		idsForEntities.put(Sorcerer.class, "sorcerer.id");
		idsForEntities.put(Place.class, "id");
		Map<Class<?>, IndexInformation> indexInformations = new HashMap<>();
		indexInformations.put(Place.class, new IndexInformation(Place.class,
				idsForEntities));
		Map<Class<?>, List<Class<?>>> containedInIndexOf = new HashMap<>();
		containedInIndexOf.put(Sorcerer.class, Arrays.asList(Place.class));
		containedInIndexOf.put(Place.class, Arrays.asList(Place.class));
		Map<Class<?>, SingularTermQuery.Type> idTypesForEntities = new HashMap<>();
		idTypesForEntities.put(Place.class, SingularTermQuery.Type.INT);
		idTypesForEntities.put(Sorcerer.class, SingularTermQuery.Type.INT);
		ReusableEntityProvider entityProvider = new ReusableEntityProvider() {

			@SuppressWarnings("rawtypes")
			@Override
			public List getBatch(Class<?> entityClass, List<Object> ids) {
				throw new AssertionError("not to be used in his test!");
			}

			@Override
			public Object get(Class<?> entityClass, Object id) {
				return IndexUpdaterTest.this.obj(entityClass, id);
			}

			@Override
			public void open() {
				
			}

			@Override
			public void close() {
				
			}
			
		};
		List<UpdateInfo> updateInfos = this.createUpdateInfos();
		Set<UpdateInfo> updateInfoSet = new HashSet<>(updateInfos);
		IndexWrapper indexWrapper = new IndexWrapper() {

			@Override
			public void delete(Class<?> entityClass, List<Class<?>> inIndexOf,
					Object id, Transaction tx) {
				Object obj = IndexUpdaterTest.this.obj(entityClass, id);
				System.out.println(entityClass);
				System.out.println(updateInfoSet);
				System.out.println(obj);
				assertTrue(updateInfoSet.remove(new UpdateInfo(entityClass, (Integer) id, EventType.DELETE)));
			}

			@Override
			public void update(Class<?> entityClass, List<Class<?>> inIndexOf,
					Object id, Transaction tx) {
				Object obj = IndexUpdaterTest.this.obj(entityClass, id);
				System.out.println(entityClass);
				System.out.println(updateInfoSet);
				System.out.println(obj);
				assertTrue(updateInfoSet.remove(new UpdateInfo(entityClass, (Integer) id, EventType.UPDATE)));
			}

			@Override
			public void index(Class<?> entityClass, List<Class<?>> inIndexOf,
					Object id, Transaction tx) {
				Object obj = IndexUpdaterTest.this.obj(entityClass, id);
				System.out.println(entityClass);
				System.out.println(updateInfoSet);
				System.out.println(obj);
				assertTrue(updateInfoSet.remove(new UpdateInfo(entityClass, (Integer) id, EventType.INSERT)));
			}

		};
		IndexUpdater updater = new IndexUpdater(indexInformations,
				containedInIndexOf, idTypesForEntities, entityProvider,
				indexWrapper);
		updater.updateEvent(updateInfos);
		assertEquals("didn't get all the events", 0, updateInfoSet.size());
	}
	
	private Object obj(Class<?> entityClass, Object id) {
		if(entityClass.equals(Place.class)) {
			Place place = new Place();
			place.setId((Integer) id);
			return place;
		} else if(entityClass.equals(Sorcerer.class)) {
			Sorcerer sorcerer = new Sorcerer();
			sorcerer.setId((Integer) id);
			return sorcerer;
		} else {
			throw new AssertionError("shouldn't happen!");
		}
	}

	private List<UpdateInfo> createUpdateInfos() {
		List<UpdateInfo> ret = new ArrayList<>();
		ret.add(new UpdateInfo(Place.class, 1, EventType.INSERT));
		ret.add(new UpdateInfo(Place.class, 1, EventType.UPDATE));
		ret.add(new UpdateInfo(Place.class, 1, EventType.DELETE));

		ret.add(new UpdateInfo(Sorcerer.class, 2, EventType.INSERT));
		ret.add(new UpdateInfo(Sorcerer.class, 2, EventType.UPDATE));
		ret.add(new UpdateInfo(Sorcerer.class, 2, EventType.DELETE));
		return ret;
	}

}
