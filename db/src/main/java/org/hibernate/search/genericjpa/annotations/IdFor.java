/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.hibernate.search.genericjpa.db.id.DefaultToOriginalIdBridge;
import org.hibernate.search.genericjpa.db.id.ToOriginalIdBridge;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * @author Martin
 */
@Target({FIELD, METHOD})
@Retention(RUNTIME)
public @interface IdFor {

	/**
	 * this is used to determine which columnTypes has to be updated. This is needed so the EventSource can supply a valid
	 * Class to an EntityProvider and pass this into the corresponding methods in the SearchFactory <br>
	 * <br>
	 * the class returned has to be annotated with @InIndex
	 */
	Class<?> entityClass();

	/**
	 * @return the column names of the id in the Updates table.<br>
	 * <b>has to be in the same order as columnsInOriginal</b>
	 */
	String[] columns();

	/**
	 * @return the column names of the id in the original table.<br>
	 * <b>has to be in the same order as columns</b>
	 */
	String[] columnsInOriginal();

	Class<? extends ToOriginalIdBridge> bridge() default DefaultToOriginalIdBridge.class;

	Hint[] hints() default {};

}
