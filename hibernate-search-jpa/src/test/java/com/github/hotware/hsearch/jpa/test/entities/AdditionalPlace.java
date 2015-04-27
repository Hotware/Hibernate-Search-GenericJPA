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
import org.hibernate.search.annotations.Index;
import org.hibernate.search.annotations.IndexedEmbedded;
import org.hibernate.search.annotations.Store;

import com.github.hotware.hsearch.annotations.InIndex;

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
