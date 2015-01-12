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
package com.github.hotware.hsearch.db.events.jpa;

import static org.junit.Assert.fail;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

import org.junit.Test;

import com.github.hotware.hsearch.db.events.EventModelInfo;
import com.github.hotware.hsearch.db.events.EventModelParser;
import com.github.hotware.hsearch.db.events.EventType;
import com.github.hotware.hsearch.db.events.MySQLTriggerSQLStringSource;
import com.github.hotware.hsearch.jpa.test.entities.PlaceSorcererUpdates;

/**
 * @author Martin
 *
 */
public class MySQLIntegrationTest extends DatabaseIntegrationTest {

	@Test
	public void testMySQLIntegration() throws SQLException {
		this.setup("EclipseLink_MySQL");

		EntityManager em = null;
		try {
			em = this.emf.createEntityManager();
			EntityTransaction tx = em.getTransaction();
			tx.begin();

			java.sql.Connection connection = em
					.unwrap(java.sql.Connection.class);

			EventModelParser parser = new EventModelParser();
			EventModelInfo info = parser.parse(
					Arrays.asList(PlaceSorcererUpdates.class)).get(0);
			List<String> dropStrings = new ArrayList<>();
			String exceptionString = null;
			MySQLTriggerSQLStringSource triggerSource = new MySQLTriggerSQLStringSource();
			try {
				for (int eventType : EventType.values()) {
					String triggerCreationString = triggerSource
							.getTriggerCreationString(info, eventType);
					String triggerDropString = triggerSource
							.getTriggerDropString(info, eventType);
					System.out.println("CREATE: "
							+ connection.nativeSQL(triggerCreationString));
					dropStrings.add(triggerDropString);
					Statement statement = connection.createStatement();
					statement.addBatch(connection
							.nativeSQL(triggerCreationString));
					statement.executeBatch();
					connection.commit();
				}
			} catch (Exception e) {
				connection.rollback();
				exceptionString = e.getMessage();
			}

			for (String dropString : dropStrings) {
				Statement statement = connection.createStatement();
				System.out.println("DROP: " + connection.nativeSQL(dropString));
				statement.addBatch(connection.nativeSQL(dropString));
				statement.executeBatch();
				connection.commit();
			}

			tx.commit();

			if (exceptionString != null) {
				fail(exceptionString);
			}
		} finally {
			if (em != null) {
				em.close();
			}
		}
	}
}
