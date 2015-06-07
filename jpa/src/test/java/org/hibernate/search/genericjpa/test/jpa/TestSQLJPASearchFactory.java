/*
 * Copyright 2015 Martin Braun
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hibernate.search.genericjpa.test.jpa;

import java.util.Arrays;
import java.util.List;
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
		this.exec.shutdownNow();
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
	protected String getConfigFile() {
		return "/hsearch.properties";
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
	protected boolean isUseJTATransaction() {
		return false;
	}

}
