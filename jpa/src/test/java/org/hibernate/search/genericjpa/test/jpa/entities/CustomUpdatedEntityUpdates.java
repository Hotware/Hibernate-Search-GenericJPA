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
import org.hibernate.search.genericjpa.annotations.Hint;
import org.hibernate.search.genericjpa.annotations.IdFor;
import org.hibernate.search.genericjpa.annotations.Updates;

/**
 * Created by Martin on 08.07.2015.
 */
@Entity
@Table(name = "CustomUpdatedEntityUpdates")
@Updates(tableName = "CustomUpdatedEntityUpdates", originalTableName = "CustomUpdatedEntity")
public class CustomUpdatedEntityUpdates {

	@Id
	private Long id;

	@IdFor(entityClass = CustomUpdatedEntity.class, columns = "customUpdatedEntityId", columnsInOriginal = "id",
			hints = @Hint(key = "test", value = "toast"))
	@Column(name = "customUpdatedEntityId")
	private Long customUpdatedEntityId;

	@Event(column = "eventType")
	@Column(name = "eventType")
	private Integer eventType;

	public Integer getEventType() {
		return this.eventType;
	}

	public CustomUpdatedEntityUpdates setEventType(Integer eventType) {
		this.eventType = eventType;
		return this;
	}

	public Long getCustomUpdatedEntityId() {
		return this.customUpdatedEntityId;
	}

	public CustomUpdatedEntityUpdates setCustomUpdatedEntityId(Long customUpdatedEntityId) {
		this.customUpdatedEntityId = customUpdatedEntityId;
		return this;
	}

	public Long getId() {
		return this.id;
	}

	public CustomUpdatedEntityUpdates setId(Long id) {
		this.id = id;
		return this;
	}
}
