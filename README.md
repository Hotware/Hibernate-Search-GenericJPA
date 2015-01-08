Hibernate-Search-JPA
====================

NOTE: This version currently relies temporarily on this version of Hibernate-Search:

https://github.com/s4ke/hibernate-search (branch deleteByQuery)

Hibernate-Search with the JPA provider you want.

The current automatic ReIndexing Feature for JPA is not intended for production use (as it may leave you with an inconsistent index). See https://github.com/Hotware/Hibernate-Search-JPA/issues/9

hibernate-search-standalone can be used to have a Hibernate-Search Index with only needing Hibernate-Search's engine module on the classpath, while hibernate-search-jpa contains means to integrate hibernate-search-standalone with your JPA provider.
