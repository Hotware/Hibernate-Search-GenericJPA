/*
 * Copyright 2015 Martin Braun
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.hotware.hsearch.jpa.test.entities;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.OneToMany;
import javax.persistence.Transient;

import org.hibernate.search.annotations.ContainedIn;
import org.hibernate.search.annotations.DocumentId;
import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.Index;
import org.hibernate.search.annotations.Indexed;
import org.hibernate.search.annotations.IndexedEmbedded;
import org.hibernate.search.annotations.Store;

import com.github.hotware.hsearch.annotations.InIndex;
import com.github.hotware.hsearch.jpa.event.HSearchJPAEventListener;

@Entity
@Indexed
@EntityListeners({HSearchJPAEventListener.class})
@InIndex
public class Place {

	private Integer id;
	private String name;
	private Set<Sorcerer> sorcerers = new HashSet<>();
	private List<AdditionalPlace> additionalPlace;
	private List<EmbeddableInfo> info;
	private boolean cool = true;

	@Id
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

	@Field(store = Store.NO, index = Index.YES)
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@IndexedEmbedded(depth = 3)
	@ContainedIn
	@OneToMany(cascade = CascadeType.ALL)
	public Set<Sorcerer> getSorcerers() {
		return sorcerers;
	}

	public void setSorcerers(Set<Sorcerer> sorcerers) {
		this.sorcerers = sorcerers;
	}

	@Override
	public String toString() {
		return "Place [id=" + this.getId() + ", name=" + this.getName() + ", sorcerers="
				+ sorcerers + "]";
	}

	@ManyToMany(cascade = CascadeType.ALL)
	@IndexedEmbedded
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

}