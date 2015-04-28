/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package com.github.hotware.hsearch.db.tableInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Martin Braun
 */
public interface TableInfoSource {

	public default List<TableInfo> getTableInfos(Set<Class<?>> classesInIndex) {
		List<Class<?>> list = new ArrayList<>( classesInIndex.size() );
		list.addAll( classesInIndex );
		return this.getTableInfos( list );
	}

	public List<TableInfo> getTableInfos(List<Class<?>> classesInIndex);

}
