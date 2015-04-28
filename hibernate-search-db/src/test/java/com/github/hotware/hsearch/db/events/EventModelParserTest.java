/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package com.github.hotware.hsearch.db.events;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import org.junit.Test;

import com.github.hotware.hsearch.db.events.EventModelInfo;
import com.github.hotware.hsearch.db.events.EventModelInfo.IdInfo;
import com.github.hotware.hsearch.db.events.EventModelParser;
import com.github.hotware.hsearch.db.events.annotations.Event;
import com.github.hotware.hsearch.db.events.annotations.IdFor;
import com.github.hotware.hsearch.db.events.annotations.Updates;
import com.github.hotware.hsearch.db.test.entities.Place;
import com.github.hotware.hsearch.db.test.entities.PlaceSorcererUpdates;
import com.github.hotware.hsearch.db.test.entities.PlaceSorcererUpdatesMethod;
import com.github.hotware.hsearch.db.test.entities.Sorcerer;

/**
 * @author Martin
 */
public class EventModelParserTest {

	@Test
	public void test() throws IllegalArgumentException, IllegalAccessException {

		{
			EventModelParser parser = new EventModelParser();
			List<EventModelInfo> infos = parser.parse( new HashSet<>( Arrays.asList( PlaceSorcererUpdates.class ) ) );
			System.out.println( infos );
			PlaceSorcererUpdates placeUpdate = new PlaceSorcererUpdates();
			placeUpdate.setId( 123123 );
			placeUpdate.setPlaceId( 1 );
			placeUpdate.setSorcererId( 2 );
			placeUpdate.setEventType( EventType.INSERT );

			List<EventModelInfo.IdInfo> idInfos = infos.get( 0 ).getIdInfos();
			idInfos.sort( new Comparator<EventModelInfo.IdInfo>() {

				@Override
				public int compare(IdInfo o1, IdInfo o2) {
					return o1.getColumns()[0].compareTo( o2.getColumns()[0] );
				}

			} );

			assertEquals( PlaceSorcererUpdates.class, infos.get( 0 ).getUpdateClass() );
			assertEquals( "PlaceSorcererUpdates", infos.get( 0 ).getTableName() );
			assertEquals( "Place_Sorcerer", infos.get( 0 ).getOriginalTableName() );

			assertEquals( Place.class, idInfos.get( 0 ).getEntityClass() );
			assertEquals( "placeId", idInfos.get( 0 ).getColumns()[0] );
			assertEquals( "id", idInfos.get( 0 ).getColumnsInOriginal()[0] );

			assertEquals( Sorcerer.class, idInfos.get( 1 ).getEntityClass() );
			assertEquals( "sorcererId", idInfos.get( 1 ).getColumns()[0] );
			assertEquals( "sorc_id", idInfos.get( 1 ).getColumnsInOriginal()[0] );

			assertEquals( 1, idInfos.get( 0 ).getIdAccessor().apply( placeUpdate ) );
			assertEquals( 2, idInfos.get( 1 ).getIdAccessor().apply( placeUpdate ) );

			assertEquals( (Integer) EventType.INSERT, infos.get( 0 ).getEventTypeAccessor().apply( placeUpdate ) );
		}

		{
			EventModelParser parser = new EventModelParser();
			List<EventModelInfo> infos = parser.parse( new HashSet<>( Arrays.asList( PlaceSorcererUpdatesMethod.class ) ) );
			System.out.println( infos );
			PlaceSorcererUpdatesMethod placeUpdate = new PlaceSorcererUpdatesMethod();
			placeUpdate.setId( 123123 );
			placeUpdate.setPlaceId( 1 );
			placeUpdate.setSorcererId( 2 );
			placeUpdate.setEventType( EventType.INSERT );

			List<EventModelInfo.IdInfo> idInfos = infos.get( 0 ).getIdInfos();
			idInfos.sort( new Comparator<EventModelInfo.IdInfo>() {

				@Override
				public int compare(IdInfo o1, IdInfo o2) {
					return o1.getColumns()[0].compareTo( o2.getColumns()[0] );
				}

			} );

			assertEquals( PlaceSorcererUpdatesMethod.class, infos.get( 0 ).getUpdateClass() );
			assertEquals( "PlaceSorcererUpdates", infos.get( 0 ).getTableName() );
			assertEquals( "Place_Sorcerer", infos.get( 0 ).getOriginalTableName() );

			assertEquals( Place.class, idInfos.get( 0 ).getEntityClass() );
			assertEquals( "placeId", idInfos.get( 0 ).getColumns()[0] );
			assertEquals( "id", idInfos.get( 0 ).getColumnsInOriginal()[0] );

			assertEquals( Sorcerer.class, idInfos.get( 1 ).getEntityClass() );
			assertEquals( "sorcererId", idInfos.get( 1 ).getColumns()[0] );
			assertEquals( "sorc_id", idInfos.get( 1 ).getColumnsInOriginal()[0] );

			assertEquals( 1, idInfos.get( 0 ).getIdAccessor().apply( placeUpdate ) );
			assertEquals( 2, idInfos.get( 1 ).getIdAccessor().apply( placeUpdate ) );

			assertEquals( (Integer) EventType.INSERT, infos.get( 0 ).getEventTypeAccessor().apply( placeUpdate ) );
		}

		{
			EventModelParser parser = new EventModelParser();
			try {
				parser.parse( new HashSet<>( Arrays.asList( Mixed.class ) ) );
				fail( "Exception expected" );
			}
			catch (IllegalArgumentException e) {

			}
		}

		{
			EventModelParser parser = new EventModelParser();
			try {
				parser.parse( new HashSet<>( Arrays.asList( Mixed2.class ) ) );
				fail( "Exception expected" );
			}
			catch (IllegalArgumentException e) {

			}
		}

		{
			EventModelParser parser = new EventModelParser();
			try {
				parser.parse( new HashSet<>( Arrays.asList( ForgotUpdates.class ) ) );
				fail( "Exception expected" );
			}
			catch (IllegalArgumentException e) {

			}
		}

		{
			EventModelParser parser = new EventModelParser();
			try {
				parser.parse( new HashSet<>( Arrays.asList( ForgotEvent.class ) ) );
				fail( "Exception expected" );
			}
			catch (IllegalArgumentException e) {

			}
		}

		{
			EventModelParser parser = new EventModelParser();
			try {
				parser.parse( new HashSet<>( Arrays.asList( ForgotIds.class ) ) );
				fail( "Exception expected" );
			}
			catch (IllegalArgumentException e) {

			}
		}
	}

