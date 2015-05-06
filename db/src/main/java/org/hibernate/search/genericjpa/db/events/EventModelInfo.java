/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.db.events;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import org.hibernate.search.genericjpa.db.id.ToOriginalIdBridge;

/**
 * contains information about the EventModel. Instances of this class can be obtained by a {@link EventModelParser}.
 *
 * @author Martin
 */
public class EventModelInfo {

	private final Class<?> updateClass;
	private final String tableName;
	private final String originalTableName;
	private final Function<Object, Integer> eventTypeAccessor;
	private final String eventTypeColumn;
	private final List<IdInfo> idInfos;

	public EventModelInfo(Class<?> updateClass, String tableName, String originalTableName, Function<Object, Integer> eventTypeAccessor,
			String eventTypeColumn, List<IdInfo> idInfos) {
		super();
		this.updateClass = updateClass;
		this.tableName = tableName;
		this.originalTableName = originalTableName;
		this.eventTypeAccessor = eventTypeAccessor;
		this.eventTypeColumn = eventTypeColumn;
		this.idInfos = idInfos;
	}

	/**
	 * @return the updateClass
	 */
	public Class<?> getUpdateClass() {
		return updateClass;
	}

	/**
	 * @return the tableName
	 */
	public String getTableName() {
		return tableName;
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

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "EventModelInfo [updateClass=" + updateClass + ", tableName=" + tableName + ", originalTableName=" + originalTableName + ", eventTypeAccessor="
				+ eventTypeAccessor + ", eventTypeColumn=" + eventTypeColumn + ", idInfos=" + idInfos + "]";
	}

	public static class IdInfo {

		private final Function<Object, Object> idAccessor;
		private final Class<?> entityClass;
		private final String[] columns;
		private final String[] columnsInOriginal;
		private final ToOriginalIdBridge toOriginalBridge;

		public IdInfo(Function<Object, Object> idAccessor, Class<?> entityClass, String[] columns, String[] columnsInOriginal,
				ToOriginalIdBridge toOriginalBridge) {
			super();
			this.idAccessor = idAccessor;
			this.entityClass = entityClass;
			this.columns = columns;
			this.columnsInOriginal = columnsInOriginal;
			this.toOriginalBridge = toOriginalBridge;
		}

		/**
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
		 * @return the columns
		 */
		public String[] getColumns() {
			return columns;
		}

		/**
		 * @return the columnsInOriginal
		 */
		public String[] getColumnsInOriginal() {
			return columnsInOriginal;
		}

		/**
		 * @return the toOriginalBridge
		 */
		public ToOriginalIdBridge getToOriginalBridge() {
			return toOriginalBridge;
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			return "IdInfo [idAccessor=" + idAccessor + ", entityClass=" + entityClass + ", columns=" + Arrays.toString( columns ) + ", columnsInOriginal="
					+ Arrays.toString( columnsInOriginal ) + "]";
		}

	}

}
