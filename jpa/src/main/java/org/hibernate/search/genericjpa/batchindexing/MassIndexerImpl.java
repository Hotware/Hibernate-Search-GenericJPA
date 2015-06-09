/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.batchindexing;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.hibernate.search.engine.integration.impl.ExtendedSearchIntegrator;
import org.hibernate.search.genericjpa.exception.SearchException;
import org.hibernate.search.standalone.metadata.RehashedTypeMetadata;

/**
 * @author Martin Braun
 */
public class MassIndexerImpl implements MassIndexer {

	private final Map<Class<?>, RehashedTypeMetadata> metadataPerForIndexRoot;
	private final Map<Class<?>, List<Class<?>>> containedInIndexOf;
	private final ExtendedSearchIntegrator searchIntegrator;
	private final List<Class<?>> rootEntities;
	private final boolean useUserTransaction;

	private ExecutorService executorServiceForIds;
	private ExecutorService executorServiceForObjects;

	private boolean purgeAllOnStart = true;
	private boolean optimizeAfterPurge = true;
	private boolean optimizeOnFinish = true;
	private int batchSizeToLoadIds = 100;
	private int batchSizeToLoadObjects = 10;
	private int threadsToLoadIds = 2;
	private int threadsToLoadObjects = 4;

	public MassIndexerImpl(Map<Class<?>, RehashedTypeMetadata> metadataPerForIndexRoot, Map<Class<?>, List<Class<?>>> containedInIndexOf,
			ExtendedSearchIntegrator searchIntegrator, List<Class<?>> rootEntities, boolean useUserTransaction) {
		this.metadataPerForIndexRoot = metadataPerForIndexRoot;
		this.containedInIndexOf = containedInIndexOf;
		this.searchIntegrator = searchIntegrator;
		this.rootEntities = rootEntities;
		this.useUserTransaction = useUserTransaction;
	}

	@Override
	public MassIndexer purgeAllOnStart(boolean purgeAllOnStart) {
		this.purgeAllOnStart = purgeAllOnStart;
		return this;
	}

	@Override
	public MassIndexer optimizeAfterPurge(boolean optimizeAfterPurge) {
		this.optimizeAfterPurge = optimizeAfterPurge;
		return this;
	}

	@Override
	public MassIndexer optimizeOnFinish(boolean optimizeOnFinish) {
		this.optimizeOnFinish = optimizeOnFinish;
		return this;
	}

	@Override
	public MassIndexer batchSizeToLoadIds(int batchSizeToLoadIds) {
		this.batchSizeToLoadIds = batchSizeToLoadIds;
		return this;
	}

	@Override
	public MassIndexer batchSizeToLoadObjects(int batchSizeToLoadObjects) {
		this.batchSizeToLoadObjects = batchSizeToLoadObjects;
		return this;
	}

	@Override
	public MassIndexer threadsToLoadIds(int threadsToLoadIds) {
		this.threadsToLoadIds = threadsToLoadIds;
		return this;
	}

	@Override
	public MassIndexer threadsToLoadObjects(int threadsToLoadObjects) {
		this.threadsToLoadObjects = threadsToLoadObjects;
		return this;
	}

	@Override
	public MassIndexer executorService(ExecutorService executorService) {
		if ( this.executorServiceForIds != null || this.executorServiceForObjects != null ) {
			throw new IllegalStateException( "already started!" );
		}
		this.executorServiceForIds = executorService;
		this.executorServiceForObjects = executorService;
		return this;
	}

	@Override
	public Future<?> start() {
		if ( this.executorServiceForIds != null ) {
			this.executorServiceForIds = Executors.newFixedThreadPool( this.threadsToLoadIds );
		}
		if ( this.executorServiceForObjects != null ) {
			this.executorServiceForObjects = Executors.newFixedThreadPool( this.threadsToLoadObjects );
		}
		return null;
	}

	@Override
	public void startAndWait() throws InterruptedException {
		try {
			this.start().get();
		}
		catch (ExecutionException e) {
			throw new SearchException( e );
		}
	}

}
