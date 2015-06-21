/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.test.entities;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.Table;
import java.io.Serializable;
import java.util.List;

import org.hibernate.search.annotations.Analyze;
import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.Index;
import org.hibernate.search.annotations.Indexed;
import org.hibernate.search.annotations.IndexedEmbedded;
import org.hibernate.search.annotations.Store;
import org.hibernate.search.genericjpa.annotations.InIndex;

@InIndex
@Indexed
@Entity
@Table(name = "Game")
public class Game implements Serializable {

	private static final long serialVersionUID = 1L;

	private Long id;
	private String title;
	private List<Vendor> vendors;

	public Game() {

	}

	public Game(String title) {
		this.title = title;
	}

	@Id
	@GeneratedValue
	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	@Field(store = Store.YES, index = Index.YES, analyze = Analyze.NO)
	@Column
	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	@IndexedEmbedded(includeEmbeddedObjectId = true, targetElement = Vendor.class)
	@ManyToMany
	public List<Vendor> getVendors() {
		return vendors;
	}

	public void setVendors(List<Vendor> vendors) {
		this.vendors = vendors;
	}

	@Override
	public String toString() {
		return "Game [id=" + this.getId() + ", title=" + this.getTitle() + ", vendors=" + this.getVendors() + "]";
	}

}
