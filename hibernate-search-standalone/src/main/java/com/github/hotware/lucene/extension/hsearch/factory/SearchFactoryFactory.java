package com.github.hotware.lucene.extension.hsearch.factory;

import java.util.List;

import org.hibernate.search.cfg.spi.SearchConfiguration;
import org.hibernate.search.engine.spi.SearchFactoryImplementor;
import org.hibernate.search.spi.SearchFactoryBuilder;

import com.github.hotware.lucene.extension.hsearch.event.EventSource;

public final class SearchFactoryFactory {
	
	private SearchFactoryFactory() {
		throw new AssertionError("can't touch this!");
	}

	public static SearchFactory createSearchFactory(EventSource eventSource,
			SearchConfiguration searchConfiguration, List<Class<?>> classes) {
		SearchFactoryBuilder builder = new SearchFactoryBuilder();
		builder.configuration(searchConfiguration).buildSearchFactory();
		classes.forEach((clazz) -> {
			builder.addClass(clazz);
		});
		SearchFactoryImplementor impl = builder.buildSearchFactory();
		SearchFactory factory = new SearchFactoryImpl(impl);
		eventSource.setEventConsumer(factory);
		return factory;
	}

}