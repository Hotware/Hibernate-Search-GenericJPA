package com.github.hotware.hsearch.entity;

import java.io.Closeable;
import java.util.List;

/**
 * Hibernate-Search is no object storage. All hits found on the Index have a
 * original representation. This interface provides means to retrieve these when
 * executing a {@link com.github.hotware.hsearch.query.HSearchQuery}
 * 
 * @author Martin Braun
 */
public interface EntityProvider extends Closeable {

	public <T> T get(Class<T> entityClass, Object id);

	public <T> List<T> getBatch(Class<T> entityClass, List<Object> id);

}
