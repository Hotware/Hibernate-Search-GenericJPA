/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.jpa.util;

import java.util.concurrent.ThreadFactory;

/**
 * @author Martin Braun
 */
public final class NamingThreadFactory implements ThreadFactory {

	private final String name;
	private int threadCount = 0;

	public NamingThreadFactory(String name) {
		this.name = name;
	}

	@Override
	public Thread newThread(Runnable r) {
		return new Thread(
				r,
				new StringBuilder().append( this.name ).append( " " ).append( this.threadCount++ ).toString()
		);
	}

}
