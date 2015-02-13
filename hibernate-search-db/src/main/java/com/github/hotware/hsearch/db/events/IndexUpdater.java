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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.search.backend.SingularTermQuery;
import org.hibernate.search.backend.SingularTermQuery.Type;
import org.hibernate.search.backend.spi.DeleteByQueryWork;
import org.hibernate.search.backend.spi.Work;
import org.hibernate.search.backend.spi.WorkType;
import org.hibernate.search.bridge.FieldBridge;
import org.hibernate.search.bridge.StringBridge;
import org.hibernate.search.engine.integration.impl.ExtendedSearchIntegrator;
import org.hibernate.search.engine.metadata.impl.DocumentFieldMetadata;
import org.hibernate.search.engine.spi.DocumentBuilderContainedEntity;
import org.jboss.logging.Logger;

import com.github.hotware.hsearch.entity.ReusableEntityProvider;
import com.github.hotware.hsearch.factory.Transaction;

/**
 * This class is the "glue" between a
 * {@link com.github.hotware.hsearch.db.UpdateSource} and the actual
 * Hibernate-Search index. It consumes Events coming from the UpdateSource and
 * updates the Hibernate-Search index accordingly
 * 
 * @author Martin Braun
 */
public class IndexUpdater implements UpdateConsumer {

	// TODO: think of a clever way of doing batching here
	// or maybe leave it as it is

	// TODO: unit test this with several batches

	private static final Logger LOGGER = Logger.getLogger(IndexUpdater.class);

	private final Map<Class<?>, IndexInformation> indexInformations;
	private final Map<Class<?>, List<Class<?>>> containedInIndexOf;
	private final Map<Class<?>, SingularTermQuery.Type> idTypesForEntities;
	private final ReusableEntityProvider entityProvider;
	private IndexWrapper indexWrapper;

	public IndexUpdater(Map<Class<?>, IndexInformation> indexInformations,
			Map<Class<?>, List<Class<?>>> containedInIndexOf,
			Map<Class<?>, Type> idTypesForEntities,
			ReusableEntityProvider entityProvider, IndexWrapper indexWrapper) {
		this.indexInformations = indexInformations;
		this.containedInIndexOf = containedInIndexOf;
		this.idTypesForEntities = idTypesForEntities;
		this.entityProvider = entityProvider;
		this.indexWrapper = indexWrapper;
	}

	public IndexUpdater(Map<Class<?>, IndexInformation> indexInformations,
			Map<Class<?>, List<Class<?>>> containedInIndexOf,
			Map<Class<?>, Type> idsTypesForEntities,
			ReusableEntityProvider entityProvider,
			ExtendedSearchIntegrator searchIntegrator) {
		this(indexInformations, containedInIndexOf, idsTypesForEntities,
				entityProvider, (IndexWrapper) null);
		this.indexWrapper = new DefaultIndexWrapper(searchIntegrator);
	}

	@Override
	public void updateEvent(List<UpdateInfo> updateInfos) {
		this.entityProvider.open();
		try {
			Transaction tx = new Transaction();
			for (UpdateInfo updateInfo : updateInfos) {
				Class<?> entityClass = updateInfo.getEntityClass();
				List<Class<?>> inIndexOf = this.containedInIndexOf
						.get(entityClass);
				if (inIndexOf != null && inIndexOf.size() != 0) {
					int eventType = updateInfo.getEventType();
					Object id = updateInfo.getId();
					switch (eventType) {
					case EventType.INSERT:
						this.indexWrapper.index(entityClass, inIndexOf, id, tx);
						break;
					case EventType.UPDATE:
						this.indexWrapper
								.update(entityClass, inIndexOf, id, tx);
						break;
					case EventType.DELETE:
						this.indexWrapper
								.delete(entityClass, inIndexOf, id, tx);
						break;
					default:
						LOGGER.warn("unknown eventType-id found: " + eventType);
					}
				} else {
					LOGGER.warn("class: " + entityClass
							+ " not found in any index!");
				}
			}
			tx.end();
		} finally {
			this.entityProvider.close();
		}
	}

