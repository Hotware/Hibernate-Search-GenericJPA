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

import com.github.hotware.hsearch.db.events.EventModelInfo.IdInfo;

/**
 * @author Martin
 *
 */
public class MySQLTriggerSQLStringSource implements TriggerSQLStringSource {

	private static final String PRE_TRIGGER = "";
	// TODO: even allow Table names with i.e. #TRIGGER_NAME# as their name.
	// maybe use String.format(...) here instead of placeholders
	private static final String CREATE_TRIGGER_SQL_FORMAT = ""
			+ "CREATE TRIGGER %s AFTER %s ON %s         \n"
			+ "FOR EACH ROW                                 \n"
			+ "BEGIN                                        \n"
			+ "    INSERT INTO placesorcererupdates(%s, %s) \n"
			+ "		VALUES(%s, %s);                         \n"
			+ "END;                                         \n";
	private static final String POST_TRIGGER = "";
	private static final String DROP_TRIGGER_SQL_FORMAT = ""
			+ "DROP TRIGGER IF EXISTS %s;";

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.github.hotware.hsearch.db.events.TriggerSQLStringSource#getTriggerString
	 * (com.github.hotware.hsearch.db.events.EventModelInfo, int)
	 */
	@Override
	public String getTriggerCreationString(EventModelInfo eventModelInfo,
			int eventType) {
		String originalTableName = eventModelInfo.getOriginalTableName();
		String triggerName = this.getTriggerName(
				eventModelInfo.getOriginalTableName(), eventType);
		String eventTypeColumn = eventModelInfo.getEventTypeColumn();
		StringBuilder valuesFromOriginal = new StringBuilder();
		StringBuilder idColumnNames = new StringBuilder();
		int addedVals = 0;
		for (IdInfo idInfo : eventModelInfo.getIdInfos()) {
			for (int i = 0; i < idInfo.getColumns().length; ++i) {
				if (addedVals > 0) {
					valuesFromOriginal.append(", ");
					idColumnNames.append(", ");
				}
				if(eventType == EventType.DELETE) {
					valuesFromOriginal.append("OLD.");
				} else {
					valuesFromOriginal.append("NEW.");
				}
				valuesFromOriginal.append(
						idInfo.getColumnsInOriginal()[i]);
				idColumnNames.append(idInfo.getColumns()[i]);
				++addedVals;
			}
		}
		if (addedVals == 0) {
			throw new IllegalArgumentException(
					"eventModelInfo didn't contain any idInfos");
		}
		String eventTypeValue = String.valueOf(eventType);
		String sql = new StringBuilder(PRE_TRIGGER)
				.append(String.format(CREATE_TRIGGER_SQL_FORMAT, triggerName,
						EventType.toString(eventType),
						originalTableName, eventTypeColumn,
						idColumnNames.toString(), eventTypeValue,
						valuesFromOriginal.toString())).append(POST_TRIGGER)
				.toString();
		return sql;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.github.hotware.hsearch.db.events.TriggerSQLStringSource#
	 * getTriggerDeletionString
	 * (com.github.hotware.hsearch.db.events.EventModelInfo, int)
	 */
	@Override
	public String getTriggerDropString(EventModelInfo eventModelInfo,
			int eventType) {
		String triggerName = this.getTriggerName(
				eventModelInfo.getOriginalTableName(), eventType);
		return String.format(DROP_TRIGGER_SQL_FORMAT, triggerName)
				.toUpperCase();
	}

	private String getTriggerName(String originalTableName, int eventType) {
		return originalTableName + "_updates_" + EventType.toString(eventType);
	}

}
