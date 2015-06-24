/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.test.db.events;

import java.util.Arrays;
import java.util.HashSet;

import org.hibernate.search.genericjpa.db.events.EventModelInfo;
import org.hibernate.search.genericjpa.db.events.EventModelParser;
import org.hibernate.search.genericjpa.db.events.EventType;
import org.hibernate.search.genericjpa.db.events.triggers.MySQLTriggerSQLStringSource;
import org.hibernate.search.genericjpa.test.db.entities.PlaceSorcererUpdates;

import org.junit.Test;

/**
 * @author Martin
 */
public class MySQLTriggerSQLStringSourceTest {

	@Test
	public void test() {
		EventModelParser parser = new EventModelParser();
		EventModelInfo info = parser.parse( new HashSet<>( Arrays.asList( PlaceSorcererUpdates.class ) ) ).get( 0 );
		MySQLTriggerSQLStringSource triggerSource = new MySQLTriggerSQLStringSource();
		System.out.println( Arrays.asList( triggerSource.getSetupCode() ) );
		for ( int eventType : EventType.values() ) {
			String[] triggerCreationString = triggerSource.getTriggerCreationCode( info, eventType );
			String[] triggerDropString = triggerSource.getTriggerDropCode( info, eventType );
			System.out.println( "CREATE: " + Arrays.asList( triggerCreationString ) );
			System.out.println( "DROP: " + Arrays.asList( triggerDropString ) );
		}
	}

}
