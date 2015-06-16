/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.batchindexing.impl;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Martin Braun
 */
public class NumberCondition {

	private final Lock lock = new ReentrantLock();
	private final Condition condition = this.lock.newCondition();

	private final int lockCount;
	private int count = 0;
	
	private boolean disable;

	public NumberCondition(int lockCount) {
		this.lockCount = lockCount;
	}

	public void up(int count) {
		this.lock.lock();
		try {
			this.count += count;
		}
		finally {
			this.lock.unlock();
		}
	}

	public void down(int down) {
		this.lock.lock();
		try {
			while(--down >= 0) {
				if ( --this.count < this.lockCount ) {
					this.condition.signal();
				}
			}
		}
		finally {
			this.lock.unlock();
		}
	}

	public void check() throws InterruptedException {
		this.lock.lock();
		try {
			if ( this.count == this.lockCount && !this.disable ) {
				this.condition.await();
			}
		}
		finally {
			this.lock.unlock();
		}
	}
	
	public void disable() {
		this.lock.lock();
		try {
			this.disable = true;
			this.condition.signalAll();
		}
		finally {
			this.lock.unlock();
		}
	}

}
