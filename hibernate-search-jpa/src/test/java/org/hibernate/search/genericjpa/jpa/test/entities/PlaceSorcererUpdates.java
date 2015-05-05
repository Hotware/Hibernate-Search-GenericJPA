/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.jpa.test.entities;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

import org.hibernate.search.genericjpa.db.events.annotations.Event;
import org.hibernate.search.genericjpa.db.events.annotations.IdFor;
import org.hibernate.search.genericjpa.db.events.annotations.Updates;

/**
 * @author Martin
 */
@Entity
@Updates(tableName = "PlaceSorcererUpdates", originalTableName = "Place_Sorcerer")
public class PlaceSorcererUpdates {

	@Id
	private Long id;

	@IdFor(entityClass = Place.class, columns = "placeId", columnsInOriginal = "Place_ID")
	@Column
	private Integer placeId;

	@IdFor(entityClass = Sorcerer.class, columns = "sorcererId", columnsInOriginal = "sorcerers_ID")
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
	 * @param sorcererId the sorcererId to set
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
	 * @param eventType the eventType to set
	 */
	public void setEventType(Integer eventType) {
		this.eventType = eventType;
	}

	/**
	 * @return the id
	 */
	public Long getId() {
		return id;
	}

	/**
	 * @param id the id to set
	 */
	public void setId(Long id) {
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

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "PlaceSorcererUpdates [id=" + id + ", placeId=" + placeId + ", sorcererId=" + sorcererId + ", eventType=" + eventType + "]";
	}

}
