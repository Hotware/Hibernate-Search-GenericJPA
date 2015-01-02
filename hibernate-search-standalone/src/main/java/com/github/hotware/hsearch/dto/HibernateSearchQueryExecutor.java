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
package com.github.hotware.hsearch.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.hibernate.search.query.engine.spi.HSQuery;

import com.github.hotware.hsearch.dto.DtoDescriptor.DtoDescription;

/**
 * projection utility class to automatically convert from projections back to a
 * DtoObject. The projection to use is specified via
 * {@link com.github.hotware.hsearch.dto.annotations.DtoField}(s)
 * 
 * @author Martin
 *
 */
public class HibernateSearchQueryExecutor {

	private final Map<Class<?>, DtoDescription> dtoDescriptions;
	private final DtoDescriptor dtoDescriptor;

	public HibernateSearchQueryExecutor() {
		this.dtoDescriptions = new HashMap<>();
		this.dtoDescriptor = new DtoDescriptorImpl();
	}

	public <T> List<T> executeHSQuery(HSQuery hsQuery, Class<T> clazz) {
		return this.executeHSQuery(hsQuery, clazz,
				DtoDescription.DEFAULT_PROFILE);
	}

	public <T> List<T> executeHSQuery(HSQuery hsQuery, Class<T> returnedType,
			String profile) {

		DtoDescription desc = this.dtoDescriptions.computeIfAbsent(
				returnedType, (clazz_) -> {
					return this.dtoDescriptor.getDtoDescription(clazz_);
				});
		String[] projectedFieldsBefore = hsQuery.getProjectedFields();
		try {
			List<String> projection = new ArrayList<>();
			List<java.lang.reflect.Field> fields = new ArrayList<>();
			desc.getFieldDescriptionsForProfile(profile).forEach((fd) -> {
				projection.add(fd.getFieldName());
				fields.add(fd.getField());
			});
			hsQuery.projection(projection.toArray(new String[0]));

			List<T> ret;
			{
				hsQuery.getTimeoutManager().start();

				ret = hsQuery
						.queryEntityInfos()
						.stream()
						.map((entityInfo) -> {
							try {
								T val = returnedType.newInstance();
								Object[] projectedValues = entityInfo
										.getProjection();
								for (int i = 0; i < projection.size(); i++) {
									java.lang.reflect.Field field = fields
											.get(i);
									field.set(val, projectedValues[i]);
								}
								return val;
							} catch (InstantiationException
									| IllegalAccessException e) {
								throw new RuntimeException(e);
							}
						}).collect(Collectors.toList());

				hsQuery.getTimeoutManager().stop();
			}
			return ret;
		} finally {
			hsQuery.projection(projectedFieldsBefore);
		}
	}
}
