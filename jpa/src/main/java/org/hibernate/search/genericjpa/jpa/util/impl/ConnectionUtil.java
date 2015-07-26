/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.jpa.util.impl;

import javax.persistence.EntityManager;
import java.sql.Connection;

/**
 * Created by Martin on 22.07.2015.
 */
public class ConnectionUtil {

	public static Connection getConnectionFromEntityManager(EntityManager em) {
		Connection connection = null;
		return connection;
	}

}
