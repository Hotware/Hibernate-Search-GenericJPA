/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.test.jpa.entities;

import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import org.hibernate.search.genericjpa.annotations.Event;
import org.hibernate.search.genericjpa.annotations.IdFor;
import org.hibernate.search.genericjpa.annotations.Updates;
import org.hibernate.search.genericjpa.db.id.DefaultToOriginalIdBridge;

@Entity
@Table(name = "MultipleColumnsIdEntityUpdates")
@Updates(tableName = "MultipleColumnsIdEntityUpdates", originalTableName = "MultipleColumnsIdEntity")
public class MultipleColumnsIdEntityUpdates {

	@Id
	private Long id;

	@Embedded
	@AttributeOverrides({
			@AttributeOverride(name = "firstId", column = @Column(name = "firstIdFk")),
			@AttributeOverride(name = "secondId", column = @Column(name = "secondIdFk"))
	})
	@IdFor(entityClass = MultipleColumnsIdEntity.class, columns = {"firstIdFk", "secondIdFk"}, columnsInOriginal = {
			"firstId",
			"secondId"
	}, bridge = DefaultToOriginalIdBridge.class)
	private ID multipleColumnsIdEntityId;

	@Event(column = "eventCase")
	@Column(name = "eventCase")
	private Integer eventCase;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public ID getMultipleColumnsIdEntityId() {
		return multipleColumnsIdEntityId;
	}

	public void setMultipleColumnsIdEntityId(ID multipleColumnsIdEntityId) {
		this.multipleColumnsIdEntityId = multipleColumnsIdEntityId;
	}

	public Integer getEventCase() {
		return eventCase;
	}

	public void setEventCase(Integer eventCase) {
		this.eventCase = eventCase;
	}
}
