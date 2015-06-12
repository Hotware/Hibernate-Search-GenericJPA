/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa;

import java.io.Closeable;

/**
 * This close intentionally doesn't have any methods. In order to use the Search capabilities in a JPA context use the
 * {@link org.hibernate.search.jpa.Search} class
 * 
 * @author Martin Braun
 */
public interface JPASearchFactory extends Closeable {

}
