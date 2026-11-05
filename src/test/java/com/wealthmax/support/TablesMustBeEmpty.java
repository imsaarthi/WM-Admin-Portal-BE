package com.wealthmax.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Names the tables a database test class needs to be empty when it starts. TablesMustBeEmptyExtension checks them
 * and stops the run if one holds rows. It never deletes anything.
 *
 * Use it on classes that assert exact row counts or first-generated numbers. Skip it when every test only reads
 * back rows it created itself.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TablesMustBeEmpty {

    String[] value();
}
