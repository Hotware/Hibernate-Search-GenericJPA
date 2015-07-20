/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.test.db.events;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.hibernate.search.genericjpa.annotations.Event;
import org.hibernate.search.genericjpa.annotations.IdFor;
import org.hibernate.search.genericjpa.annotations.Updates;
import org.hibernate.search.genericjpa.db.events.UpdateClassAnnotationEventModelParser;
import org.hibernate.search.genericjpa.db.events.EventModelInfo;
import org.hibernate.search.genericjpa.db.events.EventModelParser;
import org.hibernate.search.genericjpa.db.events.EventType;
import org.hibernate.search.genericjpa.db.id.ToOriginalIdBridge;
import org.hibernate.search.genericjpa.test.db.entities.Place;
import org.hibernate.search.genericjpa.test.db.entities.PlaceSorcererUpdates;
import org.hibernate.search.genericjpa.test.db.entities.PlaceSorcererUpdatesMethod;
import org.hibernate.search.genericjpa.test.db.entities.Sorcerer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Martin
 */
public class EventModelParserTest {

	@Test
	public void test() throws IllegalAccessException {

		{
			EventModelParser parser = new UpdateClassAnnotationEventModelParser();
			List<EventModelInfo> infos = parser.parse( new HashSet<>( Arrays.asList( PlaceSorcererUpdates.class ) ) );
			System.out.println( infos );
			PlaceSorcererUpdates placeUpdate = new PlaceSorcererUpdates();
			placeUpdate.setId( 123123 );
			placeUpdate.setPlaceId( 1 );
			placeUpdate.setSorcererId( 2 );
			placeUpdate.setEventType( EventType.INSERT );

			List<EventModelInfo.IdInfo> idInfos = infos.get( 0 ).getIdInfos();
			idInfos.sort(
					(o1, o2) -> o1.getColumnsInUpdateTable()[0].compareTo( o2.getColumnsInUpdateTable()[0] )
			);

			assertEquals( PlaceSorcererUpdates.class, infos.get( 0 ).getUpdateClass() );
			assertEquals( "PlaceSorcererUpdates", infos.get( 0 ).getUpdateTableName() );
			assertEquals( "Place_Sorcerer", infos.get( 0 ).getOriginalTableName() );

			assertEquals( Place.class, idInfos.get( 0 ).getEntityClass() );
			assertEquals( "placeId", idInfos.get( 0 ).getColumnsInUpdateTable()[0] );
			assertEquals( "id", idInfos.get( 0 ).getColumnsInOriginal()[0] );

			assertEquals( Sorcerer.class, idInfos.get( 1 ).getEntityClass() );
			assertEquals( "sorcererId", idInfos.get( 1 ).getColumnsInUpdateTable()[0] );
			assertEquals( "sorc_id", idInfos.get( 1 ).getColumnsInOriginal()[0] );

			assertEquals( 1, idInfos.get( 0 ).getIdAccessor().apply( placeUpdate ) );
			assertEquals( 2, idInfos.get( 1 ).getIdAccessor().apply( placeUpdate ) );

			assertEquals( (Integer) EventType.INSERT, infos.get( 0 ).getEventTypeAccessor().apply( placeUpdate ) );
		}

		{
			EventModelParser parser = new UpdateClassAnnotationEventModelParser();
			List<EventModelInfo> infos = parser.parse( new HashSet<>( Arrays.asList( PlaceSorcererUpdatesMethod.class ) ) );
			System.out.println( infos );
			PlaceSorcererUpdatesMethod placeUpdate = new PlaceSorcererUpdatesMethod();
			placeUpdate.setId( 123123 );
			placeUpdate.setPlaceId( 1 );
			placeUpdate.setSorcererId( 2 );
			placeUpdate.setEventType( EventType.INSERT );

			List<EventModelInfo.IdInfo> idInfos = infos.get( 0 ).getIdInfos();
			idInfos.sort(
					(o1, o2) -> o1.getColumnsInUpdateTable()[0].compareTo( o2.getColumnsInUpdateTable()[0] )
			);

			assertEquals( PlaceSorcererUpdatesMethod.class, infos.get( 0 ).getUpdateClass() );
			assertEquals( "PlaceSorcererUpdates", infos.get( 0 ).getUpdateTableName() );
			assertEquals( "Place_Sorcerer", infos.get( 0 ).getOriginalTableName() );

			assertEquals( Place.class, idInfos.get( 0 ).getEntityClass() );
			assertEquals( "placeId", idInfos.get( 0 ).getColumnsInUpdateTable()[0] );
			assertEquals( "id", idInfos.get( 0 ).getColumnsInOriginal()[0] );

			assertEquals( Sorcerer.class, idInfos.get( 1 ).getEntityClass() );
			assertEquals( "sorcererId", idInfos.get( 1 ).getColumnsInUpdateTable()[0] );
			assertEquals( "sorc_id", idInfos.get( 1 ).getColumnsInOriginal()[0] );

			assertEquals( 1, idInfos.get( 0 ).getIdAccessor().apply( placeUpdate ) );
			assertEquals( 2, idInfos.get( 1 ).getIdAccessor().apply( placeUpdate ) );

			assertEquals( (Integer) EventType.INSERT, infos.get( 0 ).getEventTypeAccessor().apply( placeUpdate ) );
		}

		{
			EventModelParser parser = new UpdateClassAnnotationEventModelParser();
			try {
				parser.parse( new HashSet<>( Arrays.asList( Mixed.class ) ) );
				fail( "Exception expected" );
			}
			catch (IllegalArgumentException e) {

			}
		}

		{
			EventModelParser parser = new UpdateClassAnnotationEventModelParser();
			try {
				parser.parse( new HashSet<>( Arrays.asList( Mixed2.class ) ) );
				fail( "Exception expected" );
			}
			catch (IllegalArgumentException e) {

			}
		}

		{
			EventModelParser parser = new UpdateClassAnnotationEventModelParser();
			try {
				parser.parse( new HashSet<>( Arrays.asList( ForgotUpdates.class ) ) );
				fail( "Exception expected" );
			}
			catch (IllegalArgumentException e) {

			}
		}

		{
			EventModelParser parser = new UpdateClassAnnotationEventModelParser();
			try {
				parser.parse( new HashSet<>( Arrays.asList( ForgotEvent.class ) ) );
				fail( "Exception expected" );
			}
			catch (IllegalArgumentException e) {

			}
		}

		{
			EventModelParser parser = new UpdateClassAnnotationEventModelParser();
			try {
				parser.parse( new HashSet<>( Arrays.asList( ForgotIds.class ) ) );
				fail( "Exception expected" );
			}
			catch (IllegalArgumentException e) {

			}
		}
	}

