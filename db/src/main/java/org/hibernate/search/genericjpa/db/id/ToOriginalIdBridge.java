/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.db.id;

/**
 * interface used to specify a custom bridge between the id used in an Update class to the original entity's id.
 * <br>
 * <br>
 * If your Update class has the same id columnTypes, you probably won't need this
 *
 * @author Martin Braun
 */
public interface ToOriginalIdBridge {

	Object toOriginal(Object object);

}
