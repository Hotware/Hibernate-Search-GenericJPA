package com.github.hotware.lucene.extension.hsearch.jpa;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;

import org.apache.lucene.search.Query;
import org.junit.Before;
import org.junit.Test;

import com.github.hotware.lucene.extension.hseach.entity.jpa.EntityManagerEntityProvider;
import com.github.hotware.lucene.extension.hsearch.entity.EntityProvider;
import com.github.hotware.lucene.extension.hsearch.factory.SearchConfigurationImpl;
import com.github.hotware.lucene.extension.hsearch.factory.SearchFactory;
import com.github.hotware.lucene.extension.hsearch.factory.SearchFactoryFactory;
import com.github.hotware.lucene.extension.hsearch.jpa.event.JPAEventProvider;
import com.github.hotware.lucene.extension.hsearch.jpa.event.MetaModelParser;
import com.github.hotware.lucene.extension.hsearch.jpa.test.entities.AdditionalPlace;
import com.github.hotware.lucene.extension.hsearch.jpa.test.entities.AdditionalPlace2;
import com.github.hotware.lucene.extension.hsearch.jpa.test.entities.EmbeddableInfo;
import com.github.hotware.lucene.extension.hsearch.jpa.test.entities.Place;
import com.github.hotware.lucene.extension.hsearch.jpa.test.entities.Sorcerer;
import com.github.hotware.lucene.extension.hsearch.query.HSearchQuery;

public class IntegrationTest {

	private int valinorId = 0;
	private Place valinor;
	private EntityManagerFactory emf;

	@Before
	public void setup() {
		this.emf = Persistence.createEntityManagerFactory("EclipseLink");
		EntityManager em = emf.createEntityManager();
		try {
			EntityTransaction tx = em.getTransaction();
			tx.begin();
			
			@SuppressWarnings("unchecked")
			List<Place> toDelete = new ArrayList<>(em.createQuery("SELECT a FROM Place a")
					.getResultList());
			for(Place place : toDelete) {
				em.remove(place);
			}
			em.flush();

			Sorcerer gandalf = new Sorcerer();
			gandalf.setName("Gandalf");
			em.persist(gandalf);

			Sorcerer saruman = new Sorcerer();
			saruman.setName("Saruman");
			em.persist(saruman);

			Sorcerer radagast = new Sorcerer();
			radagast.setName("Radagast");
			em.persist(radagast);

			Sorcerer alatar = new Sorcerer();
			alatar.setName("Alatar");
			em.persist(alatar);

			Sorcerer pallando = new Sorcerer();
			pallando.setName("Pallando");
			em.persist(pallando);

			// populate this database with some stuff
			Place helmsDeep = new Place();
			helmsDeep.setName("Helm's Deep");
			Set<Sorcerer> sorcerersAtHelmsDeep = new HashSet<>();
			sorcerersAtHelmsDeep.add(gandalf);
			gandalf.setPlace(helmsDeep);
			helmsDeep.setSorcerers(sorcerersAtHelmsDeep);
			em.persist(helmsDeep);

			Place valinor = new Place();
			valinor.setName("Valinor");
			Set<Sorcerer> sorcerersAtValinor = new HashSet<>();
			sorcerersAtValinor.add(saruman);
			saruman.setPlace(valinor);
			valinor.setSorcerers(sorcerersAtValinor);
			em.persist(valinor);

			valinorId = valinor.getId();

			this.valinor = valinor;

			em.flush();
			tx.commit();
		} finally {
			if (em != null) {
				em.close();
			}
		}

	}

	// FIXME: for some reason, this doesn't work anymore...
	// @Test
	// public void testHibernate() throws IOException {
	// EntityManagerFactory emf = Persistence
	// .createEntityManagerFactory("Hibernate");
	// this.setup(emf);
	// try {
	// this.test(emf);
	// } finally {
	// emf.close();
	// }
	// }

	@Test
	public void testEclipseLink() throws IOException {
		System.out.println("meta model parser seems to be ok.");
		this.test();
	}

