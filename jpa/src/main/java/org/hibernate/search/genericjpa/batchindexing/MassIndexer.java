/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.batchindexing;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * @author Martin Braun
 */
public interface MassIndexer {

	MassIndexer purgeAllOnStart(boolean purgeAllOnStart);

	MassIndexer optimizeAfterPurge(boolean optimizeAfterPurge);

	MassIndexer optimizeOnFinish(boolean optimizeOnFinish);

	MassIndexer batchSizeToLoadIds(int batchSizeToLoadIds);

	MassIndexer batchSizeToLoadObjects(int batchSizeToLoadObjects);

	MassIndexer threadsToLoadIds(int threadsToLoadIds);

	MassIndexer threadsToLoadObjects(int threadsToLoadObjects);

	MassIndexer executorService(ExecutorService executorService);

	MassIndexer executorServiceForIds(ExecutorService executorServiceForIds);

	MassIndexer executorServiceForObjects(ExecutorService executorServiceForObjects);

	MassIndexer createNewIdEntityManagerAfter(int createNewIdEntityManagerAfter);
	
	MassIndexer progressMonitor(MassIndexerProgressMonitor progressMonitor);

	Future<?> start();

	void startAndWait() throws InterruptedException;

}
