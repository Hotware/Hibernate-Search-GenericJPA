/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package com.github.hotware.hsearch.db.events.jpa;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.HashSet;
import java.util.List;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;

import org.junit.Test;

import com.github.hotware.hsearch.db.events.EventModelParser;
import com.github.hotware.hsearch.db.events.EventType;
import com.github.hotware.hsearch.db.events.UpdateConsumer;
import com.github.hotware.hsearch.jpa.test.entities.Place;
import com.github.hotware.hsearch.jpa.test.entities.PlaceSorcererUpdates;
import com.github.hotware.hsearch.jpa.test.entities.PlaceUpdates;
import com.github.hotware.hsearch.jpa.test.entities.Sorcerer;
import com.github.hotware.hsearch.jpa.util.MultiQueryAccess;

/**
 * @author Martin Braun
 */
public class JPAUpdateSourceTest {

	@Test
	public void test() throws InterruptedException {
		EntityManagerFactory emf = Persistence.createEntityManagerFactory( "EclipseLink" );
		try {
			EventModelParser parser = new EventModelParser();
			JPAUpdateSource updateSource = new JPAUpdateSource(
					parser.parse( new HashSet<>( Arrays.asList( PlaceSorcererUpdates.class, PlaceUpdates.class ) ) ), emf, false, 1, TimeUnit.SECONDS, 2, 2 );

			{
				EntityManager em = null;
				try {
					em = emf.createEntityManager();
					EntityTransaction tx = em.getTransaction();
					tx.begin();
					PlaceSorcererUpdates update = new PlaceSorcererUpdates();
					update.setEventType( EventType.INSERT );
					update.setId( 1L );
					update.setPlaceId( 2 );
					update.setSorcererId( 3 );
					em.persist( update );
					tx.commit();

				}
				finally {
					if ( em != null ) {
						em.close();
					}
				}
			}

			final boolean[] gotEvent = new boolean[2];
			updateSource.setUpdateConsumers( Arrays.asList( new UpdateConsumer() {

				@Override
				public void updateEvent(List<UpdateInfo> updateInfos) {
					for ( UpdateInfo updateInfo : updateInfos ) {
						Object id = updateInfo.getId();
						int eventType = updateInfo.getEventType();
						if ( id.equals( 2 ) && updateInfo.getEntityClass().equals( Place.class ) && eventType == EventType.INSERT ) {
							gotEvent[0] = true;
						}
						else if ( id.equals( 3 ) && updateInfo.getEntityClass().equals( Sorcerer.class ) && eventType == EventType.INSERT ) {
							gotEvent[1] = true;
						}
					}
				}
			} ) );
			updateSource.start();
			Thread.sleep( 1000 * 3 );
			updateSource.stop();
			for ( boolean ev : gotEvent ) {
				if ( !ev ) {
					fail( "didn't get all events that were expected" );
				}
			}

			EntityManager em = null;
			try {
				em = emf.createEntityManager();
				EntityTransaction tx = em.getTransaction();
				tx.begin();
				assertEquals( "UpdateSource should delete all things after it has processed the updates but didn't do so", null,
						em.find( PlaceSorcererUpdates.class, 1L ) );
				tx.commit();
			}
			finally {
				if ( em != null ) {
					em.close();
				}
			}

		}
		finally {
			emf.close();
		}
	}

	/**
	 * this is needed in other tests because the query method of JPAUpdateSource has package access
	 */
	public static MultiQueryAccess query(EntityManagerFactory emf, EntityManager em) throws NoSuchFieldException, SecurityException {
		EventModelParser parser = new EventModelParser();
		JPAUpdateSource updateSource = new JPAUpdateSource( parser.parse( new HashSet<>( Arrays.asList( PlaceSorcererUpdates.class, PlaceUpdates.class ) ) ),
				emf, false, 1, TimeUnit.SECONDS, 2, 2 );
		return updateSource.query( em );
	}

}
