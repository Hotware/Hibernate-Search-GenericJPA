/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.test.db.events;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.search.genericjpa.annotations.Hint;
import org.hibernate.search.genericjpa.annotations.IdInfo;
import org.hibernate.search.genericjpa.annotations.UpdateInfo;
import org.hibernate.search.genericjpa.db.events.AnnotationEventModelParser;
import org.hibernate.search.genericjpa.db.events.EventModelInfo;
import org.hibernate.search.genericjpa.db.events.EventModelParser;
import org.hibernate.search.genericjpa.db.id.IdConverter;
import org.hibernate.search.genericjpa.exception.SearchException;
import org.hibernate.search.genericjpa.test.db.entities.PlaceSorcererUpdates;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by Martin on 20.07.2015.
 */
public class AnnotationEventModelParserTest {

	EventModelParser parser = new AnnotationEventModelParser();

	@Test
	public void testCorrectUsage() {
		List<EventModelInfo> infos = parser.parse( new HashSet<>( Arrays.asList( SomeEntity.class ) ) );
		Collections.sort(
				infos,
				(first, second) -> first.getOriginalTableName().compareTo( second.getOriginalTableName() )
		);

		{
			EventModelInfo table1Info = infos.get( 0 );
			assertEquals( "table1", table1Info.getOriginalTableName() );
			assertNotEquals( "", table1Info.getUpdateTableName() );
			assertNotEquals( "", table1Info.getEventTypeColumn() );
			assertNotEquals( "", table1Info.getUpdateIdColumn() );

			List<EventModelInfo.IdInfo> idInfos = table1Info.getIdInfos();
			Collections.sort(
					idInfos,
					(first, second) -> first.getColumnsInOriginal()[0].compareTo( second.getColumnsInOriginal()[0] )
			);
			Class<?>[] classes = {SomeEntity.class, SomeOtherEntity.class, YetAnotherEntity.class};
			IdInfo.IdType[] idTypes = {IdInfo.IdType.INTEGER, IdInfo.IdType.LONG, IdInfo.IdType.STRING};
			for ( int i = 0; i < idInfos.size(); ++i ) {
				EventModelInfo.IdInfo cur = idInfos.get( i );
				assertEquals( "column1_" + (i + 1), cur.getColumnsInOriginal()[0] );
				assertTrue( cur.getHints().containsKey( "key" + (i + 1) ) );
				assertEquals( "value" + (i + 1), cur.getHints().get( "key" + (i + 1) ) );
				assertEquals( idTypes[i], cur.getIdConverter() );
				assertEquals( classes[i], cur.getEntityClass() );
			}
		}

		//the other stuff is just parsed straight-forward. just make sure, we find all the info.
		assertEquals( 4, infos.size() );
	}

	@Test(expected = SearchException.class)
	public void testSameTableTwice() {
		List<EventModelInfo> infos = parser.parse( new HashSet<>( Arrays.asList( SameTableTwice.class ) ) );
		System.err.println( infos );
	}

	@Test(expected = SearchException.class)
	public void testNamingConflictSameAnnotation() {
		List<EventModelInfo> infos = parser.parse( new HashSet<>( Arrays.asList( NamingConflictSameAnnotation.class ) ) );
		System.err.println( infos );
	}

	@Test(expected = SearchException.class)
	public void testNamingConflictTwoAnnotations() {
		List<EventModelInfo> infos = parser.parse( new HashSet<>( Arrays.asList( NamingConflictTwoAnnotations.class ) ) );
		System.err.println( infos );
	}

	@Test(expected = SearchException.class)
	public void testIdInfoAndConverterConflict() {
		List<EventModelInfo> infos = parser.parse( new HashSet<>( Arrays.asList( IdInfoAndConverter.class ) ) );
		System.err.println( infos );
	}

	@Test
	public void testManualValues() {
		List<EventModelInfo> infos = parser.parse( new HashSet<>( Arrays.asList( ManualValues.class ) ) );
		//FIXME: implement the rest here
	}

	//this information doesn't make a whole lot of sense database wise, but we can test stuff properly still
	@UpdateInfo(tableName = "table1", idInfos = {
			@IdInfo(columns = "column1_1", hints = {@Hint(key = "key1", value = "value1")}, type = IdInfo.IdType.INTEGER),
			@IdInfo(entity = SomeOtherEntity.class, columns = "column1_2", hints = {@Hint(key = "key2", value = "value2")}, type = IdInfo.IdType.LONG),
			@IdInfo(entity = YetAnotherEntity.class, columns = "column1_3", hints = {@Hint(key = "key3", value = "value3")}, type = IdInfo.IdType.STRING)
	})
	@UpdateInfo(tableName = "table2", idInfos = @IdInfo(columns = "column2_1", type = IdInfo.IdType.INTEGER))
	public static class SomeEntity {

		@UpdateInfo(tableName = "table3", idInfos = {
				@IdInfo(entity = SomeEntity.class, columns = "column3_1", type = IdInfo.IdType.INTEGER),
				@IdInfo(entity = SomeOtherEntity.class, columns = "column3_2", type = IdInfo.IdType.LONG)
		})
		private Set<SomeOtherEntity> someOtherEntity;

		@UpdateInfo(tableName = "table4", idInfos = {
				@IdInfo(entity = SomeEntity.class, columns = "column4_1", type = IdInfo.IdType.INTEGER),
				@IdInfo(entity = SomeOtherEntity.class, columns = "column4_2", type = IdInfo.IdType.LONG)
		})
		public Set<YetAnotherEntity> getYet() {
			return null;
		}

	}

	public static class SomeOtherEntity {

	}

	public static class YetAnotherEntity {

	}

	@UpdateInfo(tableName = "table_toast", idInfos = @IdInfo(columns = "toast", type = IdInfo.IdType.INTEGER))
	@UpdateInfo(tableName = "table_toast", idInfos = @IdInfo(columns = "toast2", type = IdInfo.IdType.INTEGER))
	public static class SameTableTwice {

	}

	@UpdateInfo(tableName = "namingconflict", updateTableName = "namingconflict", idInfos = @IdInfo(columns = "toast", type = IdInfo.IdType.INTEGER))
	public static class NamingConflictSameAnnotation {

	}

	@UpdateInfo(tableName = "namingconflict", idInfos = @IdInfo(columns = "toast", type = IdInfo.IdType.INTEGER))
	@UpdateInfo(tableName = "no_conflict", updateTableName = "namingconflict", idInfos = @IdInfo(columns = "toast", type = IdInfo.IdType.INTEGER))
	public static class NamingConflictTwoAnnotations {

	}

	@UpdateInfo(tableName = "toast123", updateTableName = "toast12345", idInfos = @IdInfo(columns = "toast", type = IdInfo.IdType.INTEGER, idConverter = ManualIdConverter.class))
	public static class IdInfoAndConverter {

	}

	@UpdateInfo(tableName = "manualvalues", updateTableName = "manualvalues_updates", updateTableIdColumn = "manualUpdateIdColumn", updateTableEventCaseColumn = "manualEventCaseColumn", idInfos = @IdInfo(
			entity = Manual.class, columns = "manualcolumn", updateTableColumns = "manualcolumn_FOREIGN", idConverter = ManualIdConverter.class
	))
	public static class ManualValues {

	}

	public static class Manual {

	}

	public static class ManualIdConverter implements IdConverter {

		@Override
		public Object convert(Object[] values, String[] fieldNames) {
			return null;
		}

	}

}
