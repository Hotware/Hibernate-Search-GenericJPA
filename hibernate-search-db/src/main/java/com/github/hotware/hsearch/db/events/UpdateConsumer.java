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

import java.util.List;

/**
 * @author Martin
 *
 */
public interface UpdateConsumer {

	public void updateEvent(List<UpdateInfo> updateInfo);

	public static class UpdateInfo {
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
		 * 
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result
					+ ((entityClass == null) ? 0 : entityClass.hashCode());
			result = prime * result + eventType;
			result = prime * result + ((id == null) ? 0 : id.hashCode());
			return result;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			UpdateInfo other = (UpdateInfo) obj;
			if (entityClass == null) {
				if (other.entityClass != null) {
					return false;
				}
			} else if (!entityClass.equals(other.entityClass)) {
				return false;
			}
			if (eventType != other.eventType) {
				return false;
			}
			if (id == null) {
				if (other.id != null) {
					return false;
				}
			} else if (!id.equals(other.id)) {
				return false;
			}
			return true;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("UpdateInfo [entityClass=").append(entityClass)
					.append(", id=").append(id).append(", eventType=")
					.append(eventType).append("]");
			return builder.toString();
		}

	}

}
