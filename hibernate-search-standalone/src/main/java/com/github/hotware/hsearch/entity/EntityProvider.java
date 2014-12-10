package com.github.hotware.hsearch.entity;

import java.io.Closeable;
import java.util.List;

public interface EntityProvider extends Closeable {
	
	public <T> T get(Class<T> entityClass, Object id);
	
	public <T> List<T> getBatch(Class<T> entityClass, List<Object> id);

}