	@Test
	public void testMetaModelParser() throws IOException {
		EntityProvider entityProvider = null;
		SearchFactory searchFactory = null;
		try {
			MetaModelParser parser = new MetaModelParser();
			parser.parse(this.emf.getMetamodel());
			{
				Sorcerer sorc = this.valinor.getSorcerers().iterator().next();
				Function<Object, Object> func = parser.getRootParentAccessors()
						.get(Sorcerer.class).get(Place.class);
				Place place = (Place) func.apply(sorc);
				assertEquals(this.valinor, place);
			}
		} finally {
			if (entityProvider != null) {
				entityProvider.close();
			}
			if (searchFactory != null) {
				searchFactory.close();
			}
		}
	}

	@SuppressWarnings("unchecked")
	public void test() throws IOException {
		EntityProvider entityProvider = null;
		SearchFactory searchFactory = null;
		try {
			EntityManager em;
			entityProvider = new EntityManagerEntityProvider(
					em = emf.createEntityManager());
			EntityTransaction tx = em.getTransaction();
			tx.begin();

			MetaModelParser parser = new MetaModelParser();
			parser.parse(em.getMetamodel());
			JPAEventProvider eventProvider = JPAEventProvider.register(parser
					.getManagedTypes().keySet(), true);

			searchFactory = SearchFactoryFactory.createSearchFactory(
					eventProvider, new SearchConfigurationImpl(), Arrays
							.asList(Place.class, Sorcerer.class,
									EmbeddableInfo.class,
									AdditionalPlace.class,
									AdditionalPlace2.class));

			// at first: index all places we can find
			{
				searchFactory.index(em.createQuery("SELECT a FROM Place a")
						.getResultList());
			}

			{
				Place valinorDb = em.find(Place.class, valinorId);
				valinorDb.setName("Valinor123");
				em.flush();
				// check if we find the renamed version in the index
				{
					List<Place> places = this.findPlaces(searchFactory,
							entityProvider, "name", "valinor123");
					assertEquals(1, places.size());
					assertEquals("Valinor123", places.get(0).getName());
				}

				// the original version should not be found in the index
				{
					List<Place> places = this.findPlaces(searchFactory,
							entityProvider, "name", "valinor");
					assertEquals(0, places.size());
				}

				// and name it back
				valinorDb.setName("Valinor");
				em.flush();

				// we should find it again
				{
					List<Place> places = this.findPlaces(searchFactory,
							entityProvider, "name", "valinor");
					assertEquals(1, places.size());
					assertEquals("Valinor", places.get(0).getName());
				}

				List<EmbeddableInfo> embeddableInfo = new ArrayList<>();
				{
					EmbeddableInfo e1 = new EmbeddableInfo();
					e1.setInfo("random info about valinor");
					e1.setOwnerId(valinorId);
					embeddableInfo.add(e1);
				}
				valinorDb.setInfo(embeddableInfo);
				em.flush();
			}

			{
				Place place = (Place) em.createQuery("SELECT a FROM Place a")
						.getResultList().get(0);

				Sorcerer newSorcerer = new Sorcerer();
				newSorcerer.setName("Odalbort the Unknown");
				newSorcerer.setPlace(place);

				place.getSorcerers().add(newSorcerer);

				// this will trigger a postUpdate on Place :).
				// collections can be handled from the entity owning the entity
				// :)
				place = (Place) em.createQuery("SELECT a FROM Place a")
						.getResultList().get(0);

				// this won't trigger a postUpdate on Place, but on Sorcerer
				newSorcerer.setName("Odalbert");
				em.flush();

				{
					List<Place> places = this.findPlaces(searchFactory,
							entityProvider, "sorcerers.name", "odalbert");
					assertEquals(1, places.size());
				}

				newSorcerer.setPlace(null);
				em.flush();
				{
					List<Place> places = this.findPlaces(searchFactory,
							entityProvider, "sorcerers.name", "odalbert");
					// as we set the parent to null
					// but we didn't remove it from the collection
					// of Place this should still be found here
					// from the index of Place, but changes to this Sorcerer
					// will not be propagated up anymore.
					assertEquals(1, places.size());
				}

				newSorcerer.setPlace(place);
				em.flush();

				place.getSorcerers().remove(newSorcerer);
				em.flush();
				{
					List<Place> places = this.findPlaces(searchFactory,
							entityProvider, "sorcerers.name", "odalbert");
					assertEquals(0, places.size());
				}
				
				List<AdditionalPlace> additionalPlace = new ArrayList<>();
				{
					AdditionalPlace a = new AdditionalPlace();
					a.setPlace(new ArrayList<>(Arrays.asList(place)));
					a.setInfo("addi");
					AdditionalPlace2 a2 = new AdditionalPlace2();
					a2.setAdditionalPlace(a);
					a2.setInfo("toast");
					a.setAdditionalPlace2(a2);
					additionalPlace.add(a);
				}
				place.setAdditionalPlace(new ArrayList<>(additionalPlace));
				em.flush();
				{
					List<Place> places = this.findPlaces(searchFactory,
							entityProvider, "additionalPlace.info", "addi");
					assertEquals(1, places.size());
				}
				{
					List<Place> places = this.findPlaces(searchFactory,
							entityProvider, "additionalPlace.additionalPlace2.info", "toast");
					assertEquals(1, places.size());
				}
				
				additionalPlace.get(0).setInfo("addi2");
				em.flush();
				{
					List<Place> places = this.findPlaces(searchFactory,
							entityProvider, "additionalPlace.info", "addi");
					assertEquals(0, places.size());
				}
				{
					List<Place> places = this.findPlaces(searchFactory,
							entityProvider, "additionalPlace.info", "addi2");
					assertEquals(1, places.size());
				}
				
				additionalPlace.get(0).getAdditionalPlace2().setInfo("goal");
				em.flush();
				{
					List<Place> places = this.findPlaces(searchFactory,
							entityProvider, "additionalPlace.additionalPlace2.info", "goal");
					assertEquals(1, places.size());
				}
				{
					List<Place> places = this.findPlaces(searchFactory,
							entityProvider, "additionalPlace.additionalPlace2.info", "toast");
					assertEquals(0, places.size());
				}
				
				additionalPlace.get(0).setInfo("addi");
				additionalPlace.get(0).getAdditionalPlace2().setInfo("toast");
				em.flush();
				{
					List<Place> places = this.findPlaces(searchFactory,
							entityProvider, "additionalPlace.info", "addi");
					assertEquals(1, places.size());
				}
				{
					List<Place> places = this.findPlaces(searchFactory,
							entityProvider, "additionalPlace.additionalPlace2.info", "toast");
					assertEquals(1, places.size());
				}
				
				place.getAdditionalPlace().remove(additionalPlace.get(0));
				additionalPlace.get(0).getPlace().remove(place);
				em.flush();
				{
					List<Place> places = this.findPlaces(searchFactory,
							entityProvider, "additionalPlace.info", "addi");
					assertEquals(0, places.size());
				}
				{
					List<Place> places = this.findPlaces(searchFactory,
							entityProvider, "additionalPlace.additionalPlace2.info", "toast");
					assertEquals(0, places.size());
				}
			}

			System.out.println("finished integration test");
			tx.commit();
		} finally {
			if (entityProvider != null) {
				entityProvider.close();
			}
			if (searchFactory != null) {
				searchFactory.close();
			}
		}
	}

	private List<Place> findPlaces(SearchFactory searchFactory,
			EntityProvider entityProvider, String field, String value) {
		Query query = searchFactory.buildQueryBuilder().forEntity(Place.class)
				.get().keyword().onField(field).matching(value).createQuery();
		HSearchQuery<Place> jpaQuery = searchFactory.createQuery(query,
				Place.class);
		List<Place> places = jpaQuery.query(entityProvider, Place.class);
		return places;
	}

}
