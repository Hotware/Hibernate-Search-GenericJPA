/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.test.searchFactory;

import java.sql.Connection;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.enterprise.concurrent.ManagedScheduledExecutorService;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;

import org.hibernate.internal.SessionImpl;
import org.hibernate.search.exception.AssertionFailure;
import org.hibernate.search.genericjpa.SQLJPASearchFactory;
import org.hibernate.search.genericjpa.db.events.MySQLTriggerSQLStringSource;
import org.hibernate.search.genericjpa.db.events.TriggerSQLStringSource;
import org.hibernate.search.genericjpa.test.entities.Game;
import org.hibernate.search.genericjpa.test.entities.GameUpdates;
import org.hibernate.search.genericjpa.test.entities.GameVendorUpdates;
import org.hibernate.search.genericjpa.test.entities.VendorUpdates;
import org.hibernate.search.jpa.Search;

@Singleton
@Startup
public class EJBSearchFactory extends SQLJPASearchFactory {

	@Resource
	private ManagedScheduledExecutorService exec;

	@PersistenceUnit
	private EntityManagerFactory emf;

	@PostConstruct
	public void startup() {
		super.init();
		Search.setup( this );
	}

	@PreDestroy
	public void shutdown() {
		super.shutdown();
	}

	@Override
	public void updateEvent(List<UpdateInfo> arg0) {

	}

	@Override
	protected int getBatchSizeForUpdates() {
		return 2;
	}

	@Override
	protected String getConfigFile() {
		return "/hsearch.properties";
	}

	@Override
	protected long getDelay() {
		return 100;
	}

	@Override
	protected TimeUnit getDelayUnit() {
		return TimeUnit.MILLISECONDS;
	}

	@Override
	protected EntityManagerFactory getEmf() {
		return this.emf;
	}

	@Override
	protected List<Class<?>> getIndexRootTypes() {
		return Arrays.asList( Game.class );
	}

	@Override
	protected TriggerSQLStringSource getTriggerSQLStringSource() {
		return new MySQLTriggerSQLStringSource();
	}

	@Override
	protected List<Class<?>> getUpdateClasses() {
		return Arrays.asList( GameUpdates.class, VendorUpdates.class, GameVendorUpdates.class );
	}

	@Override
	protected boolean isUseJTATransaction() {
		return true;
	}

	@Override
	protected ScheduledExecutorService getExecutorServiceForUpdater() {
		return this.exec;
	}

	@Override
	protected Connection getConnectionForSetup(EntityManager em) {
		if ( em instanceof org.eclipse.persistence.internal.jpa.EntityManagerImpl ) {
			return em.unwrap( Connection.class );
		}
		else if ( em instanceof org.hibernate.jpa.internal.EntityManagerImpl ) {
			return ( (SessionImpl) ( (org.hibernate.jpa.internal.EntityManagerImpl) em ).getSession() ).connection();
		}
		throw new AssertionFailure("unrecognized EntityManager implementation");
	}

}
