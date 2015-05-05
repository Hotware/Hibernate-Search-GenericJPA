/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.db.tableInfo;

import java.util.List;
import java.util.Map;

/**
 * @author Martin Braun
 */
public class TableInfo {

	private final List<IdInfo> updateEventRelevantIdInfos;
	private final List<String> tableNames;

	public TableInfo(List<IdInfo> updateEventRelevantIdInfos, List<String> tableNames) {
		this.updateEventRelevantIdInfos = updateEventRelevantIdInfos;
		this.tableNames = tableNames;
	}

	/**
	 * @return the updatedEntityTypes
	 */
	public List<IdInfo> getUpdateEventRelevantIdInfos() {
		return this.updateEventRelevantIdInfos;
	}

	/**
	 * @return the tableName
	 */
	public List<String> getTableNames() {
		return this.tableNames;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append( "TableInfo [updateEventRelevantIdInfos=" ).append( updateEventRelevantIdInfos ).append( ", tableNames=" ).append( tableNames )
				.append( "]" );
		return builder.toString();
	}

	public static class IdInfo {

		// FIXME: idType!!
		private final Class<?> entityClass;
		private final List<String> idColumns;
		private final Map<String, Class<?>> idColumnTypes;

		public IdInfo(Class<?> entityClass, List<String> idColumns, Map<String, Class<?>> idColumnTypes) {
			this.entityClass = entityClass;
			this.idColumns = idColumns;
			this.idColumnTypes = idColumnTypes;
		}

		/**
		 * @return the entityClass
		 */
		public Class<?> getEntityClass() {
			return this.entityClass;
		}

		/**
		 * @return the idColumns
		 */
		public List<String> getIdColumns() {
			return this.idColumns;
		}

		/**
		 * @return the idColumnTypes
		 */
		public Map<String, Class<?>> getIdColumnTypes() {
			return idColumnTypes;
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append( "IdInfo [entityClass=" ).append( entityClass ).append( ", idColumns=" ).append( idColumns ).append( ", idColumnTypes=" )
					.append( idColumnTypes ).append( "]" );
			return builder.toString();
		}

	}

}
