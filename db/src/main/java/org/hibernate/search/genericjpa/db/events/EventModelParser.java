/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.db.events;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.hibernate.search.genericjpa.annotations.Event;
import org.hibernate.search.genericjpa.annotations.Hint;
import org.hibernate.search.genericjpa.annotations.IdFor;
import org.hibernate.search.genericjpa.annotations.Updates;
import org.hibernate.search.genericjpa.db.id.ToOriginalIdBridge;
import org.hibernate.search.genericjpa.exception.AssertionFailure;
import org.hibernate.search.genericjpa.exception.SearchException;

/**
 * This class has means to parse Classes annotated with {@link Updates} into their respective representation as a
 * {@link EventModelInfo}. <br>
 * <br>
 * It also checks the classes for the right annotations and does basic integrity checking of this information
 *
 * @author Martin
 */
public class EventModelParser {

	/**
	 * @return EventModelInfos in "random" order
	 */
	public List<EventModelInfo> parse(Set<Class<?>> updateClasses) {
		ArrayList<Class<?>> l = new ArrayList<>();
		l.addAll( updateClasses );
		return this.parse( l );
	}

	/**
	 * @return EventModelInfos in the same order as in the list argument
	 */
	public List<EventModelInfo> parse(List<Class<?>> updateClasses) {
		List<EventModelInfo> ret = new ArrayList<>();
		for ( Class<?> clazz : updateClasses ) {
			Updates updates = clazz.getAnnotation( Updates.class );
			java.lang.reflect.Member eventTypeMember = null;
			String eventTypeColumn = null;
			List<EventModelInfo.IdInfo> idInfos = new ArrayList<>();
			if ( updates != null ) {
				ParseMembersReturn forFields;
				{
					List<Field> fields = new ArrayList<>();
					for ( Field field : clazz.getDeclaredFields() ) {
						field.setAccessible( true );
						fields.add( field );
					}
					ParseMembersReturn pmr = this.parseMembers( clazz, fields, idInfos );
					forFields = pmr;
					if ( forFields.foundAnything() ) {
						if ( !forFields.foundBoth() ) {
							throw new IllegalArgumentException(
									"you have to annotate either Fields OR Methods with both @IdFor AND @Event"
							);
						}
						if ( pmr.eventTypeMember != null ) {
							eventTypeMember = pmr.eventTypeMember;
							eventTypeColumn = pmr.eventTypeColumn;
						}
					}
				}
				{
					List<Method> methods = new ArrayList<>();
					for ( Method method : clazz.getDeclaredMethods() ) {
						method.setAccessible( true );
						methods.add( method );
					}
					ParseMembersReturn pmr = this.parseMembers( clazz, methods, idInfos );
					if ( forFields.foundAnything() && pmr.foundAnything() ) {
						throw new IllegalArgumentException( "you have to either annotate Fields or Methods with @Event " + "and @IdFor, not both" );
					}
					if ( pmr.foundAnything() ) {
						if ( !pmr.foundBoth() ) {
							throw new IllegalArgumentException(
									"you have to annotate either Fields OR Methods with both @IdFor AND @Event"
							);
						}
						if ( pmr.eventTypeMember != null ) {
							eventTypeMember = pmr.eventTypeMember;
							eventTypeColumn = pmr.eventTypeColumn;
						}
					}
				}
			}
			else {
				throw new IllegalArgumentException( "Updates class does not host @Updates. Class: " + clazz );
			}
			if ( eventTypeMember == null ) {
				throw new IllegalArgumentException(
						"no Integer Field found hosting @Event in Class: " + clazz
								+ ". check if your Fields OR Methods are correctly annotated!"
				);
			}
			if ( idInfos.size() == 0 ) {
				throw new IllegalArgumentException(
						"@Updates-class does not host @IdInfo: " + clazz
								+ ". check if your Fields OR Methods are correctly annotated!"
				);
			}

			// TODO: Exception for wrong values
			final Member eventTypeMemberFinal = eventTypeMember;
			Function<Object, Integer> eventTypeAccessor = (Object object) -> {
				try {
					if ( eventTypeMemberFinal instanceof Method ) {
						return (Integer) ((Method) eventTypeMemberFinal).invoke( object );
					}
					else if ( eventTypeMemberFinal instanceof Field ) {
						return (Integer) ((Field) eventTypeMemberFinal).get( object );
					}
					else {
						throw new AssertionFailure( "" );
					}
				}
				catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
					throw new SearchException( e );
				}
			};
			ret.add(
					new EventModelInfo(
							clazz,
							updates.tableName(),
							updates.originalTableName(),
							eventTypeAccessor,
							eventTypeColumn,
							idInfos
					)
			);

		}
		return ret;
	}

	private ParseMembersReturn parseMembers(
			Class<?> clazz,
			List<? extends Member> members,
			List<EventModelInfo.IdInfo> idInfos) {
		ParseMembersReturn ret = new ParseMembersReturn();
		for ( Member member : members ) {
			IdFor idFor = this.getAnnotation( member, IdFor.class );
			Event event = this.getAnnotation( member, Event.class );
			if ( idFor != null && event != null ) {
				throw new IllegalArgumentException( "@IdFor and @Event can not be on the same Field. Class: " + clazz + ". Member: " + member );
			}
			if ( event != null ) {
				if ( !this.getType( member ).equals( Integer.class ) ) {
					throw new IllegalArgumentException( "Field hosting @Event is no Field of type Integer.  Class: " + clazz + ". Field: " + member );
				}
				if ( ret.eventTypeMember == null ) {
					ret.eventTypeMember = member;
					ret.eventTypeColumn = event.column();
				}
				else {
					throw new IllegalArgumentException( "class cannot have two @Event members. Class: " + clazz );
				}
			}
			if ( idFor != null ) {
				ret.foundIdInfos = true;
				ToOriginalIdBridge toOriginalBridge;
				try {
					toOriginalBridge = idFor.bridge().newInstance();
				}
				catch (IllegalAccessException | InstantiationException e) {
					throw new SearchException( e );
				}
				Function<Object, Object> idAccessor = (Object object) -> {
					Object val;
					try {
						if ( member instanceof Method ) {
							val = ((Method) member).invoke( object );
						}
						else if ( member instanceof Field ) {
							val = ((Field) member).get( object );
						}
						else {
							throw new AssertionFailure( "" );
						}
					}
					catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
						throw new SearchException( e );
					}
					return toOriginalBridge.toOriginal( val );
				};
				if ( idFor.columns().length != idFor.columnsInOriginal().length ) {
					throw new IllegalArgumentException(
							"the count of IdFor-columns in the update table has to "
									+ "match the count of Id-columns in the original"
					);
				}
				Map<String, String> hints = new HashMap<>();
				for ( Hint hint : idFor.hints() ) {
					hints.put( hint.key(), hint.value() );
				}
				EventModelInfo.IdInfo idInfo = new EventModelInfo.IdInfo(
						idAccessor, idFor.entityClass(), idFor.columns(), idFor.columnsInOriginal(), toOriginalBridge,
						Collections.unmodifiableMap( hints )
				);
				idInfos.add( idInfo );
			}
		}
		return ret;
	}

	private <T extends Annotation> T getAnnotation(Member member, Class<T> annotationClass) {
		T ret;
		if ( member instanceof Method ) {
			Method method = (Method) member;
			ret = method.getAnnotation( annotationClass );
		}
		else if ( member instanceof Field ) {
			Field field = (Field) member;
			ret = field.getAnnotation( annotationClass );
		}
		else {
			throw new AssertionFailure( "member should either be Field or Member" );
		}
		return ret;
	}

	private Class<?> getType(Member member) {
		Class<?> ret;
		if ( member instanceof Method ) {
			Method method = (Method) member;
			ret = method.getReturnType();
		}
		else if ( member instanceof Field ) {
			Field field = (Field) member;
			ret = field.getType();
		}
		else {
			throw new AssertionFailure( "member should either be Field or Member" );
		}
		return ret;
	}

	private static class ParseMembersReturn {

		Member eventTypeMember;
		boolean foundIdInfos;
		String eventTypeColumn;

		public boolean foundAnything() {
			return this.eventTypeMember != null || this.foundIdInfos;
		}

		public boolean foundBoth() {
			return this.eventTypeMember != null && this.foundIdInfos;
		}

	}

}
