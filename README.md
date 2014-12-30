Hibernate-Search-JPA
====================

NOTE: This version currently relies temporarily on this version of Hibernate-Search:

https://github.com/s4ke/hibernate-search (branch deleteByQuery)

Hibernate-Search with the JPA provider you want.


Okay. Currently only EclipseLink and Hibernate (will be implemented soon) have automatic reindexing features (EclipseLink relies on the JPA Event system, if your JPA handles events the same as EclipseLink, then you can use the JPAEventProvider with HSearchJPAEventListeners annotated to your entities) . Feel free to contribute EventProviders for other implementations.

hibernate-search-standalone can be used to have a Hibernate-Search Index with only needing Hibernate-Search's engine module on the classpath, while hibernate-search-jpa contains means to integrate hibernate-search-standalone with your JPA provider.
