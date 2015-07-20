/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.annotations;

import java.lang.annotation.Retention;

import org.hibernate.search.genericjpa.db.id.IdConverter;
import org.hibernate.search.genericjpa.exception.AssertionFailure;
import org.hibernate.search.genericjpa.exception.SearchException;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Created by Martin on 20.07.2015.
 */
@Retention(RUNTIME)
public @interface IdInfo {

	Class<?> entity() default void.class;

	String[] columns();

	String[] updateTableColumns() default {};

	IdType type() default IdType.NONE;

	Class<? extends IdConverter> idConverter() default IdConverter.class;

	Hint[] hints() default {};

	enum IdType implements IdConverter {
		STRING {
			@Override
			public Object convert(Object[] values, String[] fieldNames) {
				if ( values.length != 1 && fieldNames.length != 1 ) {
					throw new AssertionFailure( "values.length and fieldNames.length should be equal to 1" );
				}
				return String.valueOf(values[0]);
			}
		},
		INTEGER {
			@Override
			public Object convert(Object[] values, String[] fieldNames) {
				if ( values.length != 1 && fieldNames.length != 1 ) {
					throw new AssertionFailure( "values.length and fieldNames.length should be equal to 1" );
				}
				Object val = values[0];
				if(val instanceof Number) {
					return ((Number) val).intValue();
				} else {
					throw new SearchException( fieldNames[0] + " is no Number" );
				}
			}
		},
		LONG {
			@Override
			public Object convert(Object[] values, String[] fieldNames) {
				if ( values.length != 1 && fieldNames.length != 1 ) {
					throw new AssertionFailure( "values.length and fieldNames.length should be equal to 1" );
				}
				Object val = values[0];
				if(val instanceof Number) {
					return ((Number) val).longValue();
				} else {
					throw new SearchException( fieldNames[0] + " is no Number" );
				}
			}
		},
		NONE {
			@Override
			public Object convert(Object[] values, String[] fieldNames) {
				throw new AssertionFailure( this + " should not be used this way!" );
			}
		}
	}

}
