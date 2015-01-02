/*
 * Copyright 2015 Martin Braun
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.hotware.hsearch.event;

import java.util.Arrays;

import com.github.hotware.hsearch.factory.Transaction;
import com.github.hotware.hsearch.transaction.TransactionContext;

public interface EventConsumer {

	public void index(Iterable<?> entities, TransactionContext tc);
	
	public default void index( Object entity, TransactionContext tc) {
		this.index(Arrays.asList(entity), tc);
	}

	public default void index( Iterable<?> entities) {
		Transaction tc = new Transaction();
		this.index(entities, tc);
		tc.end();
	}

	public default void index( Object entity) {
		this.index(Arrays.asList(entity));
	}

	public void update( Iterable<?> entities, TransactionContext tc);
	
	public default void update( Object entity, TransactionContext tc) {
		this.update(Arrays.asList(entity), tc);
	}

	public default void update( Iterable<?> entities) {
		Transaction tc = new Transaction();
		this.update(entities, tc);
		tc.end();
	}

	public default void update( Object entity) {
		this.update(Arrays.asList(entity));
	}

	public void delete( Iterable<?> entities, TransactionContext tc);

	public default void delete( Object entity, TransactionContext tc) {
		this.delete(Arrays.asList(entity), tc);
	}
	
	public default void delete( Iterable<?> entities) {
		Transaction tc = new Transaction();
		this.delete(entities, tc);
		tc.end();
	}

	public default void delete( Object entity) {
		this.delete(Arrays.asList(entity));
	}

	public void purgeAll(Class<?> entityClass);

}
