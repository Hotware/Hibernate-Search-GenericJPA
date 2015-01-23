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
package com.github.hotware.hsearch.db.events.jpa;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.github.hotware.hsearch.db.events.EventModelInfo;
import com.github.hotware.hsearch.db.events.EventModelParser;
import com.github.hotware.hsearch.db.events.jpa.JPAUpdatesClassBuilder.IdColumn;
import com.github.hotware.hsearch.db.id.DefaultToOriginalIdBridge;
import com.github.hotware.hsearch.jpa.test.entities.Place;
import com.github.hotware.hsearch.util.InMemoryCompiler;

/**
 * @author Martin
 *
 */
public class JPAUpdatesClassBuilderTest {

	@Test
	public void testCompileAndAnnotations() throws Exception {
		String asString = this.buildCode("");
		Class<?> clazz = InMemoryCompiler.compile(asString, "MyUpdateClass");
		EventModelParser parser = new EventModelParser();
		List<EventModelInfo> infos = parser.parse(Arrays.asList(clazz));
		EventModelInfo info = infos.get(0);
		assertTrue(info.getEventTypeAccessor() != null);
		assertEquals("hsearchEventCase", info.getEventTypeColumn());
		assertEquals("originalTableName", info.getOriginalTableName());
		assertEquals("tableName", info.getTableName());
		assertEquals(clazz, info.getUpdateClass());

		EventModelInfo.IdInfo idInfo = info.getIdInfos().get(0);
		assertEquals("placeId", idInfo.getColumns()[0]);
		assertEquals("Place_ID", idInfo.getColumnsInOriginal()[0]);
		assertEquals(Place.class, idInfo.getEntityClass());
		assertEquals(DefaultToOriginalIdBridge.class, idInfo
				.getToOriginalBridge().getClass());
		// we just pass what we want here as the DefaultBridge just returns the
		// value it is passed
		assertEquals(123123, idInfo.getToOriginalBridge().toOriginal(123123));
		assertEquals(null, idInfo.getIdAccessor().apply(clazz.newInstance()));
	}

	@Test
	public void checkPackage() throws IOException {
		String code = this.buildCode("pack");
		System.out.println(code);
		assertTrue(code.startsWith("package pack;"));
	}

	private String buildCode(String pack) throws IOException {
		JPAUpdatesClassBuilder builder = new JPAUpdatesClassBuilder();
		ByteArrayOutputStream bas = new ByteArrayOutputStream(1000);
		PrintStream ps = new PrintStream(bas);
		builder.tableName("tableName")
				.originalTableName("originalTableName")
				.idColumn(
						IdColumn.of(Long.class, true, Place.class,
								new String[] { "placeId" },
								new String[] { "Place_ID" }))
				.build(ps, pack, "MyUpdateClass");
		String asString = bas.toString("UTF-8");
		return asString;
	}

}
