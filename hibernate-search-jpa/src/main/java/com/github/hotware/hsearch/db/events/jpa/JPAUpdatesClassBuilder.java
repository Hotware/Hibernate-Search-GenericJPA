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
package com.github.hotware.hsearch.db.events.jpa;

import com.github.hotware.hsearch.db.id.DefaultToOriginalIdBridge;
import com.github.hotware.hsearch.db.id.ToOriginalIdBridge;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Martin
 *
 */
public class JPAUpdatesClassBuilder {

	private String tableName;
	private String originalTableName;
	private Set<IdColumn> idColumns = new HashSet<>();

	public JPAUpdatesClassBuilder tableName(String tableName) {
		this.tableName = tableName;
		return this;
	}

	public JPAUpdatesClassBuilder originalTableName(String originalTableName) {
		this.originalTableName = originalTableName;
		return this;
	}

	public JPAUpdatesClassBuilder idColumn(IdColumn idColumn) {
		this.idColumns.add(idColumn);
		return this;
	}

	/**
	 * @return the tableName
	 */
	public String getTableName() {
		return tableName;
	}

	/**
	 * @param tableName
	 *            the tableName to set
	 */
	public void setTableName(String tableName) {
		this.tableName = tableName;
	}

	/**
	 * @return the originalTableName
	 */
	public String getOriginalTableName() {
		return originalTableName;
	}

	/**
	 * @param originalTableName
	 *            the originalTableName to set
	 */
	public void setOriginalTableName(String originalTableName) {
		this.originalTableName = originalTableName;
	}

	/**
	 * @return the idColumns
	 */
	public Set<IdColumn> getIdColumns() {
		return idColumns;
	}

	/**
	 * @param idColumns
	 *            the idColumns to set
	 */
	public void setIdColumns(Set<IdColumn> idColumns) {
		this.idColumns = idColumns;
	}

	public static final class IdColumn {
		private Class<?> entityClass;
		private String[] columns;
		private String[] columnsInOriginal;
		private Class<? extends ToOriginalIdBridge> toOriginalIdBridge;

		public IdColumn(Class<?> entityClass, String[] columns,
				String[] columnsInOriginal,
				Class<? extends ToOriginalIdBridge> toOriginalIdBridge) {
			super();
			this.entityClass = entityClass;
			this.columns = columns;
			this.columnsInOriginal = columnsInOriginal;
			this.toOriginalIdBridge = toOriginalIdBridge;
		}

		public IdColumn(Class<?> entityClass, String[] columns,
				String[] columnsInOriginal) {
			this(entityClass, columns, columnsInOriginal,
					DefaultToOriginalIdBridge.class);
		}

		/**
		 * @return the columns
		 */
		public String[] getColumns() {
			return columns;
		}

		/**
		 * @param columns
		 *            the columns to set
		 */
		public void setColumns(String[] columns) {
			this.columns = columns;
		}

		/**
		 * @return the columnsInOriginal
		 */
		public String[] getColumnsInOriginal() {
			return columnsInOriginal;
		}

		/**
		 * @param columnsInOriginal
		 *            the columnsInOriginal to set
		 */
		public void setColumnsInOriginal(String[] columnsInOriginal) {
			this.columnsInOriginal = columnsInOriginal;
		}

		/**
		 * @return the entityClass
		 */
		public Class<?> getEntityClass() {
			return entityClass;
		}

		/**
		 * @param entityClass
		 *            the entityClass to set
		 */
		public void setEntityClass(Class<?> entityClass) {
			this.entityClass = entityClass;
		}

		/**
		 * @return the toOriginalIdBridge
		 */
		public Class<? extends ToOriginalIdBridge> getToOriginalIdBridge() {
			return toOriginalIdBridge;
		}

		/**
		 * @param toOriginalIdBridge
		 *            the toOriginalIdBridge to set
		 */
		public void setToOriginalIdBridge(
				Class<? extends ToOriginalIdBridge> toOriginalIdBridge) {
			this.toOriginalIdBridge = toOriginalIdBridge;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + Arrays.hashCode(columns);
			result = prime * result + Arrays.hashCode(columnsInOriginal);
			result = prime * result
					+ ((entityClass == null) ? 0 : entityClass.hashCode());
			result = prime
					* result
					+ ((toOriginalIdBridge == null) ? 0 : toOriginalIdBridge
							.hashCode());
			return result;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			IdColumn other = (IdColumn) obj;
			if (!Arrays.equals(columns, other.columns)) {
				return false;
			}
			if (!Arrays.equals(columnsInOriginal, other.columnsInOriginal)) {
				return false;
			}
			if (entityClass == null) {
				if (other.entityClass != null) {
					return false;
				}
			} else if (!entityClass.equals(other.entityClass)) {
				return false;
			}
			if (toOriginalIdBridge == null) {
				if (other.toOriginalIdBridge != null) {
					return false;
				}
			} else if (!toOriginalIdBridge.equals(other.toOriginalIdBridge)) {
				return false;
			}
			return true;
		}

	}

}
