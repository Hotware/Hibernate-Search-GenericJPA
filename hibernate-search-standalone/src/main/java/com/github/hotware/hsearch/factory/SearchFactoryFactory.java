package com.github.hotware.hsearch.factory;

import java.util.Collection;

import org.hibernate.search.cfg.spi.SearchConfiguration;
import org.hibernate.search.engine.spi.SearchFactoryImplementor;
import org.hibernate.search.spi.SearchFactoryBuilder;

import com.github.hotware.hsearch.event.EventSource;

public final class SearchFactoryFactory {
	
	private SearchFactoryFactory() {
		throw new AssertionError("can't touch this!");
	}

	public static SearchFactory createSearchFactory(EventSource eventSource,
			SearchConfiguration searchConfiguration, Collection<Class<?>> classes) {
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