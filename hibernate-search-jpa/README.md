Lucene-Extension-HSearch-JPA
============================

Utility to allow JPA bindings to Hibernate-Search WITHOUT Hibernate

You will need this dependency:

	<dependency>
		<groupId>com.github.hotware</groupId>
		<artifactId>hibernate-search-standalone</artifactId>
		<version>0.1.0.0</version>
	</dependency>
	
Create your SearchFactory (with EventSource for EclipseLink Event style JPA):

	MetaModelParser parser = new MetaModelParser();
	parser.parse(em.getMetamodel());
	JPAEventSource eventSource = JPAEventSource.register(
			parser.getIndexRelevantEntites(), true);

	searchFactory = SearchFactoryFactory.createSearchFactory(
			eventSource, new SearchConfigurationImpl(),
			parser.getIndexRelevantEntites());