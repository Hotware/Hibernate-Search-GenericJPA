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

import com.github.hotware.hsearch.db.events.UpdateConsumer;
import com.github.hotware.hsearch.db.events.UpdateSource;

/**
 * @author Martin
 *
 */
public class JPAUpdateSource implements UpdateSource {

	private UpdateConsumer updateConsumer;

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.github.hotware.hsearch.db.events.UpdateSource#setUpdateConsumer(com
	 * .github.hotware.hsearch.db.events.UpdateConsumer)
	 */
	@Override
	public void setUpdateConsumer(UpdateConsumer updateConsumer) {
		this.updateConsumer = updateConsumer;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.github.hotware.hsearch.db.events.UpdateSource#start()
	 */
	@Override
	public void start() {
		if(this.updateConsumer == null) {
			throw new IllegalStateException("updateConsumer was null!");
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.github.hotware.hsearch.db.events.UpdateSource#stop()
	 */
	@Override
	public void stop() {
		
	}

}