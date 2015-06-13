/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.batchindexing;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;


/**
 * @author Martin Braun
 */
public interface MassIndexer {
	
	public MassIndexer purgeAllOnStart(boolean purgeAllOnStart);
	
	public MassIndexer optimizeAfterPurge(boolean optimizeAfterPurge);
	
	public MassIndexer optimizeOnFinish(boolean optimizeOnFinish);
	
	public MassIndexer batchSizeToLoadIds(int batchSizeToLoadIds);
	
	public MassIndexer batchSizeToLoadObjects(int batchSizeToLoadObjects);
	
	public MassIndexer threadsToLoadIds(int threadsToLoadIds);
	
	public MassIndexer threadsToLoadObjects(int threadsToLoadObjects);
	
	public MassIndexer executorService(ExecutorService executorService);
	
	public MassIndexer createNewIdEntityManagerAfter(int createNewIdEntityManagerAfter);
	
	public Future<?> start();
	
	public void startAndWait() throws InterruptedException;

}
