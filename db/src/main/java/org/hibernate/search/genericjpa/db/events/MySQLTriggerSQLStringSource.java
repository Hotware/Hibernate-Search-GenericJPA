/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.db.events;

import org.hibernate.search.genericjpa.db.events.EventModelInfo.IdInfo;

/**
 * Implementation of a {@link TriggerSQLStringSource} that can be used with MySQL (or compatible) Databases. <br>
 * <br>
 * In order to provide uniqueness between the Update tables it uses a procedure that generates unique ids. This
 * procedure does this with auxilliary table that only has a autoincrement id. A row is inserted everytime a unique id
 * is needed and that id is retrieved via MySQLs last_insert_id() and then returned
 *
 * @author Martin Braun
 */
public class MySQLTriggerSQLStringSource implements TriggerSQLStringSource {

	public static final String DEFAULT_UNIQUE_ID_TABLE_NAME = "`_____unique____id____hsearch`";
	public static final String DEFAULT_UNIQUE_ID_PROCEDURE_NAME = "get_unique_id_hsearch";

	private static final String CREATE_TRIGGER_ORIGINAL_TABLE_SQL_FORMAT = "" + "CREATE TRIGGER %s AFTER %s ON %s                 \n"
			+ "FOR EACH ROW                                                                                                       \n"
			+ "BEGIN                                                                                                              \n"
			+ "    CALL %s(@unique_id);                                                                                           \n"
			+ "    INSERT INTO %s(id, %s, %s)                                                                                     \n"
			+ "		VALUES(@unique_id, %s, %s);                                                                                   \n"
			+ "END;                                                                                                               \n";
	private static final String CREATE_TRIGGER_CLEANUP_SQL_FORMAT = "" + "CREATE TRIGGER %s AFTER DELETE ON %s                    \n"
			+ "FOR EACH ROW                                                                                                       \n"
			+ "BEGIN                                                                                                              \n"
			+ "DELETE FROM #UNIQUE_ID_TABLE_NAME# WHERE id = OLD.id;                                                              \n"
			+ "END;                                                                                                               \n";
	private static final String DROP_TRIGGER_SQL_FORMAT = "" + "DROP TRIGGER IF EXISTS %s;\n";

	private final String uniqueIdTableName;
	private final String uniqueIdProcedureName;

	// we don't support dropping the unique_id_table_name
	// because otherwise we would lose information about the last used
	// ids
	private String createTriggerCleanUpSQLFormat;
	private String createUniqueIdTable;
	private String dropUniqueIdTable;
	private String dropUniqueIdProcedure;
	private String createUniqueIdProcedure;

	public MySQLTriggerSQLStringSource() {
		this( DEFAULT_UNIQUE_ID_TABLE_NAME, DEFAULT_UNIQUE_ID_PROCEDURE_NAME );
	}

	public MySQLTriggerSQLStringSource(String uniqueIdTableName, String uniqueIdProcedureName) {
		this.uniqueIdTableName = uniqueIdTableName;
		this.uniqueIdProcedureName = uniqueIdProcedureName;
		this.init();
	}

	private void init() {
		this.createUniqueIdTable = String.format( "CREATE TABLE IF NOT EXISTS %s (                                                 \n"
				+ "id BIGINT(64) NOT NULL AUTO_INCREMENT,                                                                          \n"
				+ " PRIMARY KEY (id)                                                                                               \n"
				+ ");                                                                                                              \n", this.uniqueIdTableName );
		this.dropUniqueIdTable = String.format( "DROP TABLE IF EXISTS %s;", this.uniqueIdTableName );
		this.dropUniqueIdProcedure = String.format( "DROP PROCEDURE IF EXISTS %s;                                                  \n",
				this.uniqueIdProcedureName );
		this.createUniqueIdProcedure = String.format( "CREATE PROCEDURE %s                                                         \n"
				+ "(OUT ret BIGINT)                                                                                                \n"
				+ "BEGIN                                                                                                           \n"
				+ "	INSERT INTO %s VALUES ();                                                                                      \n"
				+ "	SET ret = last_insert_id();                                                                                    \n"
				+ "END;                                                                                                            \n",
				this.uniqueIdProcedureName, this.uniqueIdTableName );
		this.createTriggerCleanUpSQLFormat = CREATE_TRIGGER_CLEANUP_SQL_FORMAT.replaceAll( "#UNIQUE_ID_TABLE_NAME#", this.uniqueIdTableName );
	}

