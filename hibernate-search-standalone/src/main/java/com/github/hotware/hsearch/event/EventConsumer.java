package com.github.hotware.hsearch.event;

import java.util.Arrays;

import com.github.hotware.hsearch.factory.TransactionContextImpl;
import com.github.hotware.hsearch.transaction.TransactionContext;

public interface EventConsumer {

	public void index(Iterable<?> entities, TransactionContext tc);
	
	public default void index( Object entity, TransactionContext tc) {
		this.index(Arrays.asList(entity), tc);
	}

	public default void index( Iterable<?> entities) {
		TransactionContextImpl tc = new TransactionContextImpl();
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
		TransactionContextImpl tc = new TransactionContextImpl();
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
		TransactionContextImpl tc = new TransactionContextImpl();
		this.delete(entities, tc);
		tc.end();
	}

	public default void delete( Object entity) {
		this.delete(Arrays.asList(entity));
	}

	public void purgeAll(Class<?> entityClass);

}
