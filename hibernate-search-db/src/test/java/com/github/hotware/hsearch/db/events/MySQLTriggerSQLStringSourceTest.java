/*
 * Copyright 2015 Martin Braun
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.hotware.hsearch.db.events;

import java.util.Arrays;
import java.util.HashSet;

import org.junit.Test;

import com.github.hotware.hsearch.db.test.entities.PlaceSorcererUpdates;

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
