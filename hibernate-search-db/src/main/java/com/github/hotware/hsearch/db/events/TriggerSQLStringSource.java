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

/**
 * @author Martin
 *
 */
public interface TriggerSQLStringSource {

	/**
	 * EXPERT: this returns the drop and recreate code (in the right order) for
	 * dropping the unique id table. you have to either use this when you are
	 * sure no updates to the index are made or you will have to recreate the
	 * whole index after this has been called (don't forget to clear the updates
	 * tables before you do that. dont do it after the massindexing process or
	 * your index might lose information!). This is a really expensive process
	 * and should only be done if your database is running out of ids for all
	 * the update instances.
	 */
	public String[] getRecreateUniqueIdTableCode();

	public String[] getSetupCode();

	public String[] getSpecificSetupCode(EventModelInfo eventModelInfo);

	public String[] getSpecificUnSetupCode(EventModelInfo eventModelInfo);

	public String[] getTriggerCreationCode(EventModelInfo eventModelInfo,
			int eventType);

	public String[] getTriggerDropCode(EventModelInfo eventModelInfo,
			int eventType);

}
