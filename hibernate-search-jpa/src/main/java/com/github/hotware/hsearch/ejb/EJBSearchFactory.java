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
package com.github.hotware.hsearch.ejb;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.concurrent.ManagedScheduledExecutorService;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.Query;
import org.hibernate.search.backend.spi.SingularTermDeletionQuery;
import org.hibernate.search.engine.integration.impl.ExtendedSearchIntegrator;
import org.hibernate.search.filter.FilterCachingStrategy;
import org.hibernate.search.indexes.IndexReaderAccessor;
import org.hibernate.search.query.dsl.QueryContextBuilder;
import org.hibernate.search.spi.SearchIntegrator;
import org.hibernate.search.spi.SearchIntegratorBuilder;
import org.hibernate.search.stat.Statistics;

import com.github.hotware.hsearch.db.events.EventModelInfo;
import com.github.hotware.hsearch.db.events.EventModelParser;
import com.github.hotware.hsearch.db.events.EventType;
import com.github.hotware.hsearch.db.events.IndexUpdater;
import com.github.hotware.hsearch.db.events.TriggerSQLStringSource;
import com.github.hotware.hsearch.db.events.UpdateConsumer;
import com.github.hotware.hsearch.db.events.IndexUpdater.IndexInformation;
import com.github.hotware.hsearch.db.events.UpdateSource;
import com.github.hotware.hsearch.db.events.jpa.JPAUpdateSource;
import com.github.hotware.hsearch.entity.EntityProvider;
import com.github.hotware.hsearch.entity.jpa.EntityManagerCloseable;
import com.github.hotware.hsearch.entity.jpa.EntityManagerEntityProvider;
import com.github.hotware.hsearch.entity.jpa.JPAReusableEntityProvider;
import com.github.hotware.hsearch.factory.SearchConfigurationImpl;
import com.github.hotware.hsearch.factory.SearchFactory;
import com.github.hotware.hsearch.factory.SearchFactoryImpl;
import com.github.hotware.hsearch.jpa.events.MetaModelParser;
import com.github.hotware.hsearch.query.HSearchQuery;
import com.github.hotware.hsearch.transaction.TransactionContext;

/**
 * Base class to create SearchFactories in a EJB environment. Uses a
 * JPAEventSource.
 * 
 * @author Martin Braun
 */
public abstract class EJBSearchFactory implements SearchFactory, UpdateConsumer {

	private final Logger LOGGER = Logger.getLogger(EntityManagerFactory.class
			.getName());
	SearchFactory searchFactory;
	MetaModelParser parser;
	UpdateSource updateSource;

	public EntityProvider entityProvider(EntityManager em) {
		return new EntityManagerEntityProvider(new EntityManagerCloseable(em),
				this.parser.getIdProperties());
	}

	protected abstract EntityManagerFactory getEmf();

	protected abstract String getConfigFile();

	protected abstract List<Class<?>> getAdditionalIndexedClasses();

	// THESE ARE NEEDED FOR THE UPDATES
	// TODO: make this easier

	protected abstract List<Class<?>> getUpdateClasses();

	protected abstract Map<Class<?>, IndexInformation> getIndexInformations();

	protected abstract Map<Class<?>, List<Class<?>>> getContainedInIndexOf();

	protected abstract Map<Class<?>, SingularTermDeletionQuery.Type> getIdTypesForEntities();

	protected abstract TimeUnit getDelayUnit();

	protected abstract long getDelay();

	protected abstract int getBatchSizeForUpdates();

	protected abstract TriggerSQLStringSource getTriggerSQLStringSource();

	protected abstract ManagedScheduledExecutorService getManagedScheduledExecutorServiceForUpdater();

	protected abstract boolean isUseJTATransaction();

