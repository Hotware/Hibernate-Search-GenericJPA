package com.github.hotware.lucene.extension.hsearch.event;

import java.util.Arrays;

import com.github.hotware.lucene.extension.hsearch.transaction.TransactionContext;
import com.github.hotware.lucene.extension.hsearch.factory.TransactionContextImpl;

public interface EventConsumer {

	public void index(Iterable<Object> entities, TransactionContext tc);

	public default void index(Iterable<Object> entities) {
		TransactionContextImpl tc = new TransactionContextImpl();
		this.index(entities, tc);
		tc.end();
	}

	public default void index(Object entity) {
		this.index(Arrays.asList(entity));
	}

	public void update(Iterable<Object> entities, TransactionContext tc);

	public default void update(Iterable<Object> entities) {
		TransactionContextImpl tc = new TransactionContextImpl();
		this.update(entities, tc);
		tc.end();
	}

	public default void update(Object entity) {
		this.update(Arrays.asList(entity));
	}

	public void delete(Iterable<Object> entities, TransactionContext tc);

	public default void delete(Iterable<Object> entities) {
		TransactionContextImpl tc = new TransactionContextImpl();
		this.delete(entities, tc);
		tc.end();
	}

	public default void delete(Object entity) {
		this.delete(Arrays.asList(entity));
	}

	public void purgeAll(Class<?> entityClass);

}
