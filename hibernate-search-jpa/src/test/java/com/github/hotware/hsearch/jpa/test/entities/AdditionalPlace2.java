package com.github.hotware.hsearch.jpa.test.entities;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToOne;

import org.hibernate.search.annotations.ContainedIn;
import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.Index;
import org.hibernate.search.annotations.Store;

import com.github.hotware.hsearch.jpa.event.HSearchJPAEventListener;

@Entity
@EntityListeners(HSearchJPAEventListener.class)
public class AdditionalPlace2 {
	
	private Integer id;
	private AdditionalPlace additionalPlace;
	private String info;
	
	public void setId(Integer id) {
		this.id = id;
	}

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	public Integer getId() {
		return id;
	}
	
	@OneToOne(mappedBy="additionalPlace2")
	@ContainedIn
	public AdditionalPlace getAdditionalPlace() {
		return additionalPlace;
	}

	public void setAdditionalPlace(AdditionalPlace additionalPlace) {
		this.additionalPlace = additionalPlace;
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