	@PostConstruct
	protected void init() {
		this.parser = new MetaModelParser();
		this.parser.parse(this.getEmf().getMetamodel());

		SearchConfigurationImpl config;
		if (this.getConfigFile() != null && !this.getConfigFile().equals("")) {
			LOGGER.info("using config @" + this.getConfigFile());
			try (InputStream is = this.getClass().getResourceAsStream(
					this.getConfigFile())) {
				Properties props = new Properties();
				props.load(is);
				config = new SearchConfigurationImpl(props);
			} catch (IOException e) {
				throw new RuntimeException(
						"IOException while loading property file.", e);
			}
		} else {
			config = new SearchConfigurationImpl();
		}
		this.getAdditionalIndexedClasses().forEach((clazz) -> {
			config.addClass(clazz);
		});

		SearchIntegratorBuilder builder = new SearchIntegratorBuilder();
		// we have to build an integrator here (but we don't need it afterwards)
		builder.configuration(config).buildSearchIntegrator();
		this.parser.getIndexRelevantEntites().forEach((clazz) -> {
			builder.addClass(clazz);
		});
		SearchIntegrator impl = builder.buildSearchIntegrator();
		this.searchFactory = new SearchFactoryImpl(
				impl.unwrap(ExtendedSearchIntegrator.class));

		JPAReusableEntityProvider entityProvider = new JPAReusableEntityProvider(
				this.getEmf(), this.parser.getIdProperties(),
				this.isUseJTATransaction());
		IndexUpdater indexUpdater = new IndexUpdater(
				this.getIndexInformations(), this.getContainedInIndexOf(),
				this.getIdTypesForEntities(), entityProvider,
				impl.unwrap(ExtendedSearchIntegrator.class));
		EventModelParser eventModelParser = new EventModelParser();
		List<EventModelInfo> eventModelInfos = eventModelParser
				.parse(new ArrayList<>(this.getUpdateClasses()));

		EntityManager em = null;
		try {
			em = this.getEmf().createEntityManager();
			Connection connection = em.unwrap(Connection.class);

			TriggerSQLStringSource triggerSource = this
					.getTriggerSQLStringSource();
			try {
				for (String str : triggerSource.getSetupCode()) {
					Statement statement = connection.createStatement();
					LOGGER.info(str);
					statement.addBatch(connection.nativeSQL(str));
					statement.executeBatch();
					connection.commit();
				}
				for (EventModelInfo info : eventModelInfos) {
					for (String unSetupCode : triggerSource
							.getSpecificUnSetupCode(info)) {
						Statement statement = connection.createStatement();
						LOGGER.info(unSetupCode);
						statement.addBatch(connection.nativeSQL(unSetupCode));
						statement.executeBatch();
						connection.commit();
					}
					for (String setupCode : triggerSource
							.getSpecificSetupCode(info)) {
						Statement statement = connection.createStatement();
						LOGGER.info(setupCode);
						statement.addBatch(connection.nativeSQL(setupCode));
						statement.executeBatch();
						connection.commit();
					}
					for (int eventType : EventType.values()) {
						String[] triggerDropStrings = triggerSource
								.getTriggerDropCode(info, eventType);
						for (String triggerCreationString : triggerDropStrings) {
							Statement statement = connection.createStatement();
							LOGGER.info(triggerCreationString);
							statement.addBatch(connection
									.nativeSQL(triggerCreationString));
							statement.executeBatch();
							connection.commit();
						}
					}
					for (int eventType : EventType.values()) {
						String[] triggerCreationStrings = triggerSource
								.getTriggerCreationCode(info, eventType);
						for (String triggerCreationString : triggerCreationStrings) {
							Statement statement = connection.createStatement();
							LOGGER.info(triggerCreationString);
							statement.addBatch(connection
									.nativeSQL(triggerCreationString));
							statement.executeBatch();
							connection.commit();
						}
					}

				}
			} catch (SQLException e) {
				try {
					connection.rollback();
				} catch (SQLException e1) {
					// TODO: better Exception:
					throw new RuntimeException(e1);
				}
				// TODO: better Exception:
				throw new RuntimeException(e);
			}
		} finally {
			if (em != null) {
				try {
					em.close();
				} catch (IllegalStateException e) {
					// yay, JPA...
				}
			}
		}

		this.updateSource = new JPAUpdateSource(eventModelInfos, this.getEmf(),
				this.isUseJTATransaction(), this.getDelay(),
				this.getDelayUnit(),
				this.getBatchSizeForUpdates(),
				this.getManagedScheduledExecutorServiceForUpdater());

		this.updateSource.setUpdateConsumers(Arrays.asList(indexUpdater, this));
		this.updateSource.start();
	}

	@PreDestroy
	protected void atShutdown() {
		try {
			this.updateSource.stop();
			this.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public Set<Class<?>> getIndexRelevantEntitiesFromJPA() {
		return this.parser.getIndexRelevantEntites();
	}

	@Override
	public Set<Class<?>> getIndexedEntities() {
		return this.searchFactory.getIndexedEntities();
	}

	@Override
	public void index(Iterable<?> entities, TransactionContext tc) {
		this.searchFactory.index(entities, tc);
	}

	@Override
	public void close() throws IOException {
		this.searchFactory.close();
	}

	@Override
	public void update(Iterable<?> entities, TransactionContext tc) {
		this.searchFactory.update(entities, tc);
	}

	@Override
	public IndexReaderAccessor getIndexReaderAccessor() {
		return this.searchFactory.getIndexReaderAccessor();
	}

	@Override
	public QueryContextBuilder buildQueryBuilder() {
		return this.searchFactory.buildQueryBuilder();
	}

	@Override
	public void optimize() {
		this.searchFactory.optimize();
	}

	@Override
	public void optimize(Class<?> entity) {
		this.searchFactory.optimize(entity);
	}

	@Override
	public Statistics getStatistics() {
		return this.searchFactory.getStatistics();
	}

	@Override
	public void delete(Iterable<?> entities, TransactionContext tc) {
		this.searchFactory.delete(entities, tc);
	}

	@Override
	public void purgeAll(Class<?> entityClass) {
		this.searchFactory.purgeAll(entityClass);
	}

	@Override
	public FilterCachingStrategy getFilterCachingStrategy() {
		return this.searchFactory.getFilterCachingStrategy();
	}

	@Override
	public Analyzer getAnalyzer(String name) {
		return this.searchFactory.getAnalyzer(name);
	}

	@Override
	public Analyzer getAnalyzer(Class<?> clazz) {
		return this.searchFactory.getAnalyzer(clazz);
	}

	@Override
	public void purgeAll(Class<?> entityClass, TransactionContext tc) {
		this.searchFactory.purgeAll(entityClass, tc);
	}

	@Override
	public HSearchQuery createQuery(Query query, Class<?>... targetedEntities) {
		return this.searchFactory.createQuery(query, targetedEntities);
	}

	@Override
	public void purge(Class<?> entityClass, Serializable id,
			TransactionContext tc) {
		this.searchFactory.purge(entityClass, id, tc);
	}

	@Override
	public void purge(Iterable<?> entities, TransactionContext tc) {
		this.searchFactory.purge(entities, tc);
	}

	@Override
	public void purge(Class<?> entityClass, Query query, TransactionContext tc) {
		this.searchFactory.purge(entityClass, query, tc);
	}

}
