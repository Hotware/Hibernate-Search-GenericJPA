/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.standalone.factory;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.TermQuery;
import org.hibernate.search.annotations.ContainedIn;
import org.hibernate.search.annotations.DocumentId;
import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.Indexed;
import org.hibernate.search.annotations.IndexedEmbedded;
import org.hibernate.search.annotations.Store;
import org.hibernate.search.backend.spi.Work;
import org.hibernate.search.backend.spi.WorkType;
import org.hibernate.search.cfg.spi.SearchConfiguration;
import org.hibernate.search.spi.SearchIntegrator;
import org.hibernate.search.spi.SearchIntegratorBuilder;
import org.hibernate.search.standalone.factory.SearchConfigurationImpl;
import org.hibernate.search.standalone.factory.SearchFactory;
import org.hibernate.search.standalone.factory.SearchFactoryFactory;
import org.hibernate.search.standalone.factory.Transaction;
import org.hibernate.search.standalone.query.HSearchQuery;
import org.junit.Test;

public class SearchFactoryTest {

	@Indexed
	public static class TopLevel {

		private int id;
		private Embedded embedded;

		public void setId(int id) {
			this.id = id;
		}

		@DocumentId
		public int getId() {
			return this.id;
		}

		@IndexedEmbedded
		public Embedded getEmbedded() {
			return embedded;
		}

		public void setEmbedded(Embedded embedded) {
			this.embedded = embedded;
		}

	}

	public static class Embedded {

		private String test;
		private TopLevel topLevel;
		private List<Embedded2> embedded2;

		public void setTest(String test) {
			this.test = test;
		}

		@Field(store = Store.YES)
		public String getTest() {
			return this.test;
		}

		@ContainedIn
		public TopLevel getTopLevel() {
			return this.topLevel;
		}

		public void setTopLevel(TopLevel topLevel) {
			this.topLevel = topLevel;
		}

		@IndexedEmbedded
		public List<Embedded2> getEmbedded2() {
			return embedded2;
		}

		public void setEmbedded2(List<Embedded2> embedded2) {
			this.embedded2 = embedded2;
		}

	}

	public static class Embedded2 {

		private String test;
		private Embedded embedded;

		public void setTest(String test) {
			this.test = test;
		}

		@Field(store = Store.YES)
		public String getTest() {
			return this.test;
		}

		@ContainedIn
		public Embedded getEmbedded() {
			return embedded;
		}

		public void setEmbedded(Embedded embedded) {
			this.embedded = embedded;
		}

	}

	@Test
	public void testWithoutNewClasses() {
		SearchConfiguration searchConfiguration = new SearchConfigurationImpl();
		List<Class<?>> classes = Arrays.asList( TopLevel.class );

		SearchIntegratorBuilder builder = new SearchIntegratorBuilder();
		// we have to build an integrator here (but we don't need it afterwards)
		builder.configuration( searchConfiguration ).buildSearchIntegrator();
		classes.forEach( (clazz) -> {
			builder.addClass( clazz );
		} );
		SearchIntegrator impl = builder.buildSearchIntegrator();

		TopLevel tl = new TopLevel();
		tl.setId( 123 );
		Embedded eb = new Embedded();

		List<Embedded2> embedded2 = new ArrayList<>();
		{
			Embedded2 e1 = new Embedded2();
			e1.setEmbedded( eb );
			embedded2.add( e1 );

			Embedded2 e2 = new Embedded2();
			e2.setEmbedded( eb );
			embedded2.add( e1 );
		}
		eb.setEmbedded2( embedded2 );

		tl.setEmbedded( eb );
		Transaction tc = new Transaction();

		impl.getWorker().performWork( new Work( tl, WorkType.ADD ), tc );
	}

	@Test
	public void test() throws IOException {
		try (SearchFactory factory = SearchFactoryFactory.createSearchFactory( new SearchConfigurationImpl(),
				Arrays.asList( TopLevel.class, Embedded.class, Embedded2.class ) )) {

			TopLevel tl = new TopLevel();
			tl.setId( 123 );
			Embedded eb = new Embedded();

			List<Embedded2> embedded2 = new ArrayList<>();
			{
				Embedded2 e1 = new Embedded2();
				e1.setEmbedded( eb );
				embedded2.add( e1 );

				Embedded2 e2 = new Embedded2();
				e2.setEmbedded( eb );
				embedded2.add( e1 );
			}
			eb.setEmbedded2( embedded2 );

			tl.setEmbedded( eb );
			eb.setTopLevel( tl );

			// indexing starting from the contained entity should work as well
			// :)
			factory.index( embedded2.get( 0 ) );

			assertEquals( 1, factory.getStatistics().getNumberOfIndexedEntities( TopLevel.class.getName() ) );

			factory.purge( TopLevel.class, new TermQuery( new Term( "id", "123" ) ) );
			HSearchQuery query = factory.createQuery( factory.buildQueryBuilder().forEntity( TopLevel.class ).get().all().createQuery(), TopLevel.class );
			assertEquals( 0, query.queryResultSize() );
		}
	}
}
