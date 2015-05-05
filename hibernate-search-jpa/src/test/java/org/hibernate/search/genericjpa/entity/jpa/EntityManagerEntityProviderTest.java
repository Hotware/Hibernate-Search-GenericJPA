/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.entity.jpa;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;

import org.hibernate.search.genericjpa.db.events.jpa.DatabaseIntegrationTest;
import org.hibernate.search.genericjpa.db.events.jpa.MetaModelParser;
import org.hibernate.search.genericjpa.entity.jpa.EntityManagerEntityProvider;
import org.hibernate.search.genericjpa.jpa.test.entities.Place;
import org.junit.Test;

/**
 * @author Martin
 */
public class EntityManagerEntityProviderTest extends DatabaseIntegrationTest {

	@SuppressWarnings("unchecked")
	@Test
	public void test() throws SQLException, IOException {
		this.setup( "EclipseLink" );
		EntityManager em = this.emf.createEntityManager();
		MetaModelParser metaModelParser = new MetaModelParser();
		metaModelParser.parse( this.emf.getMetamodel() );
		EntityManagerEntityProvider provider = new EntityManagerEntityProvider( em, metaModelParser.getIdProperties() );
		try {

			assertEquals( "Valinor", ( (Place) provider.get( Place.class, this.valinorId ) ).getName() );
			List<Place> batch = (List<Place>) provider.getBatch( Place.class, Arrays.asList( this.valinorId, this.helmsDeepId ) );
			assertEquals( 2, batch.size() );
			// order is not preserved in getBatch!
			Set<String> names = batch.stream().map( (place) -> {
				return place.getName();
			} ).collect( Collectors.toSet() );
			assertTrue( "didn't contain Valinor!", names.contains( "Valinor" ) );
			assertTrue( "didn't contain Helm's Deep", names.contains( "Helm's Deep" ) );
		}
		finally {
			if ( provider != null ) {
				provider.close();
			}
		}
	}

}
