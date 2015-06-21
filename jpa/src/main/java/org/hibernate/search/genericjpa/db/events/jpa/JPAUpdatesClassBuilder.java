/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.db.events.jpa;

import javax.lang.model.element.Modifier;
import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

import org.hibernate.search.genericjpa.annotations.Event;
import org.hibernate.search.genericjpa.annotations.IdFor;
import org.hibernate.search.genericjpa.annotations.Updates;
import org.hibernate.search.genericjpa.db.id.DefaultToOriginalIdBridge;
import org.hibernate.search.genericjpa.db.id.ToOriginalIdBridge;

/**
 * @author Martin Braun
 */
@Deprecated
public class JPAUpdatesClassBuilder {

	private String tableName;
	private String originalTableName;
	private Set<IdColumn> idColumns = new HashSet<>();

	private static String arrayStringAnnotationFormat(int size) {
		if ( size <= 0 ) {
			throw new IllegalArgumentException( "size must be greater than 0" );
		}
		StringBuilder builder = new StringBuilder( "{" );
		for ( int i = 0; i < size; ++i ) {
			if ( i > 0 ) {
				builder.append( ", " );
			}
			builder.append( "$S" );
		}
		return builder.append( "}" ).toString();
	}

	public JPAUpdatesClassBuilder tableName(String tableName) {
		this.tableName = tableName;
		return this;
	}

	public JPAUpdatesClassBuilder originalTableName(String originalTableName) {
		this.originalTableName = originalTableName;
		return this;
	}

	public JPAUpdatesClassBuilder idColumn(IdColumn idColumn) {
		this.idColumns.add( idColumn );
		return this;
	}

	/**
	 * @return the tableName
	 */
	public String getTableName() {
		return tableName;
	}

	/**
	 * @param tableName the tableName to set
	 */
	public void setTableName(String tableName) {
		this.tableName = tableName;
	}

	/**
	 * @return the originalTableName
	 */
	public String getOriginalTableName() {
		return originalTableName;
	}

	/**
	 * @param originalTableName the originalTableName to set
	 */
	public void setOriginalTableName(String originalTableName) {
		this.originalTableName = originalTableName;
	}

	/**
	 * @return the idColumns
	 */
	public Set<IdColumn> getIdColumns() {
		return idColumns;
	}

	/**
	 * @param idColumns the idColumns to set
	 */
	public void setIdColumns(Set<IdColumn> idColumns) {
		this.idColumns = idColumns;
	}

	public void build(PrintStream out, String packageName, String className) throws IOException {
		if ( this.tableName == null ) {
			throw new IllegalStateException( "tableName was not set!" );
		}
		if ( this.originalTableName == null ) {
			throw new IllegalStateException( "originalTableName was not set!" );
		}
		if ( this.tableName.equals( this.originalTableName ) ) {
			throw new IllegalStateException( "tableName must not be equal to originalTableName" );
		}
		TypeSpec.Builder builder = TypeSpec
				.classBuilder( className )
				.addModifiers( Modifier.PUBLIC )
				.addAnnotation(
						AnnotationSpec.builder( Table.class )
								.addMember( "name", "$S", this.tableName )
								.build()
				)
				.addAnnotation( Entity.class )
				.addAnnotation(
						AnnotationSpec.builder( Updates.class ).addMember( "tableName", "$S", this.tableName )
								.addMember( "originalTableName", "$S", this.originalTableName ).build()
				)
				.addField(
						FieldSpec.builder( Long.class, "id", Modifier.PRIVATE )
								.addAnnotation( AnnotationSpec.builder( Id.class ).build() )
								.build()
				)
				.addField(
						FieldSpec.builder( Integer.class, "hsearchEventCase", Modifier.PRIVATE )
								.addAnnotation(
										AnnotationSpec.builder( Column.class ).addMember(
												"name",
												"$S",
												"hsearchEventCase"
										).build()
								)
								.addAnnotation(
										AnnotationSpec.builder( Event.class ).addMember(
												"column",
												"$S",
												"hsearchEventCase"
										).build()
								).build()
				)
				.addMethod(
						MethodSpec.methodBuilder( "getId" ).returns( Long.class ).addModifiers( Modifier.PUBLIC )
								.addCode( CodeBlock.builder().addStatement( "return this.id" ).build() ).build()
				);
		int i = 0;
		for ( IdColumn idColumn : this.idColumns ) {
			FieldSpec.Builder fieldBuilder = FieldSpec.builder(
					idColumn.idClass,
					String.format( "%s_%s", idColumn.entityClass.getSimpleName().toLowerCase(), i++ ), Modifier.PRIVATE
			);
			if ( idColumn.nonEmbeddedType ) {
				fieldBuilder.addAnnotation(
						AnnotationSpec.builder( Column.class ).addMember(
								"name",
								"$S",
								idColumn.columns[0]
						).build()
				);
			}
			else {
				// FIXME: fix this to properly support Embedded stuff
				// (AttributeOverrides)
				fieldBuilder.addAnnotation( Embedded.class );
			}
			fieldBuilder.addAnnotation(
					AnnotationSpec.builder( IdFor.class ).addMember( "entityClass", "$T.class", idColumn.entityClass )
							.addMember(
									"columns",
									arrayStringAnnotationFormat( idColumn.columns.length ),
									(Object[]) idColumn.columns
							)
							.addMember(
									"columnsInOriginal",
									arrayStringAnnotationFormat( idColumn.columnsInOriginal.length ),
									(Object[]) idColumn.columnsInOriginal
							)
							.addMember( "bridge", "$T.class", idColumn.toOriginalIdBridge ).build()
			);
			builder.addField( fieldBuilder.build() );
		}
		JavaFile javaFile = JavaFile.builder( packageName, builder.build() ).build();
		javaFile.writeTo( out );
	}

