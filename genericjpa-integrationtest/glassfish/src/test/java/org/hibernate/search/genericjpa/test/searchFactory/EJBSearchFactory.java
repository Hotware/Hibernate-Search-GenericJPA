package org.hibernate.search.genericjpa.test.searchFactory;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.ejb.LocalBean;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.Stateful;
import javax.enterprise.concurrent.ManagedScheduledExecutorService;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;

import org.glassfish.embeddable.CommandResult;
import org.glassfish.embeddable.CommandResult.ExitStatus;
import org.glassfish.embeddable.CommandRunner;
import org.hibernate.search.genericjpa.JPASearchFactory;
import org.hibernate.search.genericjpa.db.events.MySQLTriggerSQLStringSource;
import org.hibernate.search.genericjpa.db.events.TriggerSQLStringSource;
import org.hibernate.search.genericjpa.test.entities.Game;
import org.hibernate.search.genericjpa.test.entities.GameUpdates;
import org.hibernate.search.genericjpa.test.entities.GameVendorUpdates;
import org.hibernate.search.genericjpa.test.entities.VendorUpdates;
import org.junit.Assert;

@Singleton
@LocalBean
@Stateful
@Startup
public class EJBSearchFactory extends JPASearchFactory {

	@Resource(mappedName = "org.glassfish.embeddable.CommandRunner")
	private CommandRunner commandRunner;

	private ManagedScheduledExecutorService exec;

	@PersistenceUnit
	private EntityManagerFactory emf;

	@PostConstruct
	public void startup() {
//		Assert.assertNotNull( "Verify that the asadmin CommandRunner resource is available", this.commandRunner );
//		CommandResult result = this.commandRunner.run( "help" );
//
//		Assert.assertEquals( result.getFailureCause().toString(), ExitStatus.SUCCESS, result.getExitStatus() );
//
//		try {
//			this.exec = InitialContext.doLookup( "concurrent/exec" );
//		}
//		catch (NamingException e) {
//			throw new RuntimeException( e );
//		}
//
//		super.init();
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
		return "hsearch.properties";
	}

	@Override
	protected long getDelay() {
		return 500;
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

}
