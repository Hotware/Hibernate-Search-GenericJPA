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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

			@Override
			public List getBatch(Class<?> arg0, List<Object> arg1) {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public Object get(Class<?> arg0, Object arg1) {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public void open() {
				// TODO Auto-generated method stub

			}

			@Override
			public void close() {
				// TODO Auto-generated method stub

			}
		};
		IndexWrapper indexWrapper = new IndexWrapper() {

			@Override
			public void delete(Class<?> entityClass, List<Class<?>> inIndexOf,
					Object id, Transaction tx) {
				// TODO Auto-generated method stub

			}

			@Override
			public void update(Class<?> entityClass, List<Class<?>> inIndexOf,
					Object id, Transaction tx) {
				// TODO Auto-generated method stub

			}

			@Override
			public void index(Class<?> entityClass, List<Class<?>> inIndexOf,
					Object id, Transaction tx) {
				// TODO Auto-generated method stub

			}

		};
		IndexUpdater updater = new IndexUpdater(indexInformations,
				containedInIndexOf, idTypesForEntities, entityProvider,
				indexWrapper);
		updater.updateEvent(this.createUpdateInfos());
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
