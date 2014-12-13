package com.github.hotware.hsearch.annotations;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * This annotation is needed to specify that a class is located in an index. <br>
 * <br>
 * This is only needed (but always allowed), if objects of subclasses are
 * supplied to all indexing related operations. Then the "least top level" class
 * that is annotated with @InIndex is used
 * 
 * @author Martin
 */
@Target({ TYPE })
@Retention(RUNTIME)
public @interface InIndex {

}
