/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package com.github.hotware.hsearch.db.events;

/**
 * Classes that implement this interface provide means to create the Triggers needed on the database to write C_UD
 * information about entities in the index into the specific Updates-Table.
 *
 * @author Martin Braun
 */
public interface TriggerSQLStringSource {

	/**
	 * EXPERT: this returns the drop and recreate code (in the right order) for dropping the unique id table. you have
	 * to either use this when you are sure no updates to the index are made or you will have to recreate the whole
	 * index after this has been called (don't forget to clear the updates tables before you do that. dont do it after
	 * the massindexing process or your index might lose information!). This is a really expensive process and should
	 * only be done if your database is running out of ids for all the update instances. However, this shouldn't be a
	 * real world problem :D http://stackoverflow.com/questions/277608/is-bigint-large-enough-for-an- event-log-table
	 */
	String[] getRecreateUniqueIdTableCode();

	/**
	 * this is executed first
	 */
	String[] getSetupCode();

	/**
	 * this has to be executed before every call to getTriggerCreationCode
	 *
	 * @param eventModelInfo the EventModelInfo/type this corresponds to
	 */
	String[] getSpecificSetupCode(EventModelInfo eventModelInfo);

	/**
	 * this removes all changes made by {@link #getSpecificSetupCode(EventModelInfo)}
	 *
	 * @param eventModelInfo the EventModelInfo/type this corresponds to
	 */
	String[] getSpecificUnSetupCode(EventModelInfo eventModelInfo);

	/**
	 * this creates a specific trigger
	 *
	 * @param eventModelInfo the EventModelInfo/type this corresponds to
	 * @param eventType see {@link EventType}
	 */
	String[] getTriggerCreationCode(EventModelInfo eventModelInfo, int eventType);

	/**
	 * this removes a specific trigger created by {@link #getTriggerCreationCode(EventModelInfo, int)}
	 *
	 * @param eventModelInfo the EventModelInfo/type this corresponds to
	 * @param eventType see {@link EventType}
	 */
	String[] getTriggerDropCode(EventModelInfo eventModelInfo, int eventType);

}
