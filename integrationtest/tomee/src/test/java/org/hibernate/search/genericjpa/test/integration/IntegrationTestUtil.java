/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.test.integration;

import java.io.File;
import javax.websocket.server.ServerEndpointConfig;

import org.hibernate.search.genericjpa.test.entities.Game;
import org.hibernate.search.genericjpa.test.searchFactory.EJBSearchFactory;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;

/**
 * @author Martin Braun
 */
public class IntegrationTestUtil {

	private IntegrationTestUtil() {
		// can't touch this
	}

	public static Archive<?> createOpenJPAMySQLDeployment() {
		return ShrinkWrap.create( WebArchive.class, "openjpa-mysql.war" ).setWebXML( "WEB-INF/web.xml" )
				.addAsResource( "WEB-INF/context.xml", "WEB-INF/context.xml" ).addPackage( Game.class.getPackage() )
				.addPackage( EJBSearchFactory.class.getPackage() ).addAsResource( "META-INF/openjpa-mysql-persistence.xml", "META-INF/persistence.xml" )
				.addAsWebInfResource( EmptyAsset.INSTANCE, "beans.xml" );
	}

}
