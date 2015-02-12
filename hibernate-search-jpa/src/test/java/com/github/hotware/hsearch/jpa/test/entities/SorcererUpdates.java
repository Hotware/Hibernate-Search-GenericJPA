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
package com.github.hotware.hsearch.jpa.test.entities;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

import com.github.hotware.hsearch.db.events.annotations.Event;
import com.github.hotware.hsearch.db.events.annotations.IdFor;
import com.github.hotware.hsearch.db.events.annotations.Updates;

/**
 * @author Martin Braun
 *
 */
@Entity
@Updates(tableName = "SorcererUpdates", originalTableName = "Sorcerer")
public class SorcererUpdates {

	@Id
	private Long id;

	@IdFor(entityClass = Sorcerer.class, columns = "sorcererId", columnsInOriginal = "id")
	@Column
	private Integer sorcererId;

	@Event(column = "eventType")
	@Column
	private Integer eventType;

	/**
	 * @return the sorcererId
	 */
	public Integer getSorcererId() {
		return sorcererId;
	}

	/**
	 * @param sorcererId
	 *            the sorcererId to set
	 */
	public void setSorcererId(Integer sorcererId) {
		this.sorcererId = sorcererId;
	}

	/**
	 * @return the id
	 */
	public Long getId() {
		return id;
	}

	/**
	 * @param id
	 *            the id to set
	 */
	public void setId(Long id) {
		this.id = id;
	}

	/**
	 * @return the eventType
	 */
	public Integer getEventType() {
		return eventType;
	}

	/**
	 * @param eventType
	 *            the eventType to set
	 */
	public void setEventType(Integer eventType) {
		this.eventType = eventType;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("SorcererUpdates [id=").append(id)
				.append(", sorcererId=").append(sorcererId)
				.append(", eventType=").append(eventType).append("]");
		return builder.toString();
	}

}
