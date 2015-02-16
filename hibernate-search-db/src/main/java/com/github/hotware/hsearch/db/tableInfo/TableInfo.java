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
package com.github.hotware.hsearch.db.tableInfo;

import java.util.List;
import java.util.Map;

/**
 * @author Martin Braun
 */
public class TableInfo {

	private final Class<?> entityClass;
	private final List<String> tableNames;
	private final List<String> idColumns;
	private final Map<String, Class<?>> idColumnTypes;

	// FIXME: idType!!

	public TableInfo(Class<?> entityClass, List<String> tableNames,
			List<String> idColumns, Map<String, Class<?>> idColumnTypes) {
		this.entityClass = entityClass;
		this.tableNames = tableNames;
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
	 * @return the tableName
	 */
	public List<String> getTableNames() {
		return this.tableNames;
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
		builder.append("TableInfo [entityClass=").append(this.entityClass)
				.append(", tableName=").append(this.tableNames)
				.append(", idColumns=").append(this.idColumns).append("]");
		return builder.toString();
	}

}
