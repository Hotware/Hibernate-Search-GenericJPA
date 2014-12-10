package com.github.hotware.hsearch.dto.annotations;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Target({ FIELD })
@Retention(RUNTIME)
@Repeatable(DtoFields.class)
public @interface DtoField {
	
	public String profileName() default "__#DEFAULT_PROFILE#__";
	
	public String fieldName() default "__#DEFAULT_FIELD_NAME#__";

}
