/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.hibernate.search.genericjpa.db.events.UpdateSource;


/**
 * @author Martin Braun
 */
interface UpdateSourceProvider {
	
	UpdateSource getUpdateSource(long delay, TimeUnit timeUnit, int batchSizeForUpdates, ScheduledExecutorService exec);

}
