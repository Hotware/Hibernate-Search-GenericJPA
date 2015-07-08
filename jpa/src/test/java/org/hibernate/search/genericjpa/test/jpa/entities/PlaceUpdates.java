/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.test.jpa.entities;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import org.hibernate.search.genericjpa.annotations.Event;
import org.hibernate.search.genericjpa.annotations.IdFor;
import org.hibernate.search.genericjpa.annotations.Updates;

/**
 * @author Martin
 */
@Entity
@Table(name = "PLACEUPDATES")
@Updates(tableName = "PLACEUPDATES", originalTableName = "PLACE")
public class PlaceUpdates {

	@Id
	private Integer id;

	@IdFor(entityClass = Place.class, columns = "placeId", columnsInOriginal = "id")
	@Column(name = "placeId")
	private Integer placeId;

	@Event(column = "eventType")
	@Column(name = "eventType")
	private Integer eventType;

	/**
	 * @return the id
	 */
	public Integer getId() {
		return id;
	}

	/**
	 * @param id the id to set
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
	 * @param placeId the placeId to set
	 */
	public void setPlaceId(Integer placeId) {
		this.placeId = placeId;
	}

	/**
	 * @return the eventType
	 */
	public Integer getEventType() {
		return eventType;
	}

	/**
	 * @param eventType the eventType to set
	 */
	public void setEventType(Integer eventType) {
		this.eventType = eventType;
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "PlaceUpdates [id=" + id + ", placeId=" + placeId + ", eventType=" + eventType + "]";
	}

}
