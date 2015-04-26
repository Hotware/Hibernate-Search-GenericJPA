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
package com.github.hotware.hsearch.metadata;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hibernate.annotations.common.reflection.java.JavaReflectionManager;
import org.hibernate.search.cfg.spi.SearchConfiguration;
import org.hibernate.search.engine.impl.ConfigContext;
import org.hibernate.search.engine.metadata.impl.AnnotationMetadataProvider;
import org.hibernate.search.engine.metadata.impl.MetadataProvider;
import org.hibernate.search.engine.metadata.impl.TypeMetadata;
import org.junit.Before;
import org.junit.Test;

import com.github.hotware.hsearch.factory.SearchConfigurationImpl;
import com.github.hotware.hsearch.testutil.BuildContextForTest;

/**
 * @author Martin Braun
 */
public class MetadataRehasherTest {

	private MetadataProvider metadataProvider;
	private MetadataRehasher metadataRehasher;

	@Before
	public void setup() {
		SearchConfiguration searchConfiguration = new SearchConfigurationImpl();
		ConfigContext configContext = new ConfigContext(searchConfiguration,
				new BuildContextForTest(searchConfiguration));
		metadataProvider = new AnnotationMetadataProvider(
				new JavaReflectionManager(), configContext);
		this.metadataRehasher = new MetadataRehasher();
	}

	@Test
	public void test() {
		TypeMetadata fromRoot = this.metadataProvider
				.getTypeMetadataFor(RootEntity.class);
		RehashedTypeMetadata fromRootRehashed = this.metadataRehasher
				.rehash(fromRoot);
		{

			assertEquals(fromRoot, fromRootRehashed.getOriginalTypeMetadata());

			// THE ID FIELD NAMES
			{
				Map<Class<?>, List<String>> idFieldNamesForType = fromRootRehashed
						.getIdFieldNamesForType();

				assertEquals(3, idFieldNamesForType.get(RootEntity.class)
						.size());
				assertTrue(idFieldNamesForType.get(RootEntity.class).contains(
						"MAYBE_ROOT_NOT_NAMED_ID"));
				assertTrue(idFieldNamesForType.get(RootEntity.class).contains(
						"recursiveSelf.MAYBE_ROOT_NOT_NAMED_ID"));
				assertTrue(idFieldNamesForType.get(RootEntity.class).contains(
						"recursiveSelf.recursiveSelf.MAYBE_ROOT_NOT_NAMED_ID"));

				assertEquals(2, idFieldNamesForType.get(SubEntity.class).size());
				assertTrue(idFieldNamesForType.get(SubEntity.class).contains(
						"otherEntity.SUB_NOT_NAMED_ID"));
				assertTrue(idFieldNamesForType.get(SubEntity.class).contains(
						"recursiveSelf.otherEntity.SUB_NOT_NAMED_ID"));
			}

			// THE ID PROPERTY NAMES
			{
				assertEquals("rootId", fromRootRehashed
						.getIdPropertyNameForType().get(RootEntity.class));
				assertEquals("subId", fromRootRehashed
						.getIdPropertyNameForType().get(SubEntity.class));
			}

			// THE DOCUMENT_FIELD_META_DATA
			{
				assertEquals(5, fromRootRehashed
						.getDocumentFieldMetadataForIdFieldName().size());
				// make sure all of these are different
				assertEquals(5,
						new HashSet<>(fromRootRehashed
								.getDocumentFieldMetadataForIdFieldName()
								.values()).size());
			}
		}

		TypeMetadata fromAnotherRoot = this.metadataProvider
				.getTypeMetadataFor(AnotherRootEntity.class);
		RehashedTypeMetadata fromAnotherRootRehashed = this.metadataRehasher
				.rehash(fromAnotherRoot);

		Set<Class<?>> indexRelevantEntities = MetadataUtil.calculateIndexRelevantEntities(Arrays.asList(
				fromRootRehashed, fromAnotherRootRehashed));
		assertEquals(3, indexRelevantEntities.size());
		assertTrue(indexRelevantEntities.contains(RootEntity.class));
		assertTrue(indexRelevantEntities.contains(AnotherRootEntity.class));
		assertTrue(indexRelevantEntities.contains(SubEntity.class));
		
		Map<Class<?>, List<Class<?>>> inIndexOf = MetadataUtil.calculateInIndexOf(Arrays.asList(
				fromRootRehashed, fromAnotherRootRehashed));
		assertEquals(1, inIndexOf.get(RootEntity.class).size());
		assertTrue(inIndexOf.get(RootEntity.class).contains(RootEntity.class));
		
		assertEquals(1, inIndexOf.get(AnotherRootEntity.class).size());
		assertTrue(inIndexOf.get(AnotherRootEntity.class).contains(AnotherRootEntity.class));
		
		assertEquals(2, inIndexOf.get(SubEntity.class).size());
		assertTrue(inIndexOf.get(SubEntity.class).contains(RootEntity.class));
		assertTrue(inIndexOf.get(SubEntity.class).contains(AnotherRootEntity.class));
	}
}
