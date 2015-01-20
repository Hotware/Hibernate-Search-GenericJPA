/*
 * Copyright 2015 Martin Braun
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.hotware.hsearch.db.events.annotations;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import com.github.hotware.hsearch.db.id.DefaultToOriginalIdBridge;
import com.github.hotware.hsearch.db.id.ToOriginalIdBridge;

/**
 * @author Martin
 */
@Target({ FIELD, METHOD })
@Retention(RUNTIME)
public @interface IdFor {

	/**
	 * this is used to determine which type has to be updated. This is needed so
	 * the EventSource can supply a valid Class to an EntityProvider and pass
	 * this into the corresponding methods in the SearchFactory
	 * 
	 * the class returned has to be annotated with @InIndex
	 */
	public Class<?> entityClass();

	/**
	 * @return the column names of the id in the Updates table.<br>
	 *         <b>has to be in the same order as columnsInOriginal</b>
	 */
	public String[] columns();

	/**
	 * @return the column names of the id in the original table.<br>
	 *         <b>has to be in the same order as columns</b>
	 */
	public String[] columnsInOriginal();
	
	public Class<? extends ToOriginalIdBridge> bridge() default DefaultToOriginalIdBridge.class;

}
