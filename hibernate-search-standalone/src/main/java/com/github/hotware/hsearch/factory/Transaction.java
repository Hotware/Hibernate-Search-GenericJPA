/*
 * Copyright 2015 Martin Braun
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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