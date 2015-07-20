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
import org.hibernate.search.genericjpa.test.db.entities.PlaceSorcererUpdates;

import org.junit.Test;

/**
 * Created by Martin on 20.07.2015.
 */
public class AnnotationEventModelParserTest {

	@Test
	public void test() {
		EventModelParser parser = new AnnotationEventModelParser();
		List<EventModelInfo> infos = parser.parse( new HashSet<>( Arrays.asList( SomeEntity.class ) ) );
		Collections.sort(
				infos,
				(first, second) -> first.getOriginalTableName().compareTo( second.getOriginalTableName() )
		);
		
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
				@IdInfo(entity = SomeOtherEntity.class, columns = "column3_2", type = IdInfo.IdType.INTEGER)
		})
		private Set<SomeOtherEntity> someOtherEntity;

		@UpdateInfo(tableName = "table4", idInfos = {
				@IdInfo(entity = SomeEntity.class, columns = "column4_1", type = IdInfo.IdType.INTEGER),
				@IdInfo(entity = SomeOtherEntity.class, columns = "column4_2", type = IdInfo.IdType.INTEGER)
		})
		public Set<YetAnotherEntity> getYet() {
			return null;
		}

	}

	public static class SomeOtherEntity {

	}

	public static class YetAnotherEntity {

	}

}
