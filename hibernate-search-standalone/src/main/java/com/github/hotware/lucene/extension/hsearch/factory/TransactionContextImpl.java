package com.github.hotware.lucene.extension.hsearch.factory;

import java.util.ArrayList;
import java.util.List;

import javax.transaction.Status;
import javax.transaction.Synchronization;

import com.github.hotware.lucene.extension.hsearch.transaction.TransactionContext;

public class TransactionContextImpl implements TransactionContext {
	
	private boolean progress = true;
	private List<Synchronization> syncs = new ArrayList<Synchronization>();

	@Override
	public boolean isTransactionInProgress() {
		return progress;
	}

	@Override
	public Object getTransactionIdentifier() {
		return this;
	}

	@Override
	public void registerSynchronization(Synchronization synchronization) {
		syncs.add(synchronization);
	}

	public void end() {
		this.progress = false;
		for (Synchronization sync : syncs) {
			sync.beforeCompletion();
		}

		for (Synchronization sync : syncs) {
			sync.afterCompletion(Status.STATUS_COMMITTED);
		}
	}
	
}