	@Test
	public void testMultiColumnsIdUpdates() {
		EventModelParser parser = new UpdateClassAnnotationEventModelParser();

		MultipleColumnsIdUpdates update = new MultipleColumnsIdUpdates();
		update.eventType = EventType.INSERT;
		update.multipleColumnsId = new String[] {"one", "two"};
		EventModelInfo evi = parser.parse( new HashSet<>( Arrays.asList( MultipleColumnsIdUpdates.class ) ) ).get( 0 );
		assertEquals( EventType.INSERT, (int) evi.getEventTypeAccessor().apply( update ) );
		ToOriginalIdBridge bridge = evi.getIdInfos().get( 0 ).getToOriginalBridge();
		//we actually apply the bridge here already
		assertEquals( "onetwo", evi.getIdInfos().get( 0 ).getIdAccessor().apply( update ) );
		//but we can test whether this is right as well
		assertEquals(
				"onetwo", bridge.toOriginal(
						update.multipleColumnsId
				)
		);
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

	public static class MultipleColumnsId {

	}

	public static class StringArrayConvertingBridge implements ToOriginalIdBridge {

		@Override
		public Object toOriginal(Object object) {
			String[] arr = (String[]) object;
			StringBuilder builder = new StringBuilder();
			for ( String str : arr ) {
				builder.append( str );
			}
			return builder.toString();
		}
	}

	@Updates(tableName = "MultipleColumnsIdUpdates", originalTableName = "originalTable")
	public static class MultipleColumnsIdUpdates {

		@Event(column = "eventType")
		private Integer eventType;

		@IdFor(entityClass = MultipleColumnsId.class, columns = {
				"columnsUpdates1",
				"columnsUpdates2"
		}, columnsInOriginal = {"columnsOriginal1", "columnsOriginal2"}, bridge = StringArrayConvertingBridge.class)
		private String[] multipleColumnsId;

	}
}