	public static class IndexInformation {

		final Class<?> mainEntity;
		final Map<Class<?>, String> idsForEntities;

		public IndexInformation(Class<?> mainEntity,
				Map<Class<?>, String> idsForEntities) {
			this.mainEntity = mainEntity;
			this.idsForEntities = idsForEntities;
		}

	}

	public static interface IndexWrapper {

		public void delete(Class<?> entityClass, List<Class<?>> inIndexOf,
				Object id, Transaction tx);

		public void update(Class<?> entityClass, List<Class<?>> inIndexOf,
				Object id, Transaction tx);

		public void index(Class<?> entityClass, List<Class<?>> inIndexOf,
				Object id, Transaction tx);

	}

	private class DefaultIndexWrapper implements IndexWrapper {

		private final ExtendedSearchIntegrator searchIntegrator;
		private final Map<Class<?>, DocumentFieldMetadata> metaDataForIdFields;

		public DefaultIndexWrapper(ExtendedSearchIntegrator searchIntegrator) {
			this.searchIntegrator = searchIntegrator;
			this.metaDataForIdFields = new HashMap<>();
		}

		@Override
		public void delete(Class<?> entityClass, List<Class<?>> inIndexOf,
				Object id, Transaction tx) {
			for (int i = 0; i < inIndexOf.size(); ++i) {
				Class<?> indexClass = inIndexOf.get(i);
				IndexInformation info = IndexUpdater.this.indexInformations
						.get(indexClass);
				final String field = info.idsForEntities.get(entityClass);
				DocumentFieldMetadata metaDataForIdField = this.metaDataForIdFields
						.computeIfAbsent(
								indexClass,
								(Class<?> indexClazz) -> {
									if (this.searchIntegrator.getIndexedTypes()
											.contains(indexClazz)) {
										return this.searchIntegrator
												.getIndexBinding(indexClazz)
												.getDocumentBuilder()
												.getMetadata()
												.getDocumentFieldMetadataFor(
														field);
									} else {
										DocumentBuilderContainedEntity builder = this.searchIntegrator
												.getDocumentBuilderContainedEntity(indexClazz);
										if (builder == null) {
											throw new IllegalArgumentException(
													"no DocumentBuilder found for: "
															+ indexClazz);
										}
										return builder.getMetadata()
												.getDocumentFieldMetadataFor(
														field);
									}
								});
				SingularTermQuery.Type idType = IndexUpdater.this.idTypesForEntities
						.get(entityClass);
				Object idValueForDeletion;
				if (idType == SingularTermQuery.Type.STRING) {
					FieldBridge fb = metaDataForIdField.getFieldBridge();
					if (!(fb instanceof StringBridge)) {
						throw new IllegalArgumentException(
								"no TwoWayStringBridge found for field: "
										+ field);
					}
					idValueForDeletion = ((StringBridge) fb).objectToString(id);
				} else {
					idValueForDeletion = id;
				}
				this.searchIntegrator.getWorker().performWork(
						new DeleteByQueryWork(indexClass,
								new SingularTermQuery(field,
										idValueForDeletion, idType)), tx);
			}
		}

		@Override
		public void update(Class<?> entityClass, List<Class<?>> inIndexOf,
				Object id, Transaction tx) {
			Object entity = IndexUpdater.this.entityProvider.get(entityClass,
					id);
			this.searchIntegrator.getWorker().performWork(
					new Work(entity, WorkType.UPDATE), tx);
		}

		@Override
		public void index(Class<?> entityClass, List<Class<?>> inIndexOf,
				Object id, Transaction tx) {
			Object entity = IndexUpdater.this.entityProvider.get(entityClass,
					id);
			this.searchIntegrator.getWorker().performWork(
					new Work(entity, WorkType.INDEX), tx);
		}

	}

}
