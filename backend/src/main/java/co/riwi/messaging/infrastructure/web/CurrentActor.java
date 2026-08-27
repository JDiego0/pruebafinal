package co.riwi.messaging.infrastructure.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method parameter to receive the {@code AuthenticatedActor}
 * built by {@link JwtAuthenticationFilter}. This is the ONLY sanctioned way a
 * controller obtains "who is calling" — never a {@code @RequestParam} or
 * {@code @PathVariable} named userId (AUTH-09).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface CurrentActor {
}
