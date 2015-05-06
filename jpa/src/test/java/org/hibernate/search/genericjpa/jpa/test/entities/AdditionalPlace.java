/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.jpa.test.entities;

import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.OneToOne;

import org.hibernate.search.annotations.ContainedIn;
import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.InIndex;
import org.hibernate.search.annotations.Index;
import org.hibernate.search.annotations.IndexedEmbedded;
import org.hibernate.search.annotations.Store;

@Entity
@InIndex
public class AdditionalPlace {

	private Integer id;
	private String info;
	private List<Place> place;
	private AdditionalPlace2 additionalPlace2;

	public void setId(Integer id) {
		this.id = id;
	}

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	public Integer getId() {
		return id;
	}

	@IndexedEmbedded(includeEmbeddedObjectId = true)
	@OneToOne(cascade = CascadeType.ALL, optional = true)
	public AdditionalPlace2 getAdditionalPlace2() {
		return additionalPlace2;
	}

	public void setAdditionalPlace2(AdditionalPlace2 additionalPlace2) {
		this.additionalPlace2 = additionalPlace2;
	}

	@ContainedIn
	@ManyToMany(cascade = CascadeType.ALL, mappedBy = "additionalPlace")
	public List<Place> getPlace() {
		return place;
	}

	public void setPlace(List<Place> place) {
		this.place = place;
	}

	@Column
	@Field(store = Store.YES, index = Index.YES)
	public String getInfo() {
		return info;
	}

	public void setInfo(String info) {
		this.info = info;
	}

}
