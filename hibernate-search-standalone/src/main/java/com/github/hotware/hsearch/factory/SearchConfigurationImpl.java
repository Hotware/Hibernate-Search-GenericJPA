/*
 * originally from Hibernate Search:
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root (of Hibernate-Search)
 * directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
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
package com.github.hotware.hsearch.factory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import org.apache.lucene.util.Version;
import org.hibernate.annotations.common.reflection.ReflectionManager;
import org.hibernate.annotations.common.reflection.java.JavaReflectionManager;
import org.hibernate.search.cfg.SearchMapping;
import org.hibernate.search.cfg.spi.SearchConfiguration;
import org.hibernate.search.cfg.spi.SearchConfigurationBase;
import org.hibernate.search.engine.service.classloading.impl.DefaultClassLoaderService;
import org.hibernate.search.engine.service.classloading.spi.ClassLoaderService;
import org.hibernate.search.engine.service.spi.Service;
import org.hibernate.search.spi.InstanceInitializer;

/**
 * Manually defines the configuration.
 *
 * Classes and properties are the only implemented options at the moment
 *
 * @author Martin Braun (adaption), Emmanuel Bernard
 */
public class SearchConfigurationImpl extends SearchConfigurationBase implements
		SearchConfiguration {

	private final Logger LOGGER = Logger
			.getLogger(SearchConfigurationImpl.class.getName());
	private final Map<String, Class<?>> classes;
	private final Properties properties;
	private final HashMap<Class<? extends Service>, Object> providedServices;
	private final InstanceInitializer initializer;
	private SearchMapping programmaticMapping;
	private boolean transactionsExpected = true;
	//TODO: is this correct?
	private boolean indexMetadataComplete = true;
	private boolean idProvidedImplicit = false;
	private ClassLoaderService classLoaderService;
	private ReflectionManager reflectionManager;

	public SearchConfigurationImpl() {
		this(new Properties());
	}

	public SearchConfigurationImpl(Properties properties) {
		this(SubClassSupportInstanceInitializer.INSTANCE, properties);
	}

	public SearchConfigurationImpl(InstanceInitializer init) {
		this(new Properties());
	}

	public SearchConfigurationImpl(InstanceInitializer init,
			Properties properties) {
		this.initializer = init;
		this.classes = new HashMap<String, Class<?>>();
		this.properties = properties;
		// default values if nothing was explicitly set
		this.properties.computeIfAbsent(
				"hibernate.search.default.directory_provider", (key) -> {
					LOGGER.info("defaulting to RAM directory-provider");
					return "ram";
				});
		this.properties.computeIfAbsent(
				"hibernate.search.lucene_version",
				(key) -> {
					LOGGER.info("defaulting to Lucene Version: "
							+ Version.LUCENE_4_10_2.toString());
					return Version.LUCENE_4_10_2.toString();
				});
		this.reflectionManager = new JavaReflectionManager();
		this.providedServices = new HashMap<Class<? extends Service>, Object>();
		this.classLoaderService = new DefaultClassLoaderService();
	}

	public SearchConfigurationImpl addProperty(String key, String value) {
		properties.setProperty(key, value);
		return this;
	}

	public SearchConfigurationImpl addClass(Class<?> indexed) {
		classes.put(indexed.getName(), indexed);
		return this;
	}

	@Override
	public Iterator<Class<?>> getClassMappings() {
		return classes.values().iterator();
	}

	@Override
	public Class<?> getClassMapping(String name) {
		return classes.get(name);
	}

	@Override
	public String getProperty(String propertyName) {
		return properties.getProperty(propertyName);
	}

	@Override
	public Properties getProperties() {
		return properties;
	}

	@Override
	public ReflectionManager getReflectionManager() {
		return this.reflectionManager;
	}

	@Override
	public SearchMapping getProgrammaticMapping() {
		return programmaticMapping;
	}

	public SearchConfigurationImpl setProgrammaticMapping(
			SearchMapping programmaticMapping) {
		this.programmaticMapping = programmaticMapping;
		return this;
	}

	@Override
	public Map<Class<? extends Service>, Object> getProvidedServices() {
		return providedServices;
	}

	public void addProvidedService(Class<? extends Service> serviceRole,
			Object service) {
		providedServices.put(serviceRole, service);
	}

	@Override
	public boolean isTransactionManagerExpected() {
		return this.transactionsExpected;
	}

	public void setTransactionsExpected(boolean transactionsExpected) {
		this.transactionsExpected = transactionsExpected;
	}

	@Override
	public InstanceInitializer getInstanceInitializer() {
		return initializer;
	}

	@Override
	public boolean isIndexMetadataComplete() {
		return indexMetadataComplete;
	}

	public void setIndexMetadataComplete(boolean indexMetadataComplete) {
		this.indexMetadataComplete = indexMetadataComplete;
	}

	@Override
	public boolean isIdProvidedImplicit() {
		return idProvidedImplicit;
	}

	public SearchConfigurationImpl setIdProvidedImplicit(
			boolean idProvidedImplicit) {
		this.idProvidedImplicit = idProvidedImplicit;
		return this;
	}

	@Override
	public ClassLoaderService getClassLoaderService() {
		return classLoaderService;
	}

	public void setClassLoaderService(ClassLoaderService classLoaderService) {
		this.classLoaderService = classLoaderService;
	}

}