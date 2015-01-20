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

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import com.github.hotware.hsearch.db.id.ToOriginalIdBridge;

/**
 * @author Martin
 *
 */
public class EventModelInfo {

	private final Class<?> updateClass;
	private final String tableName;
	private final String originalTableName;
	private final Function<Object, Integer> eventTypeAccessor;
	private final String eventTypeColumn;
	private final List<IdInfo> idInfos;

	public EventModelInfo(Class<?> updateClass, String tableName,
			String originalTableName,
			Function<Object, Integer> eventTypeAccessor,
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
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "EventModelInfo [updateClass=" + updateClass + ", tableName="
				+ tableName + ", originalTableName=" + originalTableName
				+ ", eventTypeAccessor=" + eventTypeAccessor
				+ ", eventTypeColumn=" + eventTypeColumn + ", idInfos="
				+ idInfos + "]";
	}

	public static class IdInfo {

		private final Function<Object, Object> idAccessor;
		private final Class<?> entityClass;
		private final String[] columns;
		private final String[] columnsInOriginal;
		private final ToOriginalIdBridge toOriginalBridge;

		public IdInfo(Function<Object, Object> idAccessor,
				Class<?> entityClass, String[] columns,
				String[] columnsInOriginal, ToOriginalIdBridge toOriginalBridge) {
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
		 * 
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			return "IdInfo [idAccessor=" + idAccessor + ", entityClass="
					+ entityClass + ", columns=" + Arrays.toString(columns)
					+ ", columnsInOriginal="
					+ Arrays.toString(columnsInOriginal) + "]";
		}

	}

}
