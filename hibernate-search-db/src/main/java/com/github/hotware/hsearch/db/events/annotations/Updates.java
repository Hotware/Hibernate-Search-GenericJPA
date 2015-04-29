/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package com.github.hotware.hsearch.db.events.annotations;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * @author Martin
 */
@Target({ TYPE })
@Retention(RUNTIME)
public @interface Updates {

	/**
	 * @return the name of the updates-table
	 */
	String tableName();

	/**
	 * @return the name of the table the updates correspond to
	 */
	String originalTableName();

}
