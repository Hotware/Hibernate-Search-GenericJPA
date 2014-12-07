lucene-extension-hsearch
========================

This lets you use the Hibernate-Search Engine without having Hibernate present. The Hibernate-Search-Engine does all the index handling for you and you can use it as a Lucene-Index Provider.

This module also has the support for transforming the objects of the index back to _DTOs_, not the original Object hierarchy. As of now there are no efforts to support that (and not in the future), because Lucene Documents don't have such a big hierarchy and handling the original Object's classes might be dangerous (might confuse users). 