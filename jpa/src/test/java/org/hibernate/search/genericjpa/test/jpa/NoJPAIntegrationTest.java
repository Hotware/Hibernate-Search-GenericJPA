/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.test.jpa;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import org.apache.lucene.search.MatchAllDocsQuery;

import org.hibernate.search.genericjpa.Constants;
import org.hibernate.search.genericjpa.Setup;
import org.hibernate.search.genericjpa.entity.EntityProvider;
import org.hibernate.search.genericjpa.impl.JPASearchFactoryAdapter;
import org.hibernate.search.genericjpa.test.jpa.entities.NonJPAEntity;
import org.hibernate.search.jpa.FullTextEntityManager;
import org.hibernate.search.query.DatabaseRetrievalMethod;
import org.hibernate.search.query.ObjectLookupMethod;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Created by Martin on 04.07.2015.
 */
public class NoJPAIntegrationTest {

	private JPASearchFactoryAdapter searchFactory;

	@Before
	public void setup() {
		Properties properties = new Properties();
		properties.setProperty( "org.hibernate.search.genericjpa.searchfactory.name", "test" );
		properties.setProperty( Constants.ADDITIONAL_INDEXED_TYPES_KEY, NonJPAEntity.class.getName() );
		properties.setProperty( "org.hibernate.search.genericjpa.searchfactory.type", "manual-updates" );
		this.searchFactory = (JPASearchFactoryAdapter) Setup.createSearchFactory( null, properties );
	}

	@Test
	public void test() {
		final NonJPAEntity tmp = new NonJPAEntity();
		tmp.setDocumentId( "toast" );
		FullTextEntityManager fem = this.searchFactory.getFullTextEntityManager( null );
		fem.beginSearchTransaction();
		fem.index( tmp );
		fem.commitSearchTransaction();

		NonJPAEntity fromQuery = (NonJPAEntity) fem.createFullTextQuery( new MatchAllDocsQuery(), NonJPAEntity.class )
				.initializeObjectsWith(
						ObjectLookupMethod.SKIP, DatabaseRetrievalMethod.FIND_BY_ID
				)
				.entityProvider(
						new EntityProvider() {
							@Override
							public Object get(Class<?> entityClass, Object id) {
								return tmp;
							}

							@Override
							public List getBatch(Class<?> entityClass, List<Object> id) {
								throw new AssertionError();
							}

							@Override
							public void close() throws IOException {
								//no-op
							}
						}
				)
				.getResultList()
				.get( 0 );

		assertEquals( tmp, fromQuery );
	}

	@After
	public void shutdown() {
		this.searchFactory.close();
	}

}
