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
package com.github.hotware.hsearch.db.events;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.junit.Test;

import com.github.hotware.hsearch.db.events.EventModelInfo;
import com.github.hotware.hsearch.db.events.EventModelInfo.IdInfo;
import com.github.hotware.hsearch.db.events.EventModelParser;
import com.github.hotware.hsearch.db.events.annotations.Case;
import com.github.hotware.hsearch.db.events.annotations.IdFor;
import com.github.hotware.hsearch.db.events.annotations.Updates;
import com.github.hotware.hsearch.db.test.entities.Place;
import com.github.hotware.hsearch.db.test.entities.PlaceUpdates;
import com.github.hotware.hsearch.db.test.entities.PlaceUpdatesMethod;
import com.github.hotware.hsearch.db.test.entities.Sorcerer;

/**
 * @author Martin
 *
 */
public class EventModelParserTest {

	@Test
	public void test() throws IllegalArgumentException, IllegalAccessException {

		{
			EventModelParser parser = new EventModelParser();
			List<EventModelInfo> infos = parser.parse(Arrays
					.asList(PlaceUpdates.class));
			System.out.println(infos);
			PlaceUpdates placeUpdate = new PlaceUpdates();
			placeUpdate.setId(123123);
			placeUpdate.setPlaceId(1);
			placeUpdate.setSorcererId(2);
			placeUpdate.setEventCase(EventCase.INSERT);

			List<EventModelInfo.IdInfo> idInfos = infos.get(0).getIdInfos();
			idInfos.sort(new Comparator<EventModelInfo.IdInfo>() {

				@Override
				public int compare(IdInfo o1, IdInfo o2) {
					return o1.getColumns()[0].compareTo(o2.getColumns()[0]);
				}

			});

			assertEquals(Place.class, idInfos.get(0).getEntityClass());
			assertEquals("placeId", idInfos.get(0).getColumns()[0]);
			assertEquals("id", idInfos.get(0).getColumnsInOriginal()[0]);

			assertEquals(Sorcerer.class, idInfos.get(1).getEntityClass());
			assertEquals("sorcererId", idInfos.get(1).getColumns()[0]);
			assertEquals("sorc_id", idInfos.get(1).getColumnsInOriginal()[0]);

			assertEquals(1, idInfos.get(0).getIdAccessor().apply(placeUpdate));
			assertEquals(2, idInfos.get(1).getIdAccessor().apply(placeUpdate));
			assertEquals((Integer) EventCase.INSERT, infos.get(0)
					.getCaseAccessor().apply(placeUpdate));
		}

		{
			EventModelParser parser = new EventModelParser();
			List<EventModelInfo> infos = parser.parse(Arrays
					.asList(PlaceUpdatesMethod.class));
			System.out.println(infos);
			PlaceUpdatesMethod placeUpdate = new PlaceUpdatesMethod();
			placeUpdate.setId(123123);
			placeUpdate.setPlaceId(1);
			placeUpdate.setSorcererId(2);
			placeUpdate.setEventCase(EventCase.INSERT);

			List<EventModelInfo.IdInfo> idInfos = infos.get(0).getIdInfos();
			idInfos.sort(new Comparator<EventModelInfo.IdInfo>() {

				@Override
				public int compare(IdInfo o1, IdInfo o2) {
					return o1.getColumns()[0].compareTo(o2.getColumns()[0]);
				}

			});

			assertEquals(Place.class, idInfos.get(0).getEntityClass());
			assertEquals("placeId", idInfos.get(0).getColumns()[0]);
			assertEquals("id", idInfos.get(0).getColumnsInOriginal()[0]);

			assertEquals(Sorcerer.class, idInfos.get(1).getEntityClass());
			assertEquals("sorcererId", idInfos.get(1).getColumns()[0]);
			assertEquals("sorc_id", idInfos.get(1).getColumnsInOriginal()[0]);

			assertEquals(1, idInfos.get(0).getIdAccessor().apply(placeUpdate));
			assertEquals(2, idInfos.get(1).getIdAccessor().apply(placeUpdate));
			assertEquals((Integer) EventCase.INSERT, infos.get(0)
					.getCaseAccessor().apply(placeUpdate));
		}

		{
			EventModelParser parser = new EventModelParser();
			try {
				parser.parse(Arrays.asList(Mixed.class));
				fail("Exception expected");
			} catch (IllegalArgumentException e) {

			}
		}

		{
			EventModelParser parser = new EventModelParser();
			try {
				parser.parse(Arrays.asList(Mixed2.class));
				fail("Exception expected");
			} catch (IllegalArgumentException e) {

			}
		}
		
		{
			EventModelParser parser = new EventModelParser();
			try {
				parser.parse(Arrays.asList(ForgotUpdates.class));
				fail("Exception expected");
			} catch (IllegalArgumentException e) {

			}
		}
		
		{
			EventModelParser parser = new EventModelParser();
			try {
				parser.parse(Arrays.asList(ForgotCase.class));
				fail("Exception expected");
			} catch (IllegalArgumentException e) {

			}
		}
		
		{
			EventModelParser parser = new EventModelParser();
			try {
				parser.parse(Arrays.asList(ForgotIds.class));
				fail("Exception expected");
			} catch (IllegalArgumentException e) {

			}
		}
	}

	@Updates(originalTableName = "orig", tableName = "tbl")
	private static class Mixed {

		@Case
		private Integer eventCase;

		@IdFor(entityClass = Place.class, columns = "tblId1", columnsInOriginal = "origId1")
		private Integer id1;

		private Integer id2;

		@IdFor(entityClass = Sorcerer.class, columns = "tblId2", columnsInOriginal = "origId2")
		public Integer getId2() {
			return id2;
		}

	}

	@Updates(originalTableName = "orig", tableName = "tbl")
	private static class Mixed2 {

		private Integer eventCase;

		@IdFor(entityClass = Place.class, columns = "tblId1", columnsInOriginal = "origId1")
		private Integer id1;

		@IdFor(entityClass = Sorcerer.class, columns = "tblId2", columnsInOriginal = "origId2")
		private Integer id2;

		@Case
		public Integer getEventCase() {
			return this.eventCase;
		}

	}
	
	private static class ForgotUpdates {

		@Case
		private Integer eventCase;

		@IdFor(entityClass = Place.class, columns = "tblId1", columnsInOriginal = "origId1")
		private Integer id1;

		@IdFor(entityClass = Sorcerer.class, columns = "tblId2", columnsInOriginal = "origId2")
		private Integer id2;

	}
	
	@Updates(originalTableName = "orig", tableName = "tbl")
	private static class ForgotCase {

		@SuppressWarnings("unused")
		private Integer eventCase;

		@IdFor(entityClass = Place.class, columns = "tblId1", columnsInOriginal = "origId1")
		private Integer id1;

		@IdFor(entityClass = Sorcerer.class, columns = "tblId2", columnsInOriginal = "origId2")
		private Integer id2;

	}
	
	@Updates(originalTableName = "orig", tableName = "tbl")
	private static class ForgotIds {

		@Case
		private Integer eventCase;

		@SuppressWarnings("unused")
		private Integer id1;

		@SuppressWarnings("unused")
		private Integer id2;

	}

}
