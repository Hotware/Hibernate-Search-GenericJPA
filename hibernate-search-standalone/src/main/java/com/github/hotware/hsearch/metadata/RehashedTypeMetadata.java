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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.search.engine.metadata.impl.DocumentFieldMetadata;
import org.hibernate.search.engine.metadata.impl.TypeMetadata;

/**
 * @author Martin Braun
 */
public final class RehashedTypeMetadata {

	/**
	 * the original TypeMetadata object this object rehashes
	 */
	TypeMetadata originalTypeMetadata;

	/**
	 * this contains all the possible fields in the lucene index for every given
	 * class contained in the index this metadata object corresponds to
	 */
	Map<Class<?>, List<String>> idFieldNamesForType = new HashMap<>();

	/**
	 * this contains the Java Bean property name of the id field for every given
	 * class contained in the index. every propertyname is relative to it's
	 * hosting entity instead of the index-root. This is needed to be able to
	 * retrieve the entity from the database
	 */
	Map<Class<?>, String> idPropertyNameForType = new HashMap<>();

	/**
	 * this contains the DocumentFieldMetadata for each id-fieldname. This
	 * provides info about how each id is stored in the index
	 */
	Map<String, DocumentFieldMetadata> documentFieldMetadataForIdFieldName = new HashMap<>();

	/**
	 * @return the originalTypeMetadata
	 */
	public TypeMetadata getOriginalTypeMetadata() {
		return originalTypeMetadata;
	}

	/**
	 * @param originalTypeMetadata
	 *            the originalTypeMetadata to set
	 */
	public void setOriginalTypeMetadata(TypeMetadata originalTypeMetadata) {
		this.originalTypeMetadata = originalTypeMetadata;
	}

	/**
	 * @return the idFieldNamesForType
	 */
	public Map<Class<?>, List<String>> getIdFieldNamesForType() {
		return idFieldNamesForType;
	}

	/**
	 * @param idFieldNamesForType
	 *            the idFieldNamesForType to set
	 */
	public void setIdFieldNamesForType(
			Map<Class<?>, List<String>> idFieldNamesForType) {
		this.idFieldNamesForType = idFieldNamesForType;
	}

	/**
	 * @return the idPropertyNameForType
	 */
	public Map<Class<?>, String> getIdPropertyNameForType() {
		return idPropertyNameForType;
	}

	/**
	 * @param idPropertyNameForType
	 *            the idPropertyNameForType to set
	 */
	public void setIdPropertyNameForType(
			Map<Class<?>, String> idPropertyNameForType) {
		this.idPropertyNameForType = idPropertyNameForType;
	}

	/**
	 * @return the documentFieldMetadataForIdFieldName
	 */
	public Map<String, DocumentFieldMetadata> getDocumentFieldMetadataForIdFieldName() {
		return documentFieldMetadataForIdFieldName;
	}

	/**
	 * @param documentFieldMetadataForIdFieldName
	 *            the documentFieldMetadataForIdFieldName to set
	 */
	public void setDocumentFieldMetadataForIdFieldName(
			Map<String, DocumentFieldMetadata> documentFieldMetadataForIdFieldName) {
		this.documentFieldMetadataForIdFieldName = documentFieldMetadataForIdFieldName;
	}

}