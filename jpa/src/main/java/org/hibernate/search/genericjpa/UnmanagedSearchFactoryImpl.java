/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.persistence.EntityManagerFactory;

import org.hibernate.search.genericjpa.db.events.UpdateConsumer;
import org.hibernate.search.genericjpa.db.events.UpdateSource;

/**
 * @author Martin Braun
 */
final class UnmanagedSearchFactoryImpl extends JPASearchFactory {

	private final EntityManagerFactory emf;
	private final Properties properties;
	private final UpdateConsumer updateConsumer;
	private final ScheduledExecutorService exec;
	private final UpdateSourceProvider updateSourceProvider;
	private final List<Class<?>> indexRootTypes;
	private final boolean useUserTransaction;

	private int updateDelay = 500;
	private int batchSizeForUpdates = 5;

	public UnmanagedSearchFactoryImpl(EntityManagerFactory emf, boolean useUserTransaction, List<Class<?>> indexRootTypes, Properties properties,
			UpdateConsumer updateConsumer, ScheduledExecutorService exec, UpdateSourceProvider updateSourceProvider) {
		this.emf = emf;
		this.useUserTransaction = useUserTransaction;
		this.indexRootTypes = indexRootTypes;
		this.properties = properties;
		this.updateConsumer = updateConsumer;
		this.exec = exec;
		this.updateSourceProvider = updateSourceProvider;
	}
	
	/**
	 * @return the updateDelay
	 */
	public int getUpdateDelay() {
		return this.updateDelay;
	}

	/**
	 * @param updateDelay the updateDelay to set
	 */
	public void setUpdateDelay(int updateDelay) {
		this.updateDelay = updateDelay;
	}

	/**
	 * @return the batchSizeForUpdates
	 */
	public int getBatchSizeForUpdates() {
		return this.batchSizeForUpdates;
	}

	/**
	 * @param batchSizeForUpdates the batchSizeForUpdates to set
	 */
	public void setBatchSizeForUpdates(int batchSizeForUpdates) {
		this.batchSizeForUpdates = batchSizeForUpdates;
	}

	@Override
	public void updateEvent(List<UpdateInfo> updateInfo) {
		if ( this.updateConsumer != null ) {
			this.updateConsumer.updateEvent( updateInfo );
		}
	}

	@Override
	protected EntityManagerFactory getEmf() {
		return this.emf;
	}

	@Override
	protected Properties getConfigProperties() {
		return this.properties;
	}

	@Override
	protected List<Class<?>> getIndexRootTypes() {
		return this.indexRootTypes;
	}

	@Override
	protected UpdateSource createUpdateSource() {
		return this.updateSourceProvider.getUpdateSource( this.updateDelay, TimeUnit.MILLISECONDS, this.batchSizeForUpdates, this.exec );
	}

	@Override
	protected boolean isUseUserTransaction() {
		return this.useUserTransaction;
	}

}
