/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.test.jpa.entities;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.Transient;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.search.annotations.ContainedIn;
import org.hibernate.search.annotations.DocumentId;
import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.Index;
import org.hibernate.search.annotations.Indexed;
import org.hibernate.search.annotations.IndexedEmbedded;
import org.hibernate.search.annotations.Store;
import org.hibernate.search.genericjpa.annotations.IdColumn;
import org.hibernate.search.genericjpa.annotations.IdInfo;
import org.hibernate.search.genericjpa.annotations.InIndex;
import org.hibernate.search.genericjpa.annotations.UpdateInfo;
import org.hibernate.search.genericjpa.db.events.ColumnType;

@Entity
@Table(name = "PLACE")
@Indexed
@InIndex
@UpdateInfo(tableName = "PLACE", updateTableIdColumn = "updateid", updateTableEventTypeColumn = "eventCase", updateTableName = "PlaceUpdatesHsearch", idInfos = {
		@IdInfo(columns = @IdColumn(column = "ID", updateTableColumn = "placefk", columnType = ColumnType.INTEGER))
})
public class Place {

	private Integer id;
	private String name;
	private Set<Sorcerer> sorcerers = new HashSet<>();
	private boolean cool = true;

	@Id
	@Column(name = "ID")
	@DocumentId
	@GeneratedValue(strategy = GenerationType.AUTO)
	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	@Column
	public boolean isCool() {
		return this.cool;
	}

	public void setCool(boolean cool) {
		this.cool = cool;
	}

	@Field(store = Store.YES, index = Index.YES)
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@IndexedEmbedded(depth = 3, includeEmbeddedObjectId = true)
	@ContainedIn
	@OneToMany(cascade = CascadeType.ALL, mappedBy = "place")
	public Set<Sorcerer> getSorcerers() {
		return sorcerers;
	}

	public void setSorcerers(Set<Sorcerer> sorcerers) {
		this.sorcerers = sorcerers;
	}

	@Override
	public String toString() {
		return "Place [id=" + this.getId() + ", name=" + this.getName() + ", sorcerers=" + sorcerers + "]";
	}

}
