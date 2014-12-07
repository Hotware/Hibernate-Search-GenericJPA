package com.github.hotware.lucene.extension.hsearch.dto;

import com.github.hotware.lucene.extension.hsearch.dto.DtoDescriptor.DtoDescription;
import com.github.hotware.lucene.extension.hsearch.dto.annotations.DtoField;
import com.github.hotware.lucene.extension.hsearch.dto.annotations.DtoOverEntity;

import junit.framework.TestCase;

public class DtoDescriptorTest extends TestCase {

	// the value of entityClass isn't that important in this test
	// but we want to check if it's set properly in the resulting
	// DtoDescription
	@DtoOverEntity(entityClass = B.class)
	public static class A {

		@DtoField(fieldName = "toastFieldName", profileName = "toast")
		@DtoField
		String fieldOne;

		@DtoField
		String fieldTwo;

	}
	
	public static class B {
		
	}

	public void testDescriptor() {
		DtoDescriptor descriptor = new DtoDescriptorImpl();
		DtoDescription description = descriptor.getDtoDescription(A.class);
		assertEquals(A.class, description.getDtoClass());
		assertEquals(B.class, description.getEntityClass());
		assertEquals(1, description.getFieldDescriptionsForProfile("toast").size());
		assertEquals("toastFieldName",
				description.getFieldDescriptionsForProfile("toast").get(0)
						.getFieldName());
		assertEquals(
				2,
				description.getFieldDescriptionsForProfile(
						DtoDescription.DEFAULT_PROFILE).size());
		assertEquals(
				"fieldOne",
				description
						.getFieldDescriptionsForProfile(DtoDescription.DEFAULT_PROFILE)
						.get(0).getFieldName());
		assertEquals(
				"fieldTwo",
				description
						.getFieldDescriptionsForProfile(DtoDescription.DEFAULT_PROFILE)
						.get(1).getFieldName());
	}
}
