/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Martin Braun
 */
public class SearchFactoryRegistry {

	public static String NAME_PROPERTY = "org.hibernate.search.genericjpa.searchfactory.name";
	public static String DEFAULT_NAME = "default";

	private SearchFactoryRegistry() {
		// can't touch this!
	}

	// FIXME: is this okay for multiple classloaders?

	private static Map<String, JPASearchFactory> searchFactories = new HashMap<>();

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static String getNameProperty(Map properties) {
		return (String) properties.getOrDefault( NAME_PROPERTY, DEFAULT_NAME );
	}

	public static JPASearchFactory getSearchFactory(String name) {
		return SearchFactoryRegistry.searchFactories.get( name );
	}

	static void setup(String name, JPASearchFactory searchFactory) {
		if ( !SearchFactoryRegistry.searchFactories.containsKey( name ) ) {
			SearchFactoryRegistry.searchFactories.put( name, searchFactory );
		}
	}

	static void unsetup(String name, JPASearchFactory searchFactory) {
		SearchFactoryRegistry.searchFactories.remove( name, searchFactory );
	}

}
