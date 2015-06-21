/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.test.integration;

import java.io.File;

import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;

import org.hibernate.search.genericjpa.test.entities.Game;

/**
 * @author Martin Braun
 */
public class IntegrationTestUtil {

	private IntegrationTestUtil() {
		// can't touch this
	}

	public static Archive<?> createHibernateMySQLDeployment() {
		File[] libs = Maven.resolver()
				.loadPomFromFile( "pom.xml" )
				.importRuntimeDependencies()
				.resolve()
				.withTransitivity()
				.asFile();
		return ShrinkWrap.create( WebArchive.class, "hibernate-mysql.war" )
				.addAsResource( "META-INF/hsearch.properties", "META-INF/hsearch.properties" )
				.addAsLibraries( libs )
				.setWebXML( "WEB-INF/web.xml" )
				.addPackage( Game.class.getPackage() )
				.addAsResource( "META-INF/hibernate-mysql-persistence.xml", "META-INF/persistence.xml" )
				.addAsWebInfResource( "beans.xml", "beans.xml" );
	}

}
