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
package org.hibernate.search.genericjpa.test.integration;

import org.hibernate.search.genericjpa.test.entities.Game;
import org.hibernate.search.genericjpa.test.searchFactory.EJBSearchFactory;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;

/**
 * @author Martin Braun
 */
public class IntegrationTestUtil {
	
	private IntegrationTestUtil() {
		// can't touch this
	}

	public static Archive<?> createEclipseLinkMySQLDeployment() {
		return ShrinkWrap.create( WebArchive.class, "eclipselink-mysql.war" ).setWebXML( "WEB-INF/web.xml" ).addPackage( Game.class.getPackage() )
				.addPackage( EJBSearchFactory.class.getPackage() ).addAsResource( "META-INF/eclipselink-mysql-persistence.xml", "META-INF/persistence.xml" )
				.addAsWebInfResource( EmptyAsset.INSTANCE, "beans.xml" );
	}
	
	public static Archive<?> createHibernateMySQLDeployment() {
		return ShrinkWrap.create( WebArchive.class, "hibernate-mysql.war" ).setWebXML( "WEB-INF/web.xml" ).addPackage( Game.class.getPackage() )
				.addPackage( EJBSearchFactory.class.getPackage() ).addAsResource( "META-INF/hibernate-mysql-persistence.xml", "META-INF/persistence.xml" )
				.addAsWebInfResource( EmptyAsset.INSTANCE, "beans.xml" );
	}

}
