/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.test.searchFactory;

import java.util.Properties;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.enterprise.concurrent.ManagedScheduledExecutorService;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;

import org.hibernate.search.genericjpa.JPASearchFactory;
import org.hibernate.search.genericjpa.Setup;
import org.hibernate.search.genericjpa.db.events.MySQLTriggerSQLStringSource;

@Singleton
@Startup
public class EJBSearchFactory {

	@Resource
	private ManagedScheduledExecutorService exec;

	@PersistenceUnit
	private EntityManagerFactory emf;

	private JPASearchFactory searchFactory;

	@PostConstruct
	public void startup() {
		Properties properties = new Properties();
		properties.setProperty( "org.hibernate.search.genericjpa.searchfactory.triggerSource", MySQLTriggerSQLStringSource.class.getName() );
		properties.setProperty( "org.hibernate.search.genericjpa.searchfactory.type", "sql" );
		properties.setProperty( "org.hibernate.search.genericjpa.searchfactory.batchsizeForUpdates", "2" );
		properties.setProperty( "org.hibernate.search.genericjpa.searchfactory.updateDelay", "100" );
		this.searchFactory = Setup.createSearchFactory( this.emf, true, properties, null, this.exec );
	}

	@PreDestroy
	public void shutdown() {
		this.searchFactory.shutdown();
	}

}
