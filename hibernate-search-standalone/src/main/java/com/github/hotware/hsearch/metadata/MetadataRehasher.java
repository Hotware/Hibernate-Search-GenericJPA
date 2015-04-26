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
package com.github.hotware.hsearch.metadata;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.search.engine.metadata.impl.DocumentFieldMetadata;
import org.hibernate.search.engine.metadata.impl.EmbeddedTypeMetadata;
import org.hibernate.search.engine.metadata.impl.PropertyMetadata;
import org.hibernate.search.engine.metadata.impl.TypeMetadata;

/**
 * @author Martin Braun
 */
public final class MetadataRehasher {

	public List<RehashedTypeMetadata> rehash(List<TypeMetadata> originals) {
		List<RehashedTypeMetadata> rehashed = new ArrayList<>();
		for (TypeMetadata original : originals) {
			rehashed.add(this.rehash(original));
		}
		return rehashed;
	}

	public RehashedTypeMetadata rehash(TypeMetadata original) {
		RehashedTypeMetadata rehashed = new RehashedTypeMetadata();
		rehashed.originalTypeMetadata = original;

		if (!this.handlePropertyMetadata(original, rehashed,
				original.getIdPropertyMetadata())) {
			throw new IllegalArgumentException(
					"couldn't find any id field for: "
							+ original.getType()
							+ "! This is required in order to use Hibernate Search with JPA!");
		}

		for (EmbeddedTypeMetadata embedded : original.getEmbeddedTypeMetadata()) {
			this.rehashRec(embedded, rehashed);
		}
		return rehashed;
	}

	private void rehashRec(EmbeddedTypeMetadata original,
			RehashedTypeMetadata rehashed) {
		// handle the current TypeMetadata
		this.handleTypeMetadata(original, rehashed);
		// recursion
		for (EmbeddedTypeMetadata embedded : original.getEmbeddedTypeMetadata()) {
			this.rehashRec(embedded, rehashed);
		}
	}

	private void handleTypeMetadata(EmbeddedTypeMetadata original,
			RehashedTypeMetadata rehashed) {
		for (PropertyMetadata propertyMetadata : original
				.getAllPropertyMetadata()) {
			if (this.handlePropertyMetadata(original, rehashed,
					propertyMetadata)) {
				return;
			}
		}
		throw new IllegalArgumentException(
				"couldn't find any id field for: "
						+ original.getType()
						+ "! This is required in order to use Hibernate Search with JPA!");
	}

	private boolean handlePropertyMetadata(TypeMetadata original,
			RehashedTypeMetadata rehashed, PropertyMetadata propertyMetadata) {
		for (DocumentFieldMetadata documentFieldMetadata : propertyMetadata
				.getFieldMetadata()) {
			// this must either be id or id of an embedded object
			if (documentFieldMetadata.isIdInEmbedded()
					|| documentFieldMetadata.isId()) {
				Class<?> type = original.getType();
				rehashed.idFieldNamesForType.computeIfAbsent(type, (key) -> {
					return new ArrayList<>();
				}).add(documentFieldMetadata.getName());
				rehashed.idPropertyNameForType.put(type,
						propertyMetadata.getPropertyAccessorName());
				if (rehashed.documentFieldMetadataForIdFieldName
						.containsKey(documentFieldMetadata.getName())) {
					throw new AssertionError("field handled twice!");
				}
				rehashed.documentFieldMetadataForIdFieldName.put(
						documentFieldMetadata.getName(), documentFieldMetadata);
				return true;
			}
		}
		return false;
	}
}
