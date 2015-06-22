/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.transaction.impl;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.transaction.TransactionManager;
import java.util.Map;

import org.hibernate.search.genericjpa.exception.SearchException;
import org.hibernate.search.genericjpa.transaction.TransactionManagerProvider;

import static org.hibernate.search.genericjpa.Constants.TRANSACTION_MANAGER_JNDI_KEY;

/**
 * Created by Martin on 22.06.2015.
 */
public class JNDILookupTransactionmanagerProvider implements TransactionManagerProvider {

	@Override
	public TransactionManager get(ClassLoader classLoader, Map properties) {
		String jndiName = (String) properties.get( TRANSACTION_MANAGER_JNDI_KEY );
		if ( jndiName == null ) {
			throw new SearchException( TRANSACTION_MANAGER_JNDI_KEY + " must not be null if using: " + JNDILookupTransactionmanagerProvider.class );
		}
		TransactionManager ret = null;
		try {
			ret = InitialContext.doLookup( jndiName );
		}
		catch (NamingException e) {
			throw new SearchException( "error while looking up " + jndiName );
		}
		if ( ret == null ) {
			throw new SearchException( jndiName + "was not found!" );
		}
		return ret;
	}

}
