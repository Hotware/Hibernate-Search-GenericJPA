/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.events.impl;

import javax.persistence.EntityManager;

import java.util.concurrent.ConcurrentHashMap;

import org.hibernate.search.genericjpa.impl.FullTextEntityManagerImpl;

/**
 * Created by Martin on 27.07.2015.
 */
public interface SynchronizedUpdateSource {

	void close();

	/**
	 * @param entityManagerToFullTextEntityManager (shared instance)
	 */
	void setEntityManagerToFullTextEntityManager(ConcurrentHashMap<EntityManager, FullTextEntityManagerImpl> entityManagerToFullTextEntityManager);

}
