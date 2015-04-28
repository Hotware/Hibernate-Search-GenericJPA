/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package com.github.hotware.hsearch.factory;

import java.util.ArrayList;
import java.util.List;

import javax.transaction.Status;
import javax.transaction.Synchronization;

import com.github.hotware.hsearch.transaction.TransactionContext;

public class Transaction implements TransactionContext {

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
		syncs.add( synchronization );
	}

	public void end() {
		this.progress = false;
		for ( Synchronization sync : syncs ) {
			sync.beforeCompletion();
		}

		for ( Synchronization sync : syncs ) {
			sync.afterCompletion( Status.STATUS_COMMITTED );
		}
	}

}
