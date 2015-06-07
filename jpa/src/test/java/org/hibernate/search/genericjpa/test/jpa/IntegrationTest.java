/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.test.jpa;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;

import org.apache.lucene.search.Query;
import org.eclipse.persistence.descriptors.ClassDescriptor;
import org.hibernate.search.genericjpa.entity.EntityManagerEntityProvider;
import org.hibernate.search.genericjpa.test.db.events.jpa.MetaModelParser;
import org.hibernate.search.genericjpa.test.jpa.entities.AdditionalPlace;
import org.hibernate.search.genericjpa.test.jpa.entities.AdditionalPlace2;
import org.hibernate.search.genericjpa.test.jpa.entities.EmbeddableInfo;
import org.hibernate.search.genericjpa.test.jpa.entities.Place;
import org.hibernate.search.genericjpa.test.jpa.entities.Sorcerer;
import org.hibernate.search.query.dsl.QueryBuilder;
import org.hibernate.search.standalone.entity.EntityProvider;
import org.hibernate.search.standalone.factory.SearchConfigurationImpl;
import org.hibernate.search.standalone.factory.StandaloneSearchFactory;
import org.hibernate.search.standalone.factory.StandaloneSearchFactoryFactory;
import org.hibernate.search.standalone.query.HSearchQuery;
import org.hibernate.search.standalone.query.HSearchQuery.Fetch;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class IntegrationTest {

	private int valinorId = 0;
	private Place valinor;
	private EntityManagerFactory emf;
	private TestSQLJPASearchFactory searchFactory;

	@Test
	public void testEclipseLink() throws IOException {
		this.setup( "EclipseLink_MySQL" );
		try {
			this.metaModelParser();
		}
		finally {
			this.shutdown();
		}
	}

	public void setup(String persistence) {
		this.emf = Persistence.createEntityManagerFactory( persistence );
		this.searchFactory = new TestSQLJPASearchFactory( this.emf );
		this.searchFactory.start();
		EntityManager em = emf.createEntityManager();
		try {
			EntityTransaction tx = em.getTransaction();
			tx.begin();

			@SuppressWarnings("unchecked")
			List<Place> toDelete = new ArrayList<>( em.createQuery( "SELECT a FROM Place a" ).getResultList() );
			for ( Place place : toDelete ) {
				em.remove( place );
			}
			em.flush();

			Sorcerer gandalf = new Sorcerer();
			gandalf.setName( "Gandalf" );
			em.persist( gandalf );

			Sorcerer saruman = new Sorcerer();
			saruman.setName( "Saruman" );
			em.persist( saruman );

			Sorcerer radagast = new Sorcerer();
			radagast.setName( "Radagast" );
			em.persist( radagast );

			Sorcerer alatar = new Sorcerer();
			alatar.setName( "Alatar" );
			em.persist( alatar );

			Sorcerer pallando = new Sorcerer();
			pallando.setName( "Pallando" );
			em.persist( pallando );

			// populate this database with some stuff
			Place helmsDeep = new Place();
			helmsDeep.setName( "Helm's Deep" );
			Set<Sorcerer> sorcerersAtHelmsDeep = new HashSet<>();
			sorcerersAtHelmsDeep.add( gandalf );
			gandalf.setPlace( helmsDeep );
			helmsDeep.setSorcerers( sorcerersAtHelmsDeep );
			em.persist( helmsDeep );

			Place valinor = new Place();
			valinor.setName( "Valinor" );
			Set<Sorcerer> sorcerersAtValinor = new HashSet<>();
			sorcerersAtValinor.add( saruman );
			saruman.setPlace( valinor );
			valinor.setSorcerers( sorcerersAtValinor );
			em.persist( valinor );

			valinorId = valinor.getId();

			this.valinor = valinor;

			em.flush();
			tx.commit();
		}
		finally {
			if ( em != null ) {
				em.close();
			}
		}

	}

	public void shutdown() {
		if ( this.emf != null ) {
			System.out.println( "killing EntityManagerFactory" );
			this.emf.close();
		}
		this.searchFactory.shutdown();
	}

	public void metaModelParser() throws IOException {
		EntityProvider entityProvider = null;
		StandaloneSearchFactory searchFactory = null;
		try {
			MetaModelParser parser = new MetaModelParser();
			parser.parse( this.emf.getMetamodel() );
			{
				assertEquals( 4, parser.getIndexRelevantEntites().size() );
			}
		}
		finally {
			if ( entityProvider != null ) {
				entityProvider.close();
			}
			if ( searchFactory != null ) {
				searchFactory.close();
			}
		}
	}

}