	public static final class IdColumn {

		private Class<?> idClass;
		private boolean nonEmbeddedType;
		private Class<?> entityClass;
		private String[] columns;
		private String[] columnsInOriginal;
		private Class<? extends ToOriginalIdBridge> toOriginalIdBridge;

		public IdColumn(
				Class<?> idClass,
				boolean nonEmbeddedType,
				Class<?> entityClass,
				String[] columns,
				String[] columnsInOriginal,
				Class<? extends ToOriginalIdBridge> toOriginalIdBridge) {
			super();
			if ( nonEmbeddedType ) {
				if ( columns.length != 1 ) {
					throw new IllegalArgumentException( "if type is nonEmbedded, there has to be exactly one column" );
				}
				if ( columnsInOriginal.length != 1 ) {
					throw new IllegalArgumentException(
							"if type is nonEmbedded, there has to be exactly one column in the original"
					);
				}
			}
			this.idClass = idClass;
			this.nonEmbeddedType = nonEmbeddedType;
			this.entityClass = entityClass;
			this.columns = columns;
			this.columnsInOriginal = columnsInOriginal;
			this.toOriginalIdBridge = toOriginalIdBridge;
		}

		public IdColumn(
				Class<?> idClass,
				boolean nonEmbeddedType,
				Class<?> entityClass,
				String[] columns,
				String[] columnsInOriginal) {
			this( idClass, nonEmbeddedType, entityClass, columns, columnsInOriginal, DefaultToOriginalIdBridge.class );
		}

		public static IdColumn of(
				Class<?> idClass,
				boolean nonEmbeddedType,
				Class<?> entityClass,
				String[] columns,
				String[] columnsInOriginal) {
			return new IdColumn( idClass, nonEmbeddedType, entityClass, columns, columnsInOriginal );
		}

		public static IdColumn of(
				Class<?> idClass,
				boolean nonEmbeddedType,
				Class<?> entityClass,
				String[] columns,
				String[] columnsInOriginal,
				Class<? extends ToOriginalIdBridge> toOriginalIdBridge) {
			return new IdColumn(
					idClass,
					nonEmbeddedType,
					entityClass,
					columns,
					columnsInOriginal,
					toOriginalIdBridge
			);
		}

		/**
		 * @return the idClass
		 */
		public Class<?> getIdClass() {
			return idClass;
		}

		/**
		 * @param idClass the idClass to set
		 */
		public void setIdClass(Class<?> idClass) {
			this.idClass = idClass;
		}

		/**
		 * @return the nonEmbeddedType
		 */
		public boolean isNonEmbeddedType() {
			return nonEmbeddedType;
		}

		/**
		 * @param nonEmbeddedType the nonEmbeddedType to set
		 */
		public void setNonEmbeddedType(boolean nonEmbeddedType) {
			this.nonEmbeddedType = nonEmbeddedType;
		}

		/**
		 * @return the columns
		 */
		public String[] getColumns() {
			return columns;
		}

		/**
		 * @param columns the columns to set
		 */
		public void setColumns(String[] columns) {
			this.columns = columns;
		}

		/**
		 * @return the columnsInOriginal
		 */
		public String[] getColumnsInOriginal() {
			return columnsInOriginal;
		}

		/**
		 * @param columnsInOriginal the columnsInOriginal to set
		 */
		public void setColumnsInOriginal(String[] columnsInOriginal) {
			this.columnsInOriginal = columnsInOriginal;
		}

		/**
		 * @return the entityClass
		 */
		public Class<?> getEntityClass() {
			return entityClass;
		}

		/**
		 * @param entityClass the entityClass to set
		 */
		public void setEntityClass(Class<?> entityClass) {
			this.entityClass = entityClass;
		}

		/**
		 * @return the toOriginalIdBridge
		 */
		public Class<? extends ToOriginalIdBridge> getToOriginalIdBridge() {
			return toOriginalIdBridge;
		}

		/**
		 * @param toOriginalIdBridge the toOriginalIdBridge to set
		 */
		public void setToOriginalIdBridge(Class<? extends ToOriginalIdBridge> toOriginalIdBridge) {
			this.toOriginalIdBridge = toOriginalIdBridge;
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + Arrays.hashCode( columns );
			result = prime * result + Arrays.hashCode( columnsInOriginal );
			result = prime * result + ((entityClass == null) ? 0 : entityClass.hashCode());
			result = prime * result + ((toOriginalIdBridge == null) ? 0 : toOriginalIdBridge.hashCode());
			return result;
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		@Override
		public boolean equals(Object obj) {
			if ( this == obj ) {
				return true;
			}
			if ( obj == null ) {
				return false;
			}
			if ( getClass() != obj.getClass() ) {
				return false;
			}
			IdColumn other = (IdColumn) obj;
			if ( !Arrays.equals( columns, other.columns ) ) {
				return false;
			}
			if ( !Arrays.equals( columnsInOriginal, other.columnsInOriginal ) ) {
				return false;
			}
			if ( entityClass == null ) {
				if ( other.entityClass != null ) {
					return false;
				}
			}
			else if ( !entityClass.equals( other.entityClass ) ) {
				return false;
			}
			if ( toOriginalIdBridge == null ) {
				if ( other.toOriginalIdBridge != null ) {
					return false;
				}
			}
			else if ( !toOriginalIdBridge.equals( other.toOriginalIdBridge ) ) {
				return false;
			}
			return true;
		}

	}

}
