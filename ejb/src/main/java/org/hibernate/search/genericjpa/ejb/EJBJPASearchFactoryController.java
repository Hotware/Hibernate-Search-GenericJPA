/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.ejb;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.enterprise.concurrent.ManagedScheduledExecutorService;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;

import org.hibernate.search.SearchFactory;
import org.hibernate.search.genericjpa.JPASearchFactoryController;
import org.hibernate.search.genericjpa.Setup;
import org.hibernate.search.genericjpa.db.events.UpdateConsumer;
import org.hibernate.search.genericjpa.exception.SearchException;
import org.hibernate.search.jpa.FullTextEntityManager;

@Startup
@Singleton
public class EJBJPASearchFactoryController implements JPASearchFactoryController {

	private static final Logger LOGGER = Logger.getLogger( EJBJPASearchFactoryController.class.getName() );

	private static final String PROPERTIES_PATH = "/META-INF/hsearch.properties";

	@Resource
	private ManagedScheduledExecutorService exec;

	@PersistenceUnit
	private EntityManagerFactory emf;

	private JPASearchFactoryController jpaSearchFactoryController;

	private Set<UpdateConsumer> updateConsumers = new HashSet<>();

	private final Lock lock = new ReentrantLock();

	@PostConstruct
	public void start() {
		Properties properties = new Properties();
		try (InputStream is = EJBJPASearchFactoryController.class.getResource( PROPERTIES_PATH ).openStream()) {
			properties.load( is );
		}
		catch (IOException e) {
			throw new SearchException( "couldn't load hibernate-search specific properties from: " + PROPERTIES_PATH );
		}
		this.jpaSearchFactoryController = Setup.createSearchFactory( this.emf, properties, (updateInfos) -> {
			this.lock.lock();
			try {
				for ( UpdateConsumer consumer : this.updateConsumers ) {
					try {
						consumer.updateEvent( updateInfos );
					}
					catch (Exception e) {
						LOGGER.log( Level.WARNING, "Exception in user-provided UpdateConsumer", e );
					}
				}
			}
			finally {
				this.lock.unlock();
			}
		}, this.exec );
	}

	@PreDestroy
	public void stop() {
		try {
			this.jpaSearchFactoryController.close();
		}
		catch (IOException e) {
			throw new SearchException( e );
		}
	}

	public void addUpdateConsumer(UpdateConsumer updateConsumer) {
		this.lock.lock();
		try {
			this.updateConsumers.add( updateConsumer );
		}
		finally {
			this.lock.unlock();
		}
	}

	public void removeUpdateConsumer(UpdateConsumer updateConsumer) {
		this.lock.lock();
		try {
			this.updateConsumers.remove( updateConsumer );
		}
		finally {
			this.lock.unlock();
		}
	}

	@Override
	public void close() throws IOException {
		// no-op
	}

	@Override
	public SearchFactory getSearchFactory() {
		if ( this.jpaSearchFactoryController != null ) {
			return this.jpaSearchFactoryController.getSearchFactory();
		}
		return null;
	}

	@Override
	public void pauseUpdating(boolean pause) {
		if ( this.jpaSearchFactoryController != null ) {
			this.jpaSearchFactoryController.pauseUpdating( pause );
		}
	}

	@Override
	public FullTextEntityManager getFullTextEntityManager(EntityManager em) {
		if ( this.jpaSearchFactoryController != null ) {
			return this.jpaSearchFactoryController.getFullTextEntityManager( em );
		}
		return null;
	}

}
