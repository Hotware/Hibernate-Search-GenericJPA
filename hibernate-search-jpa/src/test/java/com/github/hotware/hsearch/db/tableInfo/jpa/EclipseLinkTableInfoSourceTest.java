/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package com.github.hotware.hsearch.db.tableInfo.jpa;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.List;

import javax.persistence.Persistence;

import org.eclipse.persistence.internal.jpa.EntityManagerFactoryImpl;
import org.eclipse.persistence.internal.jpa.EntityManagerImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.github.hotware.hsearch.db.tableInfo.TableInfo;
import com.github.hotware.hsearch.jpa.test.entities.AdditionalPlace;
import com.github.hotware.hsearch.jpa.test.entities.JoinTableOneToOne;
import com.github.hotware.hsearch.jpa.test.entities.Place;
import com.github.hotware.hsearch.jpa.test.entities.Sorcerer;

/**
 * @author Martin Braun
 */
public class EclipseLinkTableInfoSourceTest {

	EntityManagerFactoryImpl emf;

	@Before
	public void setup() {
		this.emf = (EntityManagerFactoryImpl) Persistence.createEntityManagerFactory( "EclipseLink_MySQL" );
	}

	@Test
	public void test() {
		EntityManagerImpl em = (EntityManagerImpl) this.emf.createEntityManager();
		try {
			EclipseLinkTableInfoSource tblInfoSrc = new EclipseLinkTableInfoSource( em );
			int placeSorcererCount = 0;
			List<TableInfo> tableInfos = tblInfoSrc.getTableInfos( Arrays.asList( Place.class, Sorcerer.class ) );
			for ( TableInfo tableInfo : tableInfos ) {
				switch ( tableInfo.getTableNames().get( 0 ) ) {
					case "PLACE": {
						assertEquals( 1, tableInfo.getUpdateEventRelevantIdInfos().size() );
						assertEquals( Place.class, tableInfo.getUpdateEventRelevantIdInfos().get( 0 ).getEntityClass() );
						assertEquals( 1, tableInfo.getUpdateEventRelevantIdInfos().get( 0 ).getIdColumns().size() );
						// this is no mapping table so we have to have a explicit
						// name here
						assertEquals( "PLACE.ID", tableInfo.getUpdateEventRelevantIdInfos().get( 0 ).getIdColumns().get( 0 ) );
						assertEquals( 1, tableInfo.getUpdateEventRelevantIdInfos().get( 0 ).getIdColumnTypes().size() );
						assertEquals( Integer.class, tableInfo.getUpdateEventRelevantIdInfos().get( 0 ).getIdColumnTypes().get( "PLACE.ID" ) );
						break;
					}
					case "PLACE_JTOTO": {
						assertEquals( 2, tableInfo.getUpdateEventRelevantIdInfos().size() );
						boolean[] found = new boolean[2];
						for ( TableInfo.IdInfo idInfo : tableInfo.getUpdateEventRelevantIdInfos() ) {
							if ( idInfo.getEntityClass().equals( Place.class ) ) {
								assertEquals( 1, idInfo.getIdColumns().size() );
								assertEquals( "PLACE_ID", idInfo.getIdColumns().get( 0 ) );
								assertEquals( Integer.class, idInfo.getIdColumnTypes().get( "PLACE_ID" ) );
								found[0] = true;
							}
							else if ( idInfo.getEntityClass().equals( JoinTableOneToOne.class ) ) {
								assertEquals( 1, idInfo.getIdColumns().size() );
								assertEquals( "JTOTO_ID", idInfo.getIdColumns().get( 0 ) );
								assertEquals( Integer.class, idInfo.getIdColumnTypes().get( "JTOTO_ID" ) );
								found[1] = true;
							}
							else {
								fail( "either Place or JoinTableOneToOne were expected!" );
							}
						}
						assertTrue_( found );
						break;
					}
					case "PLACE_ADDITIONALPLACE": {
						assertEquals( 2, tableInfo.getUpdateEventRelevantIdInfos().size() );
						boolean found[] = new boolean[2];
						for ( TableInfo.IdInfo idInfo : tableInfo.getUpdateEventRelevantIdInfos() ) {
							if ( idInfo.getEntityClass().equals( Place.class ) ) {
								assertEquals( 1, idInfo.getIdColumns().size() );
								assertEquals( "place_ID", idInfo.getIdColumns().get( 0 ) );
								assertEquals( Integer.class, idInfo.getIdColumnTypes().get( "place_ID" ) );
								found[0] = true;
							}
							else if ( idInfo.getEntityClass().equals( AdditionalPlace.class ) ) {
								assertEquals( 1, idInfo.getIdColumns().size() );
								assertEquals( "additionalPlace_ID", idInfo.getIdColumns().get( 0 ) );
								assertEquals( Integer.class, idInfo.getIdColumnTypes().get( "additionalPlace_ID" ) );
								found[1] = true;
							}
							else {
								fail( "either Place or AdditionalPlace were expected!" );
							}
						}
						assertTrue_( found );
						break;
					}
					case "PLACE_SORCERER": {
						assertEquals( 2, tableInfo.getUpdateEventRelevantIdInfos().size() );
						boolean found[] = new boolean[2];
						for ( TableInfo.IdInfo idInfo : tableInfo.getUpdateEventRelevantIdInfos() ) {
							if ( idInfo.getEntityClass().equals( Place.class ) ) {
								assertEquals( 1, idInfo.getIdColumns().size() );
								assertEquals( "Place_ID", idInfo.getIdColumns().get( 0 ) );
								assertEquals( Integer.class, idInfo.getIdColumnTypes().get( "Place_ID" ) );
								found[0] = true;
							}
							else if ( idInfo.getEntityClass().equals( Sorcerer.class ) ) {
								assertEquals( 1, idInfo.getIdColumns().size() );
								assertEquals( "sorcerers_ID", idInfo.getIdColumns().get( 0 ) );
								assertEquals( Integer.class, idInfo.getIdColumnTypes().get( "sorcerers_ID" ) );
								found[1] = true;
							}
							else {
								fail( "either Place or Sorcerer were expected!" );
							}
						}
						++placeSorcererCount;
						assertTrue_( found );
						break;
					}
					case "SORCERER": {
						// normal table mapping was tested with Place already
						break;
					}
					default: {
						fail( "unexpected name " + tableInfo.getTableNames().get( 0 ) );
					}
				}
			}
			System.out.println( tableInfos );
			assertEquals( "mapping tables should only appear once, which means that PLACE_SORCERER was expected once!", 1, placeSorcererCount );
		}
		finally {
			if ( em != null ) {
				em.close();
			}
		}
	}

	private static void assertTrue_(boolean[] values) {
		assertTrue_( null, values );
	}

	private static void assertTrue_(String message, boolean[] values) {
		for ( boolean value : values ) {
			if ( message != null ) {
				assertTrue( message, value );
			}
			else {
				assertTrue( value );
			}
		}
	}

	@After
	public void tearDown() {
		this.emf.close();
	}

}
