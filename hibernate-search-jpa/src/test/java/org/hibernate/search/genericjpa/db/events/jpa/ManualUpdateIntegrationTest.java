/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.db.events.jpa;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

import org.hibernate.search.cfg.spi.SearchConfiguration;
import org.hibernate.search.engine.integration.impl.ExtendedSearchIntegrator;
import org.hibernate.search.engine.metadata.impl.MetadataProvider;
import org.hibernate.search.entity.ReusableEntityProvider;
import org.hibernate.search.genericjpa.db.events.EventModelInfo;
import org.hibernate.search.genericjpa.db.events.EventModelParser;
import org.hibernate.search.genericjpa.db.events.IndexUpdater;
import org.hibernate.search.genericjpa.db.events.jpa.JPAUpdateSource;
import org.hibernate.search.genericjpa.entity.jpa.JPAReusableEntityProvider;
import org.hibernate.search.genericjpa.jpa.test.entities.Place;
import org.hibernate.search.genericjpa.jpa.test.entities.PlaceSorcererUpdates;
import org.hibernate.search.genericjpa.jpa.test.entities.PlaceUpdates;
import org.hibernate.search.genericjpa.jpa.test.entities.Sorcerer;
import org.hibernate.search.genericjpa.jpa.test.entities.SorcererUpdates;
import org.hibernate.search.spi.SearchIntegratorBuilder;
import org.hibernate.search.standalone.factory.SearchConfigurationImpl;
import org.hibernate.search.standalone.metadata.MetadataRehasher;
import org.hibernate.search.standalone.metadata.MetadataUtil;
import org.hibernate.search.standalone.metadata.RehashedTypeMetadata;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Martin Braun
 */
public class ManualUpdateIntegrationTest extends DatabaseIntegrationTest {

	Map<Class<?>, List<Class<?>>> containedInIndexOf;
	Map<Class<?>, RehashedTypeMetadata> rehashedTypeMetadataPerIndexRoot;
	ReusableEntityProvider entityProvider;
	MetaModelParser metaModelParser;

	@Before
	public void setup() throws SQLException {
		MetadataProvider metadataProvider = MetadataUtil.getMetadataProvider( new SearchConfigurationImpl() );
		MetadataRehasher rehasher = new MetadataRehasher();
		List<RehashedTypeMetadata> rehashedTypeMetadatas = new ArrayList<>();
		rehashedTypeMetadataPerIndexRoot = new HashMap<>();
		for ( Class<?> indexRootType : Arrays.asList( Place.class ) ) {
			RehashedTypeMetadata rehashed = rehasher.rehash( metadataProvider.getTypeMetadataFor( indexRootType ) );
			rehashedTypeMetadatas.add( rehashed );
			rehashedTypeMetadataPerIndexRoot.put( indexRootType, rehashed );
		}
		this.containedInIndexOf = MetadataUtil.calculateInIndexOf( rehashedTypeMetadatas );
		this.setup( "EclipseLink_MySQL" );
		this.metaModelParser = new MetaModelParser();
		this.metaModelParser.parse( this.emf.getMetamodel() );
	}

