/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package com.github.hotware.hsearch.factory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.TermQuery;
import org.hibernate.search.annotations.DocumentId;
import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.Index;
import org.hibernate.search.annotations.Indexed;
import org.hibernate.search.annotations.Store;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.github.hotware.hsearch.dto.annotations.DtoField;
import com.github.hotware.hsearch.dto.annotations.DtoOverEntity;

public class IndexOperationsTest {

	private static final Logger LOGGER = Logger.getLogger( IndexOperationsTest.class.getName() );

	@Indexed
	@DtoOverEntity(entityClass = Book.class)
	public static final class Book {

		@DtoField(fieldName = "id")
		private int id;
		@DtoField(fieldName = "title")
		private String title;

		public Book() {

		}

		public Book(int id, String title) {
			this.id = id;
			this.title = title;
		}

		@DocumentId
		public int getId() {
			return id;
		}

		public void setId(int id) {
			this.id = id;
		}

		@Field(store = Store.YES, index = Index.YES)
		public String getTitle() {
			return this.title;
		}

		public void setText(String title) {
			this.title = title;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + id;
			result = prime * result + ( ( title == null ) ? 0 : title.hashCode() );
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if ( this == obj ) {
				return true;
			}
			if ( obj == null ) {
				return false;
			}
			if ( getClass() != obj.getClass() ) {
				return false;
			}
			Book other = (Book) obj;
			if ( id != other.id ) {
				return false;
			}
			if ( title == null ) {
				if ( other.title != null ) {
					return false;
				}
			}
			else if ( !title.equals( other.title ) ) {
				return false;
			}
			return true;
		}

	}

	private SearchFactory factory;

	@Before
	public void setup() {
		LOGGER.info( "setting up IndexOperationsTest" );
		this.factory = SearchFactoryFactory.createSearchFactory( new SearchConfigurationImpl(), Arrays.asList( Book.class ) );
	}

	@Test
	public void test() {
		List<Book> l = new ArrayList<>();
		l.add( new Book( 1, "The Hobbit" ) );
		l.add( new Book( 2, "Lord of the Rings" ) );

		this.factory.index( l );
		this.assertCount( 2 );

		this.factory.purgeAll( Book.class );
		this.assertCount( 0 );

		this.factory.index( l.get( 0 ) );
		this.assertCount( 1 );
		this.factory.index( l.get( 1 ) );
		this.assertCount( 2 );

		{
			Transaction tc = new Transaction();
			this.factory.purgeAll( Book.class, tc );
			tc.end();
		}
		this.assertCount( 0 );

		{
			Transaction tc = new Transaction();
			this.factory.index( l, tc );
			tc.end();
		}
		this.assertCount( 2 );

		this.factory.purgeAll( Book.class );
		this.assertCount( 0 );

		{
			Transaction tc = new Transaction();
			this.factory.index( l.get( 0 ), tc );
			tc.end();
		}
		this.assertCount( 1 );
		this.factory.purgeAll( Book.class );
		this.assertCount( 0 );

		this.factory.index( l );
		this.assertCount( 2 );

		this.factory.delete( l );
		this.assertCount( 0 );

		this.factory.index( l );
		;
		this.assertCount( 2 );

		this.factory.delete( l.get( 0 ) );
		this.assertCount( 1 );
		this.factory.delete( l.get( 1 ) );
		this.assertCount( 0 );

		this.factory.index( l );
		this.assertCount( 2 );

		{
			Transaction tc = new Transaction();
			this.factory.delete( l, tc );
			tc.end();
		}
		this.assertCount( 0 );

		this.factory.index( l );
		this.assertCount( 2 );

		{
			Transaction tc = new Transaction();
			this.factory.delete( l.get( 0 ), tc );
			tc.end();
		}
		this.assertCount( 1 );
		this.factory.purgeAll( Book.class );
		this.assertCount( 0 );

		this.factory.index( l );
		this.assertCount( 2 );

		List<Book> updated = new ArrayList<>();
		updated.add( new Book( 1, "The ultimate Hobbit" ) );
		updated.add( new Book( 2, "Lord of The ultimate Rings" ) );
		this.factory.update( updated );
		this.assertCount( 2 );
		assertNotEquals( this.all(), l );

		this.factory.purgeAll( Book.class );
		this.factory.index( l );
		this.assertCount( 2 );

		int id = updated.get( 0 ).getId();
		this.factory.update( updated.get( 0 ) );
		this.assertCount( 2 );
		assertNotEquals( this.id( id ), l.get( 0 ) );

		this.factory.purgeAll( Book.class );
		this.factory.index( l );
		this.assertCount( 2 );

		{
			Transaction tc = new Transaction();
			id = updated.get( 0 ).getId();
			this.factory.update( updated.get( 0 ), tc );
			tc.end();
		}
		this.assertCount( 2 );
		assertNotEquals( this.id( id ), l.get( 0 ) );

		{
			Transaction tc = new Transaction();
			this.factory.update( updated, tc );
			tc.end();
		}
		this.assertCount( 2 );
		assertNotEquals( this.all(), l );

		this.factory.purge( Book.class, new TermQuery( new Term( "id", "1" ) ) );
		this.assertCount( 1 );

		{
			Transaction tc = new Transaction();
			this.factory.purge( Book.class, new TermQuery( new Term( "id", "2" ) ), tc );
			tc.end();
		}
		this.assertCount( 0 );
	}

	private void assertCount(int count) {
		assertEquals( count, this.factory.createQuery( this.factory.buildQueryBuilder().forEntity( Book.class ).get().all().createQuery(), Book.class )
				.queryResultSize() );
	}

	private List<Book> all() {
		return this.factory.createQuery( this.factory.buildQueryBuilder().forEntity( Book.class ).get().all().createQuery(), Book.class ).maxResults( 10 )
				.queryDto( Book.class );
	}

	private Book id(int id) {
		return this.factory
				.createQuery(
						this.factory.buildQueryBuilder().forEntity( Book.class ).get().keyword().onField( "id" ).matching( String.valueOf( id ) ).createQuery(),
						Book.class ).maxResults( 10 ).queryDto( Book.class ).get( 0 );
	}

	@After
	public void tearDown() throws IOException {
		LOGGER.info( "tearing down IndexOperationsTest" );
		this.factory.close();
	}

}
