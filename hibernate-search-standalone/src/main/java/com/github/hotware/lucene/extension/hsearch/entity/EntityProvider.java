package com.github.hotware.lucene.extension.hsearch.entity;

import java.io.Closeable;

public interface EntityProvider extends Closeable {
	
	public <T> T get(Class<T> entityClass, Object id);

}
