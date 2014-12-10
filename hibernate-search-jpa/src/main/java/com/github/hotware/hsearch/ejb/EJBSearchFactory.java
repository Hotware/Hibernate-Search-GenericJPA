package com.github.hotware.hsearch.ejb;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceContext;

import org.apache.lucene.search.Query;
import org.hibernate.search.backend.spi.WorkType;
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
 * 
 * @author Martin Braun
 */
public class EJBSearchFactory implements SearchFactory {

	private final Logger LOGGER = Logger.getLogger(EntityManagerFactory.class
			.getName());
	SearchFactory searchFactory;
	@PersistenceContext
	EntityManagerFactory emf;
	MetaModelParser parser;
	@Resource(name = "com.github.hotware.hsearch.configfile")
	String configFile;
	
	public static EJBSearchFactory create(EntityManagerFactory emf, String configFile) {
		EJBSearchFactory ret = new EJBSearchFactory();
		ret.emf = emf;
		ret.configFile = configFile;
		ret.init();
		return ret;
	}

	public EntityProvider provider(EntityManager em) {
		return new EntityManagerEntityProvider(em,
				this.parser.getIdProperties());
	}

	@PostConstruct
	void init() {
		this.parser = new MetaModelParser();
		this.parser.parse(this.emf.getMetamodel());
		JPAEventSource eventSource = JPAEventSource.register(
				parser.getIndexRelevantEntites(), true);
		SearchConfigurationImpl config;
		if(this.configFile != null && !this.configFile.equals("")) {
			LOGGER.info("using config @" + this.configFile);
			try(InputStream is = this.getClass().getResourceAsStream(this.configFile)) {
				Properties props = new Properties();
				props.load(is);
				config = new SearchConfigurationImpl(props);
			} catch (IOException e) {
				throw new RuntimeException("IOException while loading property file.", e);
			}
		} else {
			config = new SearchConfigurationImpl();
		}
		this.searchFactory = SearchFactoryFactory
				.createSearchFactory(eventSource,
						config,
						this.parser.getIndexRelevantEntites());
		//we don't need this anymore!
		this.emf.close();
		this.emf = null;

	}

	@PreDestroy
	public void atShutdown() throws IOException {
		this.close();
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
	public void doIndexWork(Iterable<?> entities, WorkType workType,
			TransactionContext tc) {
		this.searchFactory.doIndexWork(entities, workType, tc);
	}

	@Override
	public void delete(Iterable<?> entities, TransactionContext tc) {
		this.searchFactory.delete(entities, tc);
	}

	@Override
	public void doIndexWork(Iterable<?> entities, WorkType workType) {
		this.searchFactory.doIndexWork(entities, workType);
	}

	@Override
	public void doIndexWork(Object entities, WorkType workType) {
		this.searchFactory.doIndexWork(entities, workType);
	}

	@Override
	public void purgeAll(Class<?> entityClass) {
		this.searchFactory.purgeAll(entityClass);
	}

	@Override
	public <T> HSearchQuery<T> createQuery(Query query, Class<T> targetedEntity) {
		return this.searchFactory.createQuery(query, targetedEntity);
	}

}
