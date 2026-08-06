package com.aivle.backend.pipeline.module;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(
    prefix = "app.legacy-pipeline",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false
)
public @interface LegacyPipelineSurface {}