	@Override
	public String[] getSetupCode() {
		return new String[] { this.createUniqueIdTable, this.dropUniqueIdProcedure, this.createUniqueIdProcedure };
	}

	@Override
	public String[] getTriggerCreationCode(EventModelInfo eventModelInfo, int eventType) {
		String originalTableName = eventModelInfo.getOriginalTableName();
		String triggerName = this.getTriggerName( eventModelInfo.getOriginalTableName(), eventType );
		String tableName = eventModelInfo.getTableName();
		String eventTypeColumn = eventModelInfo.getEventTypeColumn();
		StringBuilder valuesFromOriginal = new StringBuilder();
		StringBuilder idColumnNames = new StringBuilder();
		int addedVals = 0;
		for ( IdInfo idInfo : eventModelInfo.getIdInfos() ) {
			for ( int i = 0; i < idInfo.getColumns().length; ++i ) {
				if ( addedVals > 0 ) {
					valuesFromOriginal.append( ", " );
					idColumnNames.append( ", " );
				}
				if ( eventType == EventType.DELETE ) {
					valuesFromOriginal.append( "OLD." );
				}
				else {
					valuesFromOriginal.append( "NEW." );
				}
				valuesFromOriginal.append( idInfo.getColumnsInOriginal()[i] );
				idColumnNames.append( idInfo.getColumns()[i] );
				++addedVals;
			}
		}
		if ( addedVals == 0 ) {
			throw new IllegalArgumentException( "eventModelInfo didn't contain any idInfos" );
		}
		String eventTypeValue = String.valueOf( eventType );
		String createTriggerOriginalTableSQL = new StringBuilder().append(
				String.format( CREATE_TRIGGER_ORIGINAL_TABLE_SQL_FORMAT, triggerName, EventType.toString( eventType ), originalTableName,
						uniqueIdProcedureName, tableName, eventTypeColumn, idColumnNames.toString(), eventTypeValue, valuesFromOriginal.toString() ) )
				.toString();
		return new String[] { createTriggerOriginalTableSQL };
	}

	@Override
	public String[] getTriggerDropCode(EventModelInfo eventModelInfo, int eventType) {
		String triggerName = this.getTriggerName( eventModelInfo.getOriginalTableName(), eventType );
		return new String[] { String.format( DROP_TRIGGER_SQL_FORMAT, triggerName ).toUpperCase() };
	}

	private String getTriggerName(String originalTableName, int eventType) {
		return new StringBuilder().append( originalTableName ).append( "_updates_hsearch_" ).append( EventType.toString( eventType ) ).toString();
	}

	private String getCleanUpTriggerName(String updatesTableName) {
		return new StringBuilder().append( updatesTableName ).append( "_cleanup_hsearch" ).toString();
	}

	@Override
	public String[] getSpecificSetupCode(EventModelInfo eventModelInfo) {
		String createTriggerCleanUpSQL = String.format( this.createTriggerCleanUpSQLFormat, this.getCleanUpTriggerName( eventModelInfo.getTableName() ),
				eventModelInfo.getTableName() );
		return new String[] { createTriggerCleanUpSQL };
	}

	@Override
	public String[] getSpecificUnSetupCode(EventModelInfo eventModelInfo) {
		return new String[] { String.format( DROP_TRIGGER_SQL_FORMAT, this.getCleanUpTriggerName( eventModelInfo.getTableName() ) ) };
	}

	@Override
	public String[] getRecreateUniqueIdTableCode() {
		return new String[] { this.dropUniqueIdTable, this.createUniqueIdTable };
	}

}