	@Test
	public void test() throws SQLException, InterruptedException {
		this.setupTriggers();
		try {
			if ( this.exceptionString != null ) {
				fail( exceptionString );
			}
			SearchConfiguration searchConfiguration = new SearchConfigurationImpl();

			SearchIntegratorBuilder builder = new SearchIntegratorBuilder();
			// we have to build an integrator here (but we don't need it
			// afterwards)
			builder.configuration( searchConfiguration ).buildSearchIntegrator();
			metaModelParser.getIndexRelevantEntites().forEach( (clazz) -> {
				builder.addClass( clazz );
			} );
			ExtendedSearchIntegrator impl = (ExtendedSearchIntegrator) builder.buildSearchIntegrator();
			JPAReusableEntityProvider entityProvider = new JPAReusableEntityProvider( this.emf, this.metaModelParser.getIdProperties(), false );
			IndexUpdater indexUpdater = new IndexUpdater( this.rehashedTypeMetadataPerIndexRoot, this.containedInIndexOf, entityProvider, impl );
			EventModelParser eventModelParser = new EventModelParser();
			List<EventModelInfo> eventModelInfos = eventModelParser.parse( new HashSet<>( Arrays.asList( PlaceUpdates.class, SorcererUpdates.class,
					PlaceSorcererUpdates.class ) ) );
			JPAUpdateSource updateSource = new JPAUpdateSource( eventModelInfos, this.emf, false, 500, TimeUnit.MILLISECONDS, 10 );
			updateSource.setUpdateConsumers( Arrays.asList( indexUpdater ) );
			updateSource.start();

			// database already contains stuff, so clear everything out here
			EntityManager em = this.emf.createEntityManager();
			try {
				this.assertCount( impl, 0 );
				this.deleteAllData( em );

				Thread.sleep( 3000 );
				this.assertCount( impl, 0 );

				this.writeAllIntoIndex( em, impl );

				this.deleteAllData( em );
				Thread.sleep( 3000 );
				this.assertCount( impl, 0 );

				this.writeAllIntoIndex( em, impl );

				{
					List<Integer> places = this.queryPlaceIds( impl, "name", "Valinor" );
					assertEquals( "this test expects to have exactly one Place named Valinor!", 1, places.size() );
					Integer valinorId = places.get( 0 );

					{
						EntityTransaction tx = em.getTransaction();
						tx.begin();
						Place valinor = em.find( Place.class, valinorId );
						valinor.setName( "Alinor" );
						em.persist( valinor );
						tx.commit();
					}
					Thread.sleep( 3000 );
					assertEquals( "shouldn't have found \"Valinor\" in the index anymore!", 0, this.queryPlaceIds( impl, "name", "Valinor" ).size() );
					this.assertCount( impl, 2 );

					{
						String oldName;
						{
							EntityTransaction tx = em.getTransaction();
							tx.begin();
							Place valinor = em.find( Place.class, valinorId );
							Sorcerer someSorcerer = valinor.getSorcerers().iterator().next();
							oldName = someSorcerer.getName();
							assertEquals( "should have found \"" + oldName + "\" in the index!", 1, this.queryPlaceIds( impl, "sorcerers.name", oldName )
									.size() );
							someSorcerer.setName( "Odalbert" );
							tx.commit();
						}
						Thread.sleep( 3000 );
						assertEquals( "shouldn't have found \"" + oldName + "\" in the index anymore!", 0, this.queryPlaceIds( impl, "sorcerers.name", oldName )
								.size() );
						this.assertCount( impl, 2 );
					}
				}

			}
			finally {
				if ( em != null ) {
					em.close();
				}
			}
		}
		finally {
			this.tearDownTriggers();
		}
	}

	private void writeAllIntoIndex(EntityManager em, ExtendedSearchIntegrator impl) throws InterruptedException {
		// and write data in the index again
		this.setupData( em );
		// wait a bit until the UpdateSource sent the appropriate events
		Thread.sleep( 3000 );
		this.assertCount( impl, 2 );
	}

	private void assertCount(ExtendedSearchIntegrator impl, int count) {
		assertEquals(
				count,
				impl.createHSQuery().targetedEntities( Arrays.asList( Place.class ) )
						.luceneQuery( impl.buildQueryBuilder().forEntity( Place.class ).get().all().createQuery() ).queryResultSize() );
	}

	private List<Integer> queryPlaceIds(ExtendedSearchIntegrator impl, String field, String value) {
		return impl.createHSQuery().targetedEntities( Arrays.asList( Place.class ) )
				.luceneQuery( impl.buildQueryBuilder().forEntity( Place.class ).get().keyword().onField( field ).matching( value ).createQuery() )
				.queryEntityInfos().stream().map( (entInfo) -> {
					return (Integer) entInfo.getId();
				} ).collect( Collectors.toList() );
	}

}
