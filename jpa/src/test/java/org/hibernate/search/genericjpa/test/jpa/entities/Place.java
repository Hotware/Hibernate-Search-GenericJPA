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
@UpdateInfo(tableName = "PLACE", idInfos = {
		@IdInfo(columns = @IdColumn(column = "ID", columnType = ColumnType.INTEGER))
})
public class Place {

	private Integer id;
	private String name;
	private Set<Sorcerer> sorcerers = new HashSet<>();
	private List<AdditionalPlace> additionalPlace;
	private List<EmbeddableInfo> info;
	private boolean cool = true;
	private List<OneToManyWithoutTable> oneToManyWithoutTable;
	private JoinTableOneToOne jtoto;

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
	@OneToMany(cascade = CascadeType.ALL)
	@JoinTable(name = "Place_Sorcerer")
	@UpdateInfo(tableName = "Place_Sorcerer", updateTableName = "PlaceSorcererUpdatesHsearch", updateTableIdColumn = "updateid", updateTableEventTypeColumn = "eventCase", idInfos = {
			@IdInfo(entity = Place.class, columns = @IdColumn(column = "Place_ID", updateTableColumn = "placefk", columnType = ColumnType.INTEGER)),
			@IdInfo(entity = Sorcerer.class, columns = @IdColumn(column = "Sorcerer_ID", updateTableColumn = "sorcererfk", columnType = ColumnType.INTEGER))
	})
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

	@ManyToMany(cascade = CascadeType.ALL)
	@IndexedEmbedded(includeEmbeddedObjectId = true)
	public List<AdditionalPlace> getAdditionalPlace() {
		return additionalPlace;
	}

	public void setAdditionalPlace(List<AdditionalPlace> additionalPlace) {
		this.additionalPlace = additionalPlace;
	}

	// TODO: test with this
	// @ElementCollection
	// @CollectionTable(name = "PHONE", joinColumns = @JoinColumn(name =
	// "OWNER_ID"))
	@Transient
	public List<EmbeddableInfo> getInfo() {
		return info;
	}

	public void setInfo(List<EmbeddableInfo> info) {
		this.info = info;
	}

	@OneToMany(mappedBy = "place")
	@JoinColumn(name = "place_id", referencedColumnName = "place_id")
	public List<OneToManyWithoutTable> getOneToManyWithoutTable() {
		return oneToManyWithoutTable;
	}

	public void setOneToManyWithoutTable(List<OneToManyWithoutTable> oneToManyWithoutTable) {
		this.oneToManyWithoutTable = oneToManyWithoutTable;
	}

	@OneToOne
	@JoinTable(name = "PLACE_JTOTO", joinColumns = @JoinColumn(name = "PLACE_ID", referencedColumnName = "ID"), inverseJoinColumns = @JoinColumn(name = "JTOTO_ID", referencedColumnName = "ID"))
	public JoinTableOneToOne getJtoto() {
		return jtoto;
	}

	public void setJtoto(JoinTableOneToOne jtoto) {
		this.jtoto = jtoto;
	}

}
