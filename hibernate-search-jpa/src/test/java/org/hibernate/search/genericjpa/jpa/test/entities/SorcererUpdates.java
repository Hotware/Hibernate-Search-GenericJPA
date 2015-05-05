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
 * @author Martin Braun
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
	 * @param sorcererId the sorcererId to set
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
	 * @param id the id to set
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
		StringBuilder builder = new StringBuilder();
		builder.append( "SorcererUpdates [id=" ).append( id ).append( ", sorcererId=" ).append( sorcererId ).append( ", eventType=" ).append( eventType )
				.append( "]" );
		return builder.toString();
	}

}
