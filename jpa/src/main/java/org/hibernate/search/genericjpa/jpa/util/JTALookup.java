/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.jpa.util;

import javax.transaction.TransactionManager;

import org.hibernate.search.genericjpa.exception.SearchException;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.transaction.lookup.GenericTransactionManagerLookup;
import org.infinispan.transaction.tm.DummyTransactionManager;

public class JTALookup {

	private JTALookup() {
		// can't touch this!
	}

	public static TransactionManager lookup() {
		TransactionManager ret = Holder.INSTANCE.getTransactionManager();
		if ( ret instanceof DummyTransactionManager ) {
			throw new SearchException( "couldn't lookup TransactionManager!" );
		}
		return ret;
	}

	private static class Holder {

		private static final GenericTransactionManagerLookup INSTANCE = new GenericTransactionManagerLookup();
		static {
			GlobalConfigurationBuilder cfgBuilder = new GlobalConfigurationBuilder();
			INSTANCE.init( cfgBuilder.build() );
		}
	}

}
