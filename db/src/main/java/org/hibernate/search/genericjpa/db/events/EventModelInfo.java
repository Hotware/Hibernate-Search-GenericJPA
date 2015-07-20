/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.db.events;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.hibernate.search.genericjpa.db.id.IdConverter;
import org.hibernate.search.genericjpa.db.id.ToOriginalIdBridge;

/**
 * contains information about the EventModel. Instances of this class can be obtained by a {@link EventModelParser}.
 *
 * @author Martin
 */
public class EventModelInfo {

	private final String updateTableName;
	private final String originalTableName;
	private final String eventTypeColumn;
	private final List<IdInfo> idInfos;
	private final String updateIdColumn;

	private final Class<?> updateClass;
	private final Function<Object, Integer> eventTypeAccessor;

	public EventModelInfo(
			Class<?> updateClass,
			String tableName,
			String originalTableName,
			Function<Object, Integer> eventTypeAccessor,
			String eventTypeColumn,
			List<IdInfo> idInfos) {
		super();
		this.updateClass = updateClass;
		this.updateTableName = tableName;
		this.originalTableName = originalTableName;
		this.eventTypeAccessor = eventTypeAccessor;
		this.eventTypeColumn = eventTypeColumn;
		this.idInfos = idInfos;

		this.updateIdColumn = null;
	}


	public EventModelInfo(
			String tableName,
			String originalTableName,
			String eventTypeColumn, String updateIdColumn,
			List<IdInfo> idInfos) {
		this.updateTableName = tableName;
		this.originalTableName = originalTableName;
		this.eventTypeColumn = eventTypeColumn;
		this.idInfos = idInfos;
		this.updateIdColumn = updateIdColumn;

		this.updateClass = null;
		this.eventTypeAccessor = null;
	}

	public String getUpdateIdColumn() {
		return updateIdColumn;
	}

	/**
	 * @return the updateClass
	 */
	public Class<?> getUpdateClass() {
		return updateClass;
	}

	/**
	 * @return the updateTableName
	 */
	public String getUpdateTableName() {
		return updateTableName;
	}

	/**
	 * @return the originalTableName
	 */
	public String getOriginalTableName() {
		return originalTableName;
	}

	/**
	 * @return the eventTypeAccessor
	 */
	public Function<Object, Integer> getEventTypeAccessor() {
		return eventTypeAccessor;
	}

	/**
	 * @return the idInfos
	 */
	public List<IdInfo> getIdInfos() {
		return idInfos;
	}

	/**
	 * @return the eventTypeColumn
	 */
	public String getEventTypeColumn() {
		return eventTypeColumn;
	}

	@Override
	public String toString() {
		return "EventModelInfo{" +
				"updateTableName='" + updateTableName + '\'' +
				", originalTableName='" + originalTableName + '\'' +
				", eventTypeColumn='" + eventTypeColumn + '\'' +
				", idInfos=" + idInfos +
				", updateIdColumn='" + updateIdColumn + '\'' +
				", updateClass=" + updateClass +
				", eventTypeAccessor=" + eventTypeAccessor +
				'}';
	}

	public static class IdInfo {

		private final Class<?> entityClass;
		private final String[] columnsInUpdateTable;
		private final String[] columnsInOriginal;
		private final IdConverter idConverter;
		private final Map<String, String> hints;

		private final Function<Object, Object> idAccessor;
		private final ToOriginalIdBridge toOriginalBridge;

		public IdInfo(
				Function<Object, Object> idAccessor, Class<?> entityClass, String[] columns, String[] columnsInOriginal,
				ToOriginalIdBridge toOriginalBridge, Map<String, String> hints) {
			super();
			this.idAccessor = idAccessor;
			this.entityClass = entityClass;
			this.columnsInUpdateTable = columns;
			this.columnsInOriginal = columnsInOriginal;
			this.toOriginalBridge = toOriginalBridge;
			this.hints = hints;

			this.idConverter = null;
		}

		public IdInfo(
				Class<?> entityClass,
				String[] columnsInUpdateTable,
				String[] columnsInOriginal,
				IdConverter idConverter, Map<String, String> hints) {
			this.entityClass = entityClass;
			this.columnsInUpdateTable = columnsInUpdateTable;
			this.idConverter = idConverter;
			this.hints = hints;
			this.columnsInOriginal = columnsInOriginal;

			this.idAccessor = null;
			this.toOriginalBridge = null;
		}

		public IdConverter getIdConverter() {
			return idConverter;
		}

		/**
		 * id accessor that applies all the logic to get an id for the updated entity from the Updates class
		 *
		 * @return the idAccessor
		 */
		public Function<Object, Object> getIdAccessor() {
			return idAccessor;
		}

		/**
		 * @return the entityClass
		 */
		public Class<?> getEntityClass() {
			return entityClass;
		}

		/**
		 * @return the columnsInUpdateTable
		 */
		public String[] getColumnsInUpdateTable() {
			return columnsInUpdateTable;
		}

		/**
		 * @return the columnsInOriginal
		 */
		public String[] getColumnsInOriginal() {
			return columnsInOriginal;
		}

		/**
		 * <b>internal method used for debugging</b>
		 *
		 * @return the toOriginalBridge
		 */
		public ToOriginalIdBridge getToOriginalBridge() {
			return toOriginalBridge;
		}

		public Map<String, String> getHints() {
			return hints;
		}

		@Override
		public String toString() {
			return "IdInfo{" +
					"entityClass=" + entityClass +
					", columnsInUpdateTable=" + Arrays.toString( columnsInUpdateTable ) +
					", columnsInOriginal=" + Arrays.toString( columnsInOriginal ) +
					", idConverter=" + idConverter +
					", hints=" + hints +
					", idAccessor=" + idAccessor +
					", toOriginalBridge=" + toOriginalBridge +
					'}';
		}
	}

}
