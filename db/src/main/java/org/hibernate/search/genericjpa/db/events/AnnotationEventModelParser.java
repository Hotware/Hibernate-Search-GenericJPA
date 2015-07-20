/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.db.events;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hibernate.search.genericjpa.annotations.Hint;
import org.hibernate.search.genericjpa.annotations.IdInfo;
import org.hibernate.search.genericjpa.annotations.UpdateInfo;
import org.hibernate.search.genericjpa.db.id.IdConverter;
import org.hibernate.search.genericjpa.exception.SearchException;

/**
 * Created by Martin on 20.07.2015.
 */
public class AnnotationEventModelParser implements EventModelParser {

	@Override
	public List<EventModelInfo> parse(Set<Class<?>> updateClasses) {
		ArrayList<Class<?>> l = new ArrayList<>( updateClasses.size() );
		l.addAll( updateClasses );
		return this.parse( l );
	}

	@Override
	public List<EventModelInfo> parse(List<Class<?>> updateClasses) {
		List<EventModelInfo> ret = new ArrayList<>( updateClasses.size() );
		Set<String> handledOriginalTableNames = new HashSet<>( updateClasses.size() );
		for ( Class<?> clazz : updateClasses ) {
			{
				UpdateInfo[] classUpdateInfos = clazz.getAnnotationsByType( UpdateInfo.class );
				this.addUpdateInfosToList( ret, clazz, classUpdateInfos, handledOriginalTableNames );
			}

			for ( Method method : clazz.getDeclaredMethods() ) {
				UpdateInfo[] methodUpdateInfos = method.getAnnotationsByType( UpdateInfo.class );
				this.addUpdateInfosToList( ret, null, methodUpdateInfos, handledOriginalTableNames );
			}

			for ( Field field : clazz.getDeclaredFields() ) {
				UpdateInfo[] fieldUpdateInfos = field.getAnnotationsByType( UpdateInfo.class );
				this.addUpdateInfosToList( ret, null, fieldUpdateInfos, handledOriginalTableNames );
			}
		}
		return ret;
	}

	private void addUpdateInfosToList(
			List<EventModelInfo> eventModelInfos,
			Class<?> classSpecifiedOn,
			UpdateInfo[] infos,
			Set<String> handledOriginalTableNames) {
		for ( UpdateInfo info : infos ) {
			String originalTableName = info.tableName();
			if ( handledOriginalTableNames.contains( originalTableName ) ) {
				throw new SearchException( "multiple @UpdateInfo specified for table " + originalTableName );
			}
			else {
				handledOriginalTableNames.add( originalTableName );
			}
			String updateTableName = info.updateTableName().equals( "" ) ?
					originalTableName + "hsearchupdates" :
					info.tableName();
			String eventCaseColumn = info.updateTableEventCaseColumn().equals( "" ) ?
					"eventcasehsearchupdates" :
					info.updateTableEventCaseColumn();
			String updateIdColumn = info.updateTableIdColumn().equals( "" ) ?
					"updatetableidcolumnhsearchupdates" :
					info.updateTableIdColumn();

			IdInfo[] annotationIdInfos = info.idInfos();
			List<EventModelInfo.IdInfo> idInfos = new ArrayList<>( annotationIdInfos.length );
			//now handle all the IdInfos
			for ( IdInfo annotationIdInfo : annotationIdInfos ) {
				final Class<?> idInfoEntityClass;
				if ( annotationIdInfo.entity().equals( void.class ) ) {
					if ( classSpecifiedOn == null ) {
						throw new SearchException( "IdInfo.entity must be specified for the member level!" );
					}
					idInfoEntityClass = classSpecifiedOn;
				}
				else {
					idInfoEntityClass = annotationIdInfo.entity();
				}

				final String[] columns = annotationIdInfo.columns();
				final String[] updateTableColumns;
				if ( annotationIdInfo.updateTableColumns().length != 0 ) {
					if ( annotationIdInfo.updateTableColumns().length != columns.length ) {
						throw new SearchException(
								"the length of IdInfo.updateTableColumns must be equal to IdInfo.columns"
						);
					}
					updateTableColumns = annotationIdInfo.updateTableColumns();
				}
				else {
					updateTableColumns = new String[columns.length];
					for ( int i = 0; i < columns.length; ++i ) {
						updateTableColumns[i] = columns[i] + "fk";
					}
				}

				IdConverter idConverter = null;
				if ( annotationIdInfo.type() != IdInfo.IdType.NONE ) {
					if ( !annotationIdInfo.idConverter().equals( IdConverter.class ) ) {
						throw new SearchException( "please specify either IdInfo.type OR IdInfo.idConverter" );
					}
					idConverter = annotationIdInfo.type();
				}
				else {
					if ( annotationIdInfo.idConverter().equals( IdConverter.class ) ) {
						throw new SearchException( "please specify either IdInfo.type OR IdInfo.idConverter" );
					}
					try {
						idConverter = annotationIdInfo.idConverter().newInstance();
					}
					catch (InstantiationException | IllegalAccessException e) {
						throw new SearchException( e );
					}
				}

				Map<String, String> hints = new HashMap<>();
				for ( Hint hint : annotationIdInfo.hints() ) {
					hints.put( hint.key(), hint.value() );
				}

				idInfos.add(
						new EventModelInfo.IdInfo(
								idInfoEntityClass,
								updateTableColumns,
								columns,
								idConverter,
								hints
						)
				);
			}

			EventModelInfo evi = new EventModelInfo(
					updateTableName,
					originalTableName,
					eventCaseColumn,
					updateIdColumn,
					idInfos
			);
			eventModelInfos.add( evi );
		}
	}

}
