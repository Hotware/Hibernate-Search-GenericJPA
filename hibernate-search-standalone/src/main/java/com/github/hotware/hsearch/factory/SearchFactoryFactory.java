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