package com.github.hotware.lucene.extension.hsearch.dto.annotations;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

//TODO: maybe make this repeatable?
@Target({ TYPE })
@Retention(RUNTIME)
public @interface DtoOverEntity {

	public Class<?> entityClass();

}
