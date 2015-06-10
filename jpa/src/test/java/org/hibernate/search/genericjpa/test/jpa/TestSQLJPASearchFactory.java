/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.test.jpa;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.persistence.EntityManagerFactory;

import org.hibernate.search.genericjpa.SQLJPASearchFactory;
import org.hibernate.search.genericjpa.db.events.MySQLTriggerSQLStringSource;
import org.hibernate.search.genericjpa.db.events.TriggerSQLStringSource;
import org.hibernate.search.genericjpa.test.jpa.entities.Place;
import org.hibernate.search.genericjpa.test.jpa.entities.PlaceSorcererUpdates;
import org.hibernate.search.genericjpa.test.jpa.entities.PlaceUpdates;
import org.hibernate.search.genericjpa.test.jpa.entities.SorcererUpdates;

/**
 * @author Martin Braun
 */
public class TestSQLJPASearchFactory extends SQLJPASearchFactory {

	private final EntityManagerFactory emf;
	private final ScheduledExecutorService exec;

	public TestSQLJPASearchFactory(EntityManagerFactory emf) {
		this.emf = emf;
		this.exec = Executors.newSingleThreadScheduledExecutor();
	}
	
	public void start() {
		super.init();
	}
	
	public void shutdown() {
		this.exec.shutdown();
		super.shutdown();
	}

	@Override
	public void updateEvent(List<UpdateInfo> updateInfo) {

	}

	@Override
	protected TriggerSQLStringSource getTriggerSQLStringSource() {
		return new MySQLTriggerSQLStringSource();
	}

	@Override
	protected EntityManagerFactory getEmf() {
		return this.emf;
	}


	@Override
	protected List<Class<?>> getIndexRootTypes() {
		return Arrays.asList( Place.class );
	}

	@Override
	protected List<Class<?>> getUpdateClasses() {
		return Arrays.asList( PlaceUpdates.class, PlaceSorcererUpdates.class, SorcererUpdates.class );
	}

	@Override
	protected TimeUnit getDelayUnit() {
		return TimeUnit.MILLISECONDS;
	}

	@Override
	protected long getDelay() {
		return 100;
	}

	@Override
	protected int getBatchSizeForUpdates() {
		return 2;
	}

	@Override
	protected ScheduledExecutorService getExecutorServiceForUpdater() {
		return this.exec;
	}

	@Override
	protected boolean isUseUserTransaction() {
		return false;
	}

	@Override
	protected Properties getConfigProperties() {
		return new Properties();
	}

}
