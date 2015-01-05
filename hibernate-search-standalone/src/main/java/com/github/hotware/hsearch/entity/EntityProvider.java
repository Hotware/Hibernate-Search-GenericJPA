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

	public Object get(Class<?> entityClass, Object id);

	public List<Object> getBatch(Class<?> entityClass, List<Object> id);

}