	@Updates(originalTableName = "orig", tableName = "tbl")
	private static class Mixed {

		@Event(column = "eventType")
		private Integer eventType;

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

		private Integer eventType;

		@IdFor(entityClass = Place.class, columns = "tblId1", columnsInOriginal = "origId1")
		private Integer id1;

		@IdFor(entityClass = Sorcerer.class, columns = "tblId2", columnsInOriginal = "origId2")
		private Integer id2;

		@Event(column = "eventType")
		public Integer getEventType() {
			return this.eventType;
		}

	}

	private static class ForgotUpdates {

		@Event(column = "eventType")
		private Integer eventType;

		@IdFor(entityClass = Place.class, columns = "tblId1", columnsInOriginal = "origId1")
		private Integer id1;

		@IdFor(entityClass = Sorcerer.class, columns = "tblId2", columnsInOriginal = "origId2")
		private Integer id2;

	}

	@Updates(originalTableName = "orig", tableName = "tbl")
	private static class ForgotEvent {

		@SuppressWarnings("unused")
		private Integer eventType;

		@IdFor(entityClass = Place.class, columns = "tblId1", columnsInOriginal = "origId1")
		private Integer id1;

		@IdFor(entityClass = Sorcerer.class, columns = "tblId2", columnsInOriginal = "origId2")
		private Integer id2;

	}

	@Updates(originalTableName = "orig", tableName = "tbl")
	private static class ForgotIds {

		@Event(column = "eventType")
		private Integer eventType;

		@SuppressWarnings("unused")
		private Integer id1;

		@SuppressWarnings("unused")
		private Integer id2;

	}

}
