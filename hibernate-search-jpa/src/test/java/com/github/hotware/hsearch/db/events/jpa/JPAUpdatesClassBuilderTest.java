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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.List;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.junit.Test;

import com.github.hotware.hsearch.db.events.jpa.JPAUpdatesClassBuilder.IdColumn;
import com.github.hotware.hsearch.jpa.test.entities.Place;

/**
 * @author Martin
 *
 */
public class JPAUpdatesClassBuilderTest {

	@Test
	public void test() throws Exception {
		JPAUpdatesClassBuilder builder = new JPAUpdatesClassBuilder();
		ByteArrayOutputStream bas = new ByteArrayOutputStream(1000);
		PrintStream ps = new PrintStream(bas);
		builder.tableName("tableName")
				.originalTableName("originalTableName")
				.idColumn(
						IdColumn.of(Long.class, true, Place.class,
								new String[] { "placeId" },
								new String[] { "Place_ID" }))
				.build(ps, "pack", "MyUpdateClass");
		String asString = bas.toString("UTF-8");
		System.out.println(asString);
		this.loadClass("pack", "MyUpdateClass", asString);
		// String tmpLoc = "pack/MyUpdateClass.java";
		// new File("pack").mkdirs();
		// try (FileOutputStream fos = new FileOutputStream(new File(tmpLoc))) {
		// bas.writeTo(fos);
		// }
		// bas.close();
		// JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
		// int result = javaCompiler.run(null, null, null, tmpLoc);
		// // success if 0
		// assertEquals(0, result);
		//
		// Class.forName("pack.MyUpdateClass");

		// assertTrue(new File(tmpLoc).delete());
		// assertTrue(new File("pack/MyUpdateClass.class").delete());
		// assertTrue(new File("pack").delete());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Class<?> loadClass(String pack, String className, String contents)
			throws Exception {
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		DiagnosticCollector diagnosticsCollector = new DiagnosticCollector();
		StandardJavaFileManager fileManager = compiler.getStandardFileManager(
				diagnosticsCollector, null, null);
		JavaObjectFromString obj = new JavaObjectFromString(className, contents);
		List<JavaObjectFromString> fileObjects = Arrays.asList(obj);
		{
			CompilationTask task = compiler.getTask(null, fileManager,
					diagnosticsCollector, null, null, fileObjects);
			Boolean result = task.call();
			List<Diagnostic> diagnostics = diagnosticsCollector
					.getDiagnostics();
			for (Diagnostic diagnostic : diagnostics) {
				System.out.println(diagnostic.getCode());
				System.out.println(diagnostic.getKind());
				System.out.println(diagnostic.getPosition());
				System.out.println(diagnostic.getStartPosition());
				System.out.println(diagnostic.getEndPosition());
				System.out.println(diagnostic.getSource());
				System.out.println(diagnostic.getMessage(null));
			}
			if (result == true) {
				System.out.println("Compilation has succeeded");
			} else {
				System.out.println("Compilation fails.");
			}
		}
		//FIXME:
//		return Class.forName(className);
		return null;
	}

	static class JavaObjectFromString extends SimpleJavaFileObject {
		private String contents = null;

		public JavaObjectFromString(String className, String contents)
				throws Exception {
			super(new URI("string:///" + className + Kind.SOURCE.extension),
					Kind.SOURCE);
			this.contents = contents;
		}

		public CharSequence getCharContent(boolean ignoreEncodingErrors)
				throws IOException {
			return contents;
		}
	}

}
