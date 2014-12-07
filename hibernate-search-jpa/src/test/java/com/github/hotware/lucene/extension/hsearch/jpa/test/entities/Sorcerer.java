package com.github.hotware.lucene.extension.hsearch.jpa.test.entities;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

import org.hibernate.search.annotations.ContainedIn;
import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.Index;
import org.hibernate.search.annotations.IndexedEmbedded;
import org.hibernate.search.annotations.Store;

import com.github.hotware.lucene.extension.hsearch.jpa.event.HSearchJPAEventListener;

@Entity
@EntityListeners({HSearchJPAEventListener.class})
public class Sorcerer {

	private Integer id;
	private String name;
	private Place place;

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	@Field(store = Store.NO, index = Index.YES)
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@Override
	public String toString() {
		return "Sorcerer [id=" + id + ", name=" + name + "]";
	}

	@ContainedIn
	@ManyToOne(cascade = CascadeType.ALL)
	@IndexedEmbedded
	public Place getPlace() {
		return place;
	}

	public void setPlace(Place place) {
		this.place = place;
	}

}