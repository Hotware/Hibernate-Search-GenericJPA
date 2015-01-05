/*
 * Copyright 2015 Martin Braun
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.hotware.hsearch.dto;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import com.github.hotware.hsearch.dto.annotations.DtoField;

/**
 * Parser Interface for Dto Objects to project from the index (no more manual
 * projections)
 * 
 * @author Martin Braun
 */
public interface DtoDescriptor {

	/**
	 * parses a class annotated with @DtoOverEntity and attempts to create a
	 * valid DtoDescription out of it
	 * 
	 * @param clazz
	 *            the class to parse
	 * @return a valid DtoDescription
	 * @throws IllegalArgumentException
	 *             when the Dto-Class is annotated in a wrong way (i.e. the 2
	 *             fieldNames for one field in the same profile)
	 */
	public DtoDescription getDtoDescription(Class<?> clazz);

	public final class DtoDescription {

		public static final String DEFAULT_PROFILE = (String) getDefaultValueForAnnotationMethod(
				DtoField.class, "profileName");
		public static final String DEFAULT_FIELD_NAME = (String) getDefaultValueForAnnotationMethod(
				DtoField.class, "fieldName");

		public static Object getDefaultValueForAnnotationMethod(
				Class<? extends Annotation> annotationClass, String name) {
			try {
				return annotationClass.getDeclaredMethod(name)
						.getDefaultValue();
			} catch (NoSuchMethodException e) {
				throw new RuntimeException(e);
			} catch (SecurityException e) {
				throw new RuntimeException(e);
			}
		}

		private final Class<?> dtoClass;
		private final Class<?> entityClass;
		private final Map<String, Set<FieldDescription>> fieldNamesForProfile;

		public DtoDescription(Class<?> dtoClass, Class<?> entityClass,
				Map<String, Set<FieldDescription>> fieldNamesForProfile) {
			super();
			this.dtoClass = dtoClass;
			this.entityClass = entityClass;
			this.fieldNamesForProfile = fieldNamesForProfile;
		}

		public Set<FieldDescription> getFieldDescriptionsForProfile(
				String profile) {
			return Collections.unmodifiableSet(this.fieldNamesForProfile
					.getOrDefault(profile, Collections.emptySet()));
		}

		public Class<?> getEntityClass() {
			return this.entityClass;
		}

		public Class<?> getDtoClass() {
			return this.dtoClass;
		}

		public static class FieldDescription {

			private final String fieldName;
			private final java.lang.reflect.Field field;

			public FieldDescription(String fieldName, Field field) {
				super();
				this.fieldName = fieldName;
				this.field = field;
			}

			public String getFieldName() {
				return fieldName;
			}

			public java.lang.reflect.Field getField() {
				return field;
			}

			@Override
			public int hashCode() {
				final int prime = 31;
				int result = 1;
				result = prime * result
						+ ((field == null) ? 0 : field.hashCode());
				result = prime * result
						+ ((fieldName == null) ? 0 : fieldName.hashCode());
				return result;
			}

			@Override
			public boolean equals(Object obj) {
				if (this == obj)
					return true;
				if (obj == null)
					return false;
				if (getClass() != obj.getClass())
					return false;
				FieldDescription other = (FieldDescription) obj;
				if (field == null) {
					if (other.field != null)
						return false;
				} else if (!field.equals(other.field))
					return false;
				if (fieldName == null) {
					if (other.fieldName != null)
						return false;
				} else if (!fieldName.equals(other.fieldName))
					return false;
				return true;
			}

		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result
					+ ((entityClass == null) ? 0 : entityClass.hashCode());
			result = prime
					* result
					+ ((fieldNamesForProfile == null) ? 0
							: fieldNamesForProfile.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			DtoDescription other = (DtoDescription) obj;
			if (entityClass == null) {
				if (other.entityClass != null)
					return false;
			} else if (!entityClass.equals(other.entityClass))
				return false;
			if (fieldNamesForProfile == null) {
				if (other.fieldNamesForProfile != null)
					return false;
			} else if (!fieldNamesForProfile.equals(other.fieldNamesForProfile))
				return false;
			return true;
		}

	}

}
