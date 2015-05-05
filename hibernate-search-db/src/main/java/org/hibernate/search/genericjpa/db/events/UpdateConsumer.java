/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.db.events;

import java.util.List;

/**
 * @author Martin Braun
 */
public interface UpdateConsumer {

	/**
	 * called everytime an update is found in the database
	 *
	 * @param updateInfo a list of objects describing the several updates in the order they occured in the database
	 */
	void updateEvent(List<UpdateInfo> updateInfo);

	public class UpdateInfo {

		private final Class<?> entityClass;
		private final Object id;
		private final int eventType;

		public UpdateInfo(Class<?> entityClass, Object id, int eventType) {
			super();
			this.entityClass = entityClass;
			this.id = id;
			this.eventType = eventType;
		}

		/**
		 * @return the id
		 */
		public Object getId() {
			return this.id;
		}

		/**
		 * @return the eventType
		 */
		public int getEventType() {
			return this.eventType;
		}

		/**
		 * @return the entityClass
		 */
		public Class<?> getEntityClass() {
			return entityClass;
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ( ( entityClass == null ) ? 0 : entityClass.hashCode() );
			result = prime * result + eventType;
			result = prime * result + ( ( id == null ) ? 0 : id.hashCode() );
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
			UpdateInfo other = (UpdateInfo) obj;
			if ( entityClass == null ) {
				if ( other.entityClass != null ) {
					return false;
				}
			}
			else if ( !entityClass.equals( other.entityClass ) ) {
				return false;
			}
			if ( eventType != other.eventType ) {
				return false;
			}
			if ( id == null ) {
				if ( other.id != null ) {
					return false;
				}
			}
			else if ( !id.equals( other.id ) ) {
				return false;
			}
			return true;
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append( "UpdateInfo [entityClass=" ).append( entityClass ).append( ", id=" ).append( id ).append( ", eventType=" ).append( eventType )
					.append( "]" );
			return builder.toString();
		}

	}

}
