/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package com.github.hotware.hsearch.db.events;

import java.util.List;

/**
 * Source for updates on entities. This does no hierarchy checks, it just delivers information about which entry in
 * which table has changed
 * 
 * @author Martin Braun
 */
public interface UpdateSource {

	public void setUpdateConsumers(List<UpdateConsumer> updateConsumers);

	public void start();

	public void stop();

}
