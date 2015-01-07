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

import java.util.ArrayList;
import java.util.List;

import com.github.hotware.hsearch.db.events.annotations.Case;
import com.github.hotware.hsearch.db.events.annotations.IdFor;
import com.github.hotware.hsearch.db.events.annotations.Updates;

/**
 * @author Martin
 *
 */
public class EventModelParser {

	public List<EventModelInfo> parse(List<Class<?>> updateClasses) {
		List<EventModelInfo> ret = new ArrayList<>();
		for (Class<?> clazz : updateClasses) {
			Updates updates = clazz.getAnnotation(Updates.class);
			java.lang.reflect.Field caseField = null;
			List<EventModelInfo.IdInfo> idInfos = new ArrayList<>();
			if (updates != null) {
				for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
					field.setAccessible(true);
					IdFor idFor = field.getAnnotation(IdFor.class);
					Case eventCase = field.getAnnotation(Case.class);
					if (idFor != null && eventCase != null) {
						throw new IllegalArgumentException(
								"@IdFor and @Case can not be on the same Field. Class: "
										+ clazz + ". Field: " + field);
					}
					if (eventCase != null) {
						if (!field.getType().equals(Integer.class)) {
							throw new IllegalArgumentException(
									"Field hosting @Case is no Field of type Integer.  Class: "
											+ clazz + ". Field: " + field);
						}
						if (caseField == null) {
							caseField = field;
						} else {
							throw new IllegalArgumentException(
									"class cannot have two caseFields. Class: "
											+ clazz);
						}
					}
					if (idFor != null) {
						// TODO: Exception for wrong values
						EventModelInfo.IdInfo idInfo = new EventModelInfo.IdInfo(
								field, idFor.entityClass(), idFor.columns(),
								idFor.columnsInOriginal());
						idInfos.add(idInfo);
					}
				}
			} else {
				throw new IllegalArgumentException(
						"Updates class does not host @Updates. Class: " + clazz);
			}
			if (caseField == null) {
				throw new IllegalArgumentException(
						"no Integer Field found hosting @Case in Class: "
								+ clazz);
			}
			if (idInfos.size() == 0) {
				throw new IllegalArgumentException(
						"@Updates-class does not host @IdInfo: " + clazz);
			}
			//TODO: Exception for wrong values
			ret.add(new EventModelInfo(updates.tableName(), updates
					.originalTableName(), caseField, idInfos));
		}
		return ret;
	}
}
