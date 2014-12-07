package com.github.hotware.lucene.extension.hsearch.jpa.test.entities;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.PostUpdate;

@Embeddable
public class EmbeddableInfo {
	
	private int ownerId;
	
	@PostUpdate()
	public void postUpdateEmbeddableInfo() {
		System.out.println("embeddableInfo: PostUpdate");
	}
	
	private String info;

	@Column
	public String getInfo() {
		return info;
	}

	public void setInfo(String info) {
		this.info = info;
	}

	@Column(name = "OWNER_ID")
	public int getOwnerId() {
		return ownerId;
	}

	public void setOwnerId(int ownerId) {
		this.ownerId = ownerId;
	}

}
