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
package com.github.hotware.hsearch.db.tableInfo.jpa;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.persistence.descriptors.ClassDescriptor;
import org.eclipse.persistence.internal.helper.DatabaseField;
import org.eclipse.persistence.internal.jpa.EntityManagerImpl;

import com.github.hotware.hsearch.db.tableInfo.TableInfo;
import com.github.hotware.hsearch.db.tableInfo.TableInfoSource;

/**
 * @author Martin Braun
 */
public class EclipseLinkTableInfoSource implements TableInfoSource {

	private final EntityManagerImpl em;

	public EclipseLinkTableInfoSource(EntityManagerImpl em) {
		this.em = em;
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<TableInfo> getTableInfos(List<Class<?>> classesInIndex) {
		List<TableInfo> ret = new ArrayList<>();
		for (Class<?> clazz : classesInIndex) {
			ClassDescriptor classDescriptor = this.em.getSession()
					.getDescriptor(clazz);

			final List<String> primaryKeyFieldNames;
			final Map<String, Class<?>> primaryKeyColumnTypes;
			{
				primaryKeyFieldNames = new ArrayList<>();
				primaryKeyColumnTypes = new HashMap<>();
				for (DatabaseField pkField : classDescriptor
						.getPrimaryKeyFields()) {
					String idColumn = String.format("%s.%s", pkField.getTable()
							.getName(), pkField.getName());
					primaryKeyFieldNames.add(idColumn);
					primaryKeyColumnTypes.put(idColumn, pkField.getType());
				}
			}

			final List<String> tableNames;
			{
				tableNames = new ArrayList<>();
				tableNames.addAll(classDescriptor.getTableNames());
			}

			TableInfo tableInfo = new TableInfo(clazz,
					Collections.unmodifiableList(tableNames),
					Collections.unmodifiableList(primaryKeyFieldNames),
					Collections.unmodifiableMap(primaryKeyColumnTypes));
			ret.add(tableInfo);
			// FIXME: mappng tables are not fine here!
			// -> what happens if we add/insert something in the mapping table
		}
		return ret;
	}
}
