/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa;

import org.hibernate.search.genericjpa.impl.SearchFactoryRegistry;

/**
 * @author Martin Braun
 */
public class Constants {

	private Constants() {
		// can't touch this!
	}

	public static final String USE_USER_TRANSACTIONS_KEY = "org.hibernate.search.genericjpa.searchfactory.useUserTransactions";
	public static final String USE_USER_TRANSACTIONS_DEFAULT_VALUE = "false";

	public static final String SEARCH_FACTORY_TYPE_KEY = "org.hibernate.search.genericjpa.searchfactory.type";
	public static final String SEARCH_FACTORY_TYPE_DEFAULT_VALUE = "sql";

	public static final String BATCH_SIZE_FOR_UPDATES_KEY = "org.hibernate.search.genericjpa.searchfactory.batchSizeForUpdates";
	public static final String BATCH_SIZE_FOR_UPDATES_DEFAULT_VALUE = "5";

	public static final String UPDATE_DELAY_KEY = "org.hibernate.search.genericjpa.searchfactory.updateDelay";
	public static final String UPDATE_DELAY_DEFAULT_VALUE = "500";

	public static final String TRIGGER_SOURCE_KEY = "org.hibernate.search.genericjpa.searchfactory.triggerSource";

	public static final String SEARCH_FACTORY_NAME_KEY = SearchFactoryRegistry.NAME_PROPERTY;
	public static final String SEARCH_FACTORY_NAME_DEFAULT_VALUE = SearchFactoryRegistry.DEFAULT_NAME;

}
