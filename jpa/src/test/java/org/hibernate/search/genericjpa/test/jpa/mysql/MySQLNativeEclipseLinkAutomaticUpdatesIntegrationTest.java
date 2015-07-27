/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.test.jpa.mysql;

import java.util.Properties;

import org.apache.lucene.search.MatchAllDocsQuery;

import org.hibernate.search.genericjpa.Constants;
import org.hibernate.search.genericjpa.Setup;
import org.hibernate.search.genericjpa.db.events.triggers.MySQLTriggerSQLStringSource;
import org.hibernate.search.genericjpa.impl.JPASearchFactoryAdapter;
import org.hibernate.search.genericjpa.test.jpa.AutomaticUpdatesIntegrationTest;
import org.hibernate.search.genericjpa.test.jpa.entities.CustomUpdatedEntity;
import org.hibernate.search.genericjpa.test.jpa.entities.ID;
import org.hibernate.search.genericjpa.test.jpa.entities.MultipleColumnsIdEntity;
import org.hibernate.search.genericjpa.test.jpa.entities.NonJPAEntity;
import org.hibernate.search.jpa.FullTextEntityManager;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Created by Martin on 27.07.2015.
 */
public class MySQLNativeEclipseLinkAutomaticUpdatesIntegrationTest extends AutomaticUpdatesIntegrationTest {

	@Before
	public void setup() {
		this.setup( "eclipselink", "EclipseLink_MySQL", MySQLTriggerSQLStringSource.class );
	}

	@Test
	public void testNativeEvents() {
		if ( "sql".equals( this.searchFactoryType ) ) {
			System.out.println( "skipping rollback test for searchFactoryType (useless for this type): " + this.searchFactoryType );
			return;
		}
		Properties properties = new Properties();
		properties.setProperty( Constants.SEARCH_FACTORY_NAME_KEY, "testCustomUpdatedEntity" );
		properties.setProperty( Constants.ADDITIONAL_INDEXED_TYPES_KEY, NonJPAEntity.class.getName() );
		//we do manual updates, so this will be ignored, but let's keep it here
		//if we change our mind later
		properties.setProperty(
				Constants.TRIGGER_SOURCE_KEY,
				this.triggerSourceClass.getName()
		);
		properties.setProperty(
				Constants.TRIGGER_CREATION_STRATEGY_KEY,
				Constants.TRIGGER_CREATION_STRATEGY_DROP_CREATE
		);
		properties.setProperty( Constants.BATCH_SIZE_FOR_UPDATES_KEY, "2" );
		properties.setProperty( Constants.SEARCH_FACTORY_TYPE_KEY, this.searchFactoryType );
		JPASearchFactoryAdapter searchFactory = (JPASearchFactoryAdapter) Setup.createSearchFactoryController(
				this.emf,
				properties
		);
		try {
			for ( int times = 0; times < 100; ++times ) {
				this.em.getTransaction().begin();
				for ( int i = 0; i < 5; ++i ) {
					MultipleColumnsIdEntity ent = new MultipleColumnsIdEntity();
					ent.setFirstId( "first" + i );
					ent.setSecondId( "second" + i );
					ent.setInfo( "info" + i );
					this.em.persist( ent );
				}
				this.em.getTransaction().rollback();
			}
			assertEquals(
					0, searchFactory.getFullTextEntityManager( this.em )
							.createFullTextQuery( new MatchAllDocsQuery(), MultipleColumnsIdEntity.class )
							.getResultSize()
			);

			{
				this.em.getTransaction().begin();
				MultipleColumnsIdEntity ent = new MultipleColumnsIdEntity();
				ent.setFirstId( "first" );
				ent.setSecondId( "second" );
				ent.setInfo( "info" );
				this.em.persist( ent );
				this.em.getTransaction().commit();

				assertEquals(
						1, searchFactory.getFullTextEntityManager( this.em )
								.createFullTextQuery( new MatchAllDocsQuery(), MultipleColumnsIdEntity.class )
								.getResultSize()
				);
			}

			{
				this.em.getTransaction().begin();
				MultipleColumnsIdEntity ent = this.em.find(
						MultipleColumnsIdEntity.class, new ID(
								"first",
								"second"
						)
				);
				ent.setInfo( "info_new" );
				this.em.getTransaction().commit();

				assertEquals(
						1, searchFactory.getFullTextEntityManager( this.em )
								.createFullTextQuery( new MatchAllDocsQuery(), MultipleColumnsIdEntity.class )
								.getResultSize()
				);

				FullTextEntityManager fem = searchFactory.getFullTextEntityManager( this.em );

				assertEquals(
						1, fem.createFullTextQuery(
								fem.getSearchFactory().buildQueryBuilder().forEntity(
										MultipleColumnsIdEntity.class
								).get().keyword().onField( "info" ).matching( "info_new" ).createQuery(),
								MultipleColumnsIdEntity.class
						).getResultSize()
				);
			}

			{
				this.em.getTransaction().begin();
				MultipleColumnsIdEntity ent = this.em.find(
						MultipleColumnsIdEntity.class, new ID(
								"first",
								"second"
						)
				);
				this.em.remove( ent );
				this.em.getTransaction().commit();

				//assertEquals(
				//		0, searchFactory.getFullTextEntityManager( this.em )
				//				.createFullTextQuery( new MatchAllDocsQuery(), MultipleColumnsIdEntity.class )
				//				.getResultSize()
				//);
			}
		}
		finally {
			searchFactory.close();
		}
	}

}
