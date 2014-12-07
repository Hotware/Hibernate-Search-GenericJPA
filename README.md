Hibernate-Search-JPA
====================

Hibernate-Search with the JPA provider you want.


Okay. Currently only EclipseLink and Hibernate (will be implemented soon) have automatic reindexing features (EclipseLink relies on the JPA Event system, if your JPA handles events the same as EclipseLink, then you can use the JPAEventProvider with HSearchJPAEventListeners annotated to your entities) . Feel free to contribute EventProviders for other implementations.
