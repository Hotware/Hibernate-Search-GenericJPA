/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.db.events;

import java.sql.Connection;
import java.sql.SQLException;

import org.h2.api.Trigger;

/**
 * H2 Trigger Source implementation
 *
 * @author Martin Braun
 */
public class H2TriggerSQLStringSource implements TriggerSQLStringSource {

	@Override
	public String[] getRecreateUniqueIdTableCode() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String[] getSetupCode() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String[] getSpecificSetupCode(EventModelInfo eventModelInfo) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String[] getSpecificUnSetupCode(EventModelInfo eventModelInfo) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String[] getTriggerCreationCode(EventModelInfo eventModelInfo, int eventType) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String[] getTriggerDropCode(EventModelInfo eventModelInfo, int eventType) {
		// TODO Auto-generated method stub
		return null;
	}

	public static class H2Trigger implements Trigger {

		@Override
		public void close() throws SQLException {
			// TODO Auto-generated method stub

		}

		@Override
		public void fire(Connection arg0, Object[] arg1, Object[] arg2) throws SQLException {
			// TODO Auto-generated method stub

		}

		@Override
		public void init(Connection arg0, String arg1, String arg2, String arg3, boolean arg4, int arg5)
				throws SQLException {
			// TODO Auto-generated method stub

		}

		@Override
		public void remove() throws SQLException {
			// TODO Auto-generated method stub

		}

	}

}
