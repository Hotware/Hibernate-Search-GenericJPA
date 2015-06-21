/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.entity;

import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.SystemException;

import org.hibernate.search.genericjpa.exception.SearchException;
import org.hibernate.search.genericjpa.jpa.util.JTALookup;

/**
 * @author Martin Braun
 */
public class JPAReusableEntityProvider implements ReusableEntityProvider {

	private final EntityManagerFactory emf;
	private final Map<Class<?>, String> idProperties;
	private final boolean useJTATransaction;
	private EntityManager em;
	private EntityManagerEntityProvider provider;
	private boolean startedJTA = false;

	public JPAReusableEntityProvider(EntityManagerFactory emf, Map<Class<?>, String> idProperties, boolean useJTATransaction) {
		this.emf = emf;
		this.idProperties = idProperties;
		this.useJTATransaction = useJTATransaction;
	}

	@Override
	public Object get(Class<?> entityClass, Object id) {
		if ( this.provider == null ) {
			throw new IllegalStateException( "not open!" );
		}
		return this.provider.get( entityClass, id );
	}

	@Override
	@SuppressWarnings("rawtypes")
	public List getBatch(Class<?> entityClass, List<Object> ids) {
		if ( this.provider == null ) {
			throw new IllegalStateException( "not open!" );
		}
		return this.provider.getBatch( entityClass, ids );
	}

	@Override
	public void close() {
		try {
			if ( this.provider == null ) {
				throw new IllegalStateException( "already closed!" );
			}
			this.commitTransaction();
			this.em.close();
		}
		finally {
			this.em = null;
			this.provider = null;
		}
	}

	@Override
	public void open() {
		try {
			if ( this.provider != null ) {
				throw new IllegalStateException( "already open!" );
			}
			this.em = this.emf.createEntityManager();
			this.provider = new EntityManagerEntityProvider( this.em, this.idProperties );
			this.beginTransaction();
		}
		catch (Throwable e) {
			if ( this.em != null ) {
				this.em.close();
			}
			this.em = null;
			this.provider = null;
			throw e;
		}
	}

	public void clearEm() {
		this.em.clear();
	}

	private void beginTransaction() {
		if ( !this.useJTATransaction ) {
			this.em.getTransaction().begin();
		}
		else {
			try {
				if(JTALookup.lookup().getStatus() == Status.STATUS_NO_TRANSACTION) {
					JTALookup.lookup().begin();
					this.startedJTA = true;
				}
			}
			catch (NotSupportedException | SystemException e) {
				throw new SearchException( "couldn't start a JTA Transaction", e );
			}
		}
	}

	private void commitTransaction() {
		if ( !this.useJTATransaction ) {
			this.em.getTransaction().commit();
		}
		else {
			try {
				if(this.startedJTA) {
					this.startedJTA = false;
					JTALookup.lookup().commit();
				}
			}
			catch (SecurityException | IllegalStateException | RollbackException | HeuristicMixedException | HeuristicRollbackException | SystemException e) {
				throw new SearchException( "couldn't commit a JTA Transaction", e );
			}
		}
	}

}
