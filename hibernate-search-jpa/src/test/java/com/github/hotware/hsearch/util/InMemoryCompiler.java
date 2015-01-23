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
package com.github.hotware.hsearch.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

/**
 * most (or all code) is from <a href=
 * "http://normalengineering.com.au/normality/c-and-java-code-snippets/java-runtime-compilation-in-memory/"
 * >here</a>
 * 
 * @author Martin Braun
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class InMemoryCompiler {

	/* Compiles the provided source code and returns the resulting Class object. */
	public static Class compile(String source, String className) {

		Class clazz = null; // default

		/* Create a list of compilation units (ie Java sources) to compile. */
		List compilationUnits = Arrays.asList(new CompilationUnit(className,
				source));

		/*
		 * The diagnostic listener gives you a way of examining the source when
		 * the compile fails. You don't need it, but it makes debugging easier.
		 */
		DiagnosticCollector diagnosticListener = new DiagnosticCollector();

		/*
		 * Get a Java compiler to use. (If this returns null there is a good
		 * chance you're using a JRE instead of a JDK.)
		 */
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

		/* Set up the target file manager and call the compiler. */
		SingleFileManager singleFileManager = new SingleFileManager(compiler,
				new ByteCode(className));
		JavaCompiler.CompilationTask compile = compiler.getTask(null,
				singleFileManager, diagnosticListener, null, null,
				compilationUnits);

		if (!compile.call()) {
			/* Compilation failed: Output the compiler errors to stderr. */
			for (Diagnostic diagnostic : (List<Diagnostic>) diagnosticListener
					.getDiagnostics()) {
				System.err.println(diagnostic);
			}
		} else {
			/* Compilation succeeded: Get the Class object. */
			try {
				clazz = singleFileManager.getClassLoader().findClass(className);
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
		}

		return clazz;
	}

	/* Container for a Java compilation unit (ie Java source) in memory. */
	private static class CompilationUnit extends SimpleJavaFileObject {

		public CompilationUnit(String className, String source) {
			super(URI.create("file:///" + className + ".java"), Kind.SOURCE);
			source_ = source;
		}

		@Override
		public CharSequence getCharContent(boolean ignoreEncodingErrors) {
			return source_;
		}

		@Override
		public OutputStream openOutputStream() {
			throw new IllegalStateException();
		}

		@Override
		public InputStream openInputStream() {
			return new ByteArrayInputStream(source_.getBytes());
		}

		private final String source_;
	}

	/* Container for Java byte code in memory. */
	private static class ByteCode extends SimpleJavaFileObject {

		public ByteCode(String className) {
			super(URI.create("byte:///" + className + ".class"), Kind.CLASS);
		}

		@Override
		public CharSequence getCharContent(boolean ignoreEncodingErrors) {
			return null;
		}

		@Override
		public OutputStream openOutputStream() {
			byteArrayOutputStream_ = new ByteArrayOutputStream();
			return byteArrayOutputStream_;
		}

		@Override
		public InputStream openInputStream() {
			return null;
		}

		public byte[] getByteCode() {
			return byteArrayOutputStream_.toByteArray();
		}

		private ByteArrayOutputStream byteArrayOutputStream_;
	}

	/* A file manager for a single class. */
	private static class SingleFileManager extends ForwardingJavaFileManager {

		public SingleFileManager(JavaCompiler compiler, ByteCode byteCode) {
			super(compiler.getStandardFileManager(null, null, null));
			singleClassLoader_ = new SingleClassLoader(byteCode);
		}

		@Override
		public JavaFileObject getJavaFileForOutput(Location notUsed,
				String className, JavaFileObject.Kind kind, FileObject sibling)
				throws IOException {
			return singleClassLoader_.getFileObject();
		}

		@Override
		public ClassLoader getClassLoader(Location location) {
			return singleClassLoader_;
		}

		public SingleClassLoader getClassLoader() {
			return singleClassLoader_;
		}

		private final SingleClassLoader singleClassLoader_;
	}

	/* A class loader for a single class. */
	private static class SingleClassLoader extends ClassLoader {

		public SingleClassLoader(ByteCode byteCode) {
			byteCode_ = byteCode;
		}

		@Override
		protected Class findClass(String className)
				throws ClassNotFoundException {
			return defineClass(className, byteCode_.getByteCode(), 0,
					byteCode_.getByteCode().length);
		}

		ByteCode getFileObject() {
			return byteCode_;
		}

		private final ByteCode byteCode_;
	}

}
