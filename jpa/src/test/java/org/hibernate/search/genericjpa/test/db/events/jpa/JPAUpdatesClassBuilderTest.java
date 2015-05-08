/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.test.db.events.jpa;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.hibernate.search.genericjpa.db.events.EventModelInfo;
import org.hibernate.search.genericjpa.db.events.EventModelParser;
import org.hibernate.search.genericjpa.db.events.jpa.JPAUpdatesClassBuilder;
import org.hibernate.search.genericjpa.db.events.jpa.JPAUpdatesClassBuilder.IdColumn;
import org.hibernate.search.genericjpa.db.id.DefaultToOriginalIdBridge;
import org.hibernate.search.genericjpa.test.jpa.entities.Place;
import org.hibernate.search.genericjpa.test.util.InMemoryCompiler;
import org.junit.Test;

/**
 * @author Martin
 */
public class JPAUpdatesClassBuilderTest {

	@Test
	public void testCompileAndAnnotations() throws Exception {
		String asString = this.buildCode( "" );
		Class<?> clazz = InMemoryCompiler.compile( asString, "MyUpdateClass" );
		EventModelParser parser = new EventModelParser();
		List<EventModelInfo> infos = parser.parse( new HashSet<>( Arrays.asList( clazz ) ) );
		EventModelInfo info = infos.get( 0 );
		assertTrue( info.getEventTypeAccessor() != null );
		assertEquals( "hsearchEventCase", info.getEventTypeColumn() );
		assertEquals( "originalTableName", info.getOriginalTableName() );
		assertEquals( "tableName", info.getTableName() );
		assertEquals( clazz, info.getUpdateClass() );

		EventModelInfo.IdInfo idInfo = info.getIdInfos().get( 0 );
		assertEquals( "placeId", idInfo.getColumns()[0] );
		assertEquals( "Place_ID", idInfo.getColumnsInOriginal()[0] );
		assertEquals( Place.class, idInfo.getEntityClass() );
		assertEquals( DefaultToOriginalIdBridge.class, idInfo.getToOriginalBridge().getClass() );
		// we just pass what we want here as the DefaultBridge just returns the
		// value it is passed
		assertEquals( 123123, idInfo.getToOriginalBridge().toOriginal( 123123 ) );
		assertEquals( null, idInfo.getIdAccessor().apply( clazz.newInstance() ) );
	}

	@Test
	public void checkPackage() throws IOException {
		String code = this.buildCode( "pack" );
		System.out.println( code );
		assertTrue( code.startsWith( "package pack;" ) );
	}

	private String buildCode(String pack) throws IOException {
		JPAUpdatesClassBuilder builder = new JPAUpdatesClassBuilder();
		ByteArrayOutputStream bas = new ByteArrayOutputStream( 1000 );
		PrintStream ps = new PrintStream( bas );
		builder.tableName( "tableName" ).originalTableName( "originalTableName" )
				.idColumn( IdColumn.of( Long.class, true, Place.class, new String[] { "placeId" }, new String[] { "Place_ID" } ) )
				.build( ps, pack, "MyUpdateClass" );
		String asString = bas.toString( "UTF-8" );
		return asString;
	}

}
