/*
 * Copyright 2015 Martin Braun
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.hotware.hsearch.dto;

import com.github.hotware.hsearch.dto.DtoDescriptor;
import com.github.hotware.hsearch.dto.DtoDescriptorImpl;
import com.github.hotware.hsearch.dto.DtoDescriptor.DtoDescription;
import com.github.hotware.hsearch.dto.annotations.DtoField;
import com.github.hotware.hsearch.dto.annotations.DtoOverEntity;

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
