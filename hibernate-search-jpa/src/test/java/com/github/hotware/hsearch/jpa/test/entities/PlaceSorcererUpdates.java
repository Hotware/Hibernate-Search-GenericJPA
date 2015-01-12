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
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

import com.github.hotware.hsearch.db.events.annotations.Event;
import com.github.hotware.hsearch.db.events.annotations.IdFor;
import com.github.hotware.hsearch.db.events.annotations.Updates;

/**
 * @author Martin
 */
@Entity
@Updates(tableName = "PlaceSorcererUpdates", originalTableName = "Place_Sorcerer")
public class PlaceSorcererUpdates {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@IdFor(entityClass = Place.class, columns = "placeId", columnsInOriginal = "Place_ID")
	@Column
	private Integer placeId;

	@IdFor(entityClass = Sorcerer.class, columns = "sorcererId", columnsInOriginal = "sorcerers_ID")
	@Column
	private Integer sorcererId;

	@Event
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

	/**
	 * @return the id
	 */
	public Integer getId() {
		return id;
	}

	/**
	 * @param id
	 *            the id to set
	 */
	public void setId(Integer id) {
		this.id = id;
	}

	/**
	 * @return the placeId
	 */
	public Integer getPlaceId() {
		return placeId;
	}

	/**
	 * @param placeId
	 *            the placeId to set
	 */
	public void setPlaceId(Integer placeId) {
		this.placeId = placeId;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "PlaceSorcererUpdates [id=" + id + ", placeId=" + placeId
				+ ", sorcererId=" + sorcererId + ", eventType=" + eventType
				+ "]";
	}

}
