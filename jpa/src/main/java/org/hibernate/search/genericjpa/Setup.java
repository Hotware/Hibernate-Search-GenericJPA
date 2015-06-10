/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa;

public final class Setup {

	private Setup() {
		// can't touch this!
	}

	// FIXME: is this okay for multiple classloaders?

	private static JPASearchFactory searchFactory;

	public static JPASearchFactory getSearchFactory() {
		return searchFactory;
	}

	// TODO: make this easier together with a JPASearchFactory that can be configured via properties
	// rather than by overriding methods
	static void setup(JPASearchFactory searchFactory) {
		Setup.searchFactory = searchFactory;
	}

}
