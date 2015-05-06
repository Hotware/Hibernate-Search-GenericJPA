/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.entity;

import java.io.Closeable;
import java.util.List;

/**
 * Hibernate-Search is no object storage. All hits found on the Index have a original representation. This interface
 * provides means to retrieve these when executing a {@link org.hibernate.search.standalone.query.HSearchQuery}
 *
 * @author Martin Braun
 */
public interface EntityProvider extends Closeable {

	Object get(Class<?> entityClass, Object id);

	/**
	 * ATTENTION: ORDER IS NOT PRESERVED!
	 */
	@SuppressWarnings("rawtypes")
	List getBatch(Class<?> entityClass, List<Object> id);

}
