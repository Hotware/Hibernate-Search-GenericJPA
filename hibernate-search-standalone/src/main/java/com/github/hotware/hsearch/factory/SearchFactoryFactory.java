package com.github.hotware.hsearch.factory;

import java.util.Collection;

import org.hibernate.search.cfg.spi.SearchConfiguration;
import org.hibernate.search.engine.integration.impl.ExtendedSearchIntegrator;
import org.hibernate.search.spi.SearchIntegrator;
import org.hibernate.search.spi.SearchIntegratorBuilder;

import com.github.hotware.hsearch.event.EventSource;

public final class SearchFactoryFactory {
	
	private SearchFactoryFactory() {
		throw new AssertionError("can't touch this!");
	}

	public static SearchFactory createSearchFactory(EventSource eventSource,
			SearchConfiguration searchConfiguration, Collection<Class<?>> classes) {
		SearchIntegratorBuilder builder = new SearchIntegratorBuilder();
		//we have to build an integrator here (but we don't need it afterwards)
		builder.configuration(searchConfiguration).buildSearchIntegrator();
		classes.forEach((clazz) -> {
			builder.addClass(clazz);
		});
		SearchIntegrator impl = builder.buildSearchIntegrator();
		SearchFactory factory = new SearchFactoryImpl(impl.unwrap(ExtendedSearchIntegrator.class));
		eventSource.setEventConsumer(factory);
		return factory;
	}

}