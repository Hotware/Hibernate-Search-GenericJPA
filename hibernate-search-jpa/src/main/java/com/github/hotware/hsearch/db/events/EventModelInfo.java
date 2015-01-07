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

/**
 * @author Martin
 *
 */
public class EventModelInfo {

	private final String tableName;
	private final String originalTableName;
	private final java.lang.reflect.Field caseField;
	private final List<IdInfo> idInfos;

	public EventModelInfo(String tableName, String originalTableName,
			java.lang.reflect.Field caseField, List<IdInfo> idInfos) {
		super();
		this.tableName = tableName;
		this.originalTableName = originalTableName;
		this.caseField = caseField;
		this.idInfos = idInfos;
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
	 * @return the caseField
	 */
	public java.lang.reflect.Field getCaseField() {
		return caseField;
	}

	/**
	 * @return the idInfos
	 */
	public List<IdInfo> getIdInfos() {
		return idInfos;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "EventModelInfo [tableName=" + tableName
				+ ", originalTableName=" + originalTableName + ", caseField="
				+ caseField + ", idInfos=" + idInfos + "]";
	}

	public static class IdInfo {

		private final java.lang.reflect.Field field;
		private final Class<?> entityClass;
		private final String[] columns;
		private final String[] columnsInOriginal;

		public IdInfo(java.lang.reflect.Field field, Class<?> entityClass,
				String[] columns, String[] columnsInOriginal) {
			super();
			this.field = field;
			this.entityClass = entityClass;
			this.columns = columns;
			this.columnsInOriginal = columnsInOriginal;
		}

		/**
		 * @return the field
		 */
		public java.lang.reflect.Field getField() {
			return field;
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

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			return "IdInfo [field=" + field + ", entityClass=" + entityClass
					+ ", columns=" + Arrays.toString(columns)
					+ ", columnsInOriginal="
					+ Arrays.toString(columnsInOriginal) + "]";
		}

	}

}
