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
package com.github.hotware.hsearch.db.events;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.github.hotware.hsearch.db.events.annotations.Case;
import com.github.hotware.hsearch.db.events.annotations.IdFor;
import com.github.hotware.hsearch.db.events.annotations.Updates;

/**
 * @author Martin
 *
 */
public class EventModelParser {

	public List<EventModelInfo> parse(List<Class<?>> updateClasses) {
		List<EventModelInfo> ret = new ArrayList<>();
		for (Class<?> clazz : updateClasses) {
			Updates updates = clazz.getAnnotation(Updates.class);
			java.lang.reflect.Member caseMember = null;
			List<EventModelInfo.IdInfo> idInfos = new ArrayList<>();
			if (updates != null) {
				ParseMembersReturn forFields;
				{
					List<Field> fields = new ArrayList<>();
					for (Field field : clazz.getDeclaredFields()) {
						field.setAccessible(true);
						fields.add(field);
					}
					ParseMembersReturn pmr = this.parseMembers(clazz, fields,
							idInfos);
					forFields = pmr;
					if (forFields.foundAnything()) {
						if (!forFields.foundBoth()) {
							throw new IllegalArgumentException(
									"you have to annotate either Fields OR Methods with both @IdFor AND @Case");
						}
						if (pmr.caseMember != null) {
							caseMember = pmr.caseMember;
						}
					}
				}
				{
					List<Method> methods = new ArrayList<>();
					for (Method method : clazz.getDeclaredMethods()) {
						method.setAccessible(true);
						methods.add(method);
					}
					ParseMembersReturn pmr = this.parseMembers(clazz, methods,
							idInfos);
					if (forFields.foundAnything() && pmr.foundAnything()) {
						throw new IllegalArgumentException(
								"you have to either annotate Fields or Methods with @Case "
										+ "and @IdFor, not both");
					}
					if (pmr.foundAnything()) {
						if (!pmr.foundBoth()) {
							throw new IllegalArgumentException(
									"you have to annotate either Fields OR Methods with both @IdFor AND @Case");
						}
						if (pmr.caseMember != null) {
							caseMember = pmr.caseMember;
						}
					}
				}
			} else {
				throw new IllegalArgumentException(
						"Updates class does not host @Updates. Class: " + clazz);
			}
			if (caseMember == null) {
				throw new IllegalArgumentException(
						"no Integer Field found hosting @Case in Class: "
								+ clazz
								+ ". check if your Fields OR Methods are correctly annotated!");
			}
			if (idInfos.size() == 0) {
				throw new IllegalArgumentException(
						"@Updates-class does not host @IdInfo: "
								+ clazz
								+ ". check if your Fields OR Methods are correctly annotated!");
			}

			// TODO: Exception for wrong values
			final Member caseMemberFinal = caseMember;
			Function<Object, Integer> caseAccessor = (Object object) -> {
				try {
					if (caseMemberFinal instanceof Method) {
						return (Integer) ((Method) caseMemberFinal)
								.invoke(object);
					} else if (caseMemberFinal instanceof Field) {
						return (Integer) ((Field) caseMemberFinal).get(object);
					} else {
						throw new AssertionError();
					}
				} catch (IllegalAccessException | IllegalArgumentException
						| InvocationTargetException e) {
					throw new RuntimeException(e);
				}
			};

			ret.add(new EventModelInfo(updates.tableName(), updates
					.originalTableName(), caseAccessor, idInfos));

		}
		return ret;
	}

	private static class ParseMembersReturn {
		Member caseMember;
		boolean foundIdInfos;

		public boolean foundAnything() {
			return this.caseMember != null || this.foundIdInfos;
		}

		public boolean foundBoth() {
			return this.caseMember != null && this.foundIdInfos;
		}

	}

	private ParseMembersReturn parseMembers(Class<?> clazz,
			List<? extends Member> members, List<EventModelInfo.IdInfo> idInfos) {
		ParseMembersReturn ret = new ParseMembersReturn();
		for (Member member : members) {
			IdFor idFor = this.getAnnotation(member, IdFor.class);
			Case eventCase = this.getAnnotation(member, Case.class);
			if (idFor != null && eventCase != null) {
				throw new IllegalArgumentException(
						"@IdFor and @Case can not be on the same Field. Class: "
								+ clazz + ". Member: " + member);
			}
			if (eventCase != null) {
				if (!this.getType(member).equals(Integer.class)) {
					throw new IllegalArgumentException(
							"Field hosting @Case is no Field of type Integer.  Class: "
									+ clazz + ". Field: " + member);
				}
				if (ret.caseMember == null) {
					ret.caseMember = member;
				} else {
					throw new IllegalArgumentException(
							"class cannot have two caseFields. Class: " + clazz);
				}
			}
			if (idFor != null) {
				ret.foundIdInfos = true;
				Function<Object, Object> idAccessor = (Object object) -> {
					try {
						if (member instanceof Method) {
							return ((Method) member).invoke(object);
						} else if (member instanceof Field) {
							return ((Field) member).get(object);
						} else {
							throw new AssertionError();
						}
					} catch (IllegalAccessException | IllegalArgumentException
							| InvocationTargetException e) {
						throw new RuntimeException(e);
					}
				};
				// TODO: Exception for wrong values
				EventModelInfo.IdInfo idInfo = new EventModelInfo.IdInfo(
						idAccessor, idFor.entityClass(), idFor.columns(),
						idFor.columnsInOriginal());
				idInfos.add(idInfo);
			}
		}
		return ret;
	}

	private <T extends Annotation> T getAnnotation(Member member,
			Class<T> annotationClass) {
		T ret;
		if (member instanceof Method) {
			Method method = (Method) member;
			ret = method.getAnnotation(annotationClass);
		} else if (member instanceof Field) {
			Field field = (Field) member;
			ret = field.getAnnotation(annotationClass);
		} else {
			throw new AssertionError("member should either be Field or Member");
		}
		return ret;
	}

	private Class<?> getType(Member member) {
		Class<?> ret;
		if (member instanceof Method) {
			Method method = (Method) member;
			ret = method.getReturnType();
		} else if (member instanceof Field) {
			Field field = (Field) member;
			ret = field.getType();
		} else {
			throw new AssertionError("member should either be Field or Member");
		}
		return ret;
	}

}
