package com.github.hotware.hsearch.ejb;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.Query;
import org.hibernate.search.engine.impl.FilterDef;
import org.hibernate.search.filter.FilterCachingStrategy;
import org.hibernate.search.indexes.IndexReaderAccessor;
import org.hibernate.search.query.dsl.QueryContextBuilder;
import org.hibernate.search.stat.Statistics;

import com.github.hotware.hsearch.entity.EntityProvider;
import com.github.hotware.hsearch.entity.jpa.EntityManagerEntityProvider;
import com.github.hotware.hsearch.factory.SearchConfigurationImpl;
import com.github.hotware.hsearch.factory.SearchFactory;
import com.github.hotware.hsearch.factory.SearchFactoryFactory;
import com.github.hotware.hsearch.jpa.event.JPAEventSource;
import com.github.hotware.hsearch.jpa.event.MetaModelParser;
import com.github.hotware.hsearch.query.HSearchQuery;
import com.github.hotware.hsearch.transaction.TransactionContext;

/**
 * Base class to create SearchFactories in a EJB environment. Uses a
 * JPAEventSource.
 * 
 * @author Martin Braun
 */
public abstract class EJBSearchFactory implements SearchFactory {

	private final Logger LOGGER = Logger.getLogger(EntityManagerFactory.class
			.getName());
	SearchFactory searchFactory;
	MetaModelParser parser;

	public EntityProvider entityProvider(EntityManager em) {
		return new EntityManagerEntityProvider(em,
				this.parser.getIdProperties());
	}

	protected abstract EntityManagerFactory getEmf();

	protected abstract String getConfigFile();

	protected abstract List<Class<?>> getAdditionalIndexedClasses();

	@PostConstruct
	void init() {
		this.parser = new MetaModelParser();
		this.parser.parse(this.getEmf().getMetamodel());
		JPAEventSource eventSource = JPAEventSource.register(
				this.parser.getIndexRelevantEntites(), true);
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
		this.searchFactory = SearchFactoryFactory.createSearchFactory(
				eventSource, config, this.parser.getIndexRelevantEntites());
	}

	@PreDestroy
	void atShutdown() {
		try {
			this.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
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
	public <T> HSearchQuery<T> createQuery(Query query, Class<T> targetedEntity) {
		return this.searchFactory.createQuery(query, targetedEntity);
	}

	@Override
	public FilterCachingStrategy getFilterCachingStrategy() {
		return this.searchFactory.getFilterCachingStrategy();
	}

	@Override
	public FilterDef getFilterDefinition(String name) {
		return this.getFilterDefinition(name);
	}

	@Override
	public int getFilterCacheBitResultsSize() {
		return this.getFilterCacheBitResultsSize();
	}

	@Override
	public Analyzer getAnalyzer(String name) {
		return this.getAnalyzer(name);
	}

	@Override
	public Analyzer getAnalyzer(Class<?> clazz) {
		return this.getAnalyzer(clazz);
	}

}
