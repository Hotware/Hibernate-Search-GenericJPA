package com.github.hotware.lucene.extension.hsearch.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.hibernate.search.query.engine.spi.HSQuery;

import com.github.hotware.lucene.extension.hsearch.dto.DtoDescriptor.DtoDescription;

public class HibernateSearchQueryExecutor {

	private final Map<Class<?>, DtoDescription> dtoDescriptions;
	private final DtoDescriptor dtoDescriptor;

	public HibernateSearchQueryExecutor() {
		this.dtoDescriptions = new HashMap<>();
		this.dtoDescriptor = new DtoDescriptorImpl();
	}

	/**
	 * @param hsQuery
	 *            (will be modified internally!)
	 */
	public <T> List<T> executeHSQuery(HSQuery hsQuery, Class<T> clazz) {
		return this.executeHSQuery(hsQuery, clazz,
				DtoDescription.DEFAULT_PROFILE);
	}

	/**
	 * @param hsQuery
	 *            (will be modified internally!)
	 */
	public <T> List<T> executeHSQuery(HSQuery hsQuery, Class<T> returnedType,
			String profile) {

		DtoDescription desc = this.dtoDescriptions.computeIfAbsent(returnedType, (
				clazz_) -> {
			return this.dtoDescriptor.getDtoDescription(clazz_);
		});
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
			
			ret = hsQuery.queryEntityInfos().stream().map((entityInfo) -> {
				try {
					T val = returnedType.newInstance();
					Object[] projectedValues = entityInfo.getProjection();
					for (int i = 0; i < projection.size(); i++) {
						java.lang.reflect.Field field = fields.get(i);
						field.set(val, projectedValues[i]);
					}
					return val;
				} catch (InstantiationException | IllegalAccessException e) {
					throw new RuntimeException(e);
				}
			}).collect(Collectors.toList());

			hsQuery.getTimeoutManager().stop();
		}
		return ret;
	}
}
