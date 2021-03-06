package io.toolisticon.jigsawbyap.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.SOURCE)
@Target(value = {ElementType.PACKAGE})
@Documented
public @interface JigsawModule {

    String name() default "";

    String[] requires() default {};

    String[] requiresStatic() default {};

}
