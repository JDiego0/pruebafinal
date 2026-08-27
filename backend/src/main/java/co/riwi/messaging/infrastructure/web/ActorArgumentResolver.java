package co.riwi.messaging.infrastructure.web;

import co.riwi.messaging.domain.error.AuthenticationFailed;
import co.riwi.messaging.domain.model.AuthenticatedActor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves {@code @CurrentActor AuthenticatedActor} parameters from the
 * attribute {@link JwtAuthenticationFilter} sets. Throws
 * {@link AuthenticationFailed} (mapped to 401) if no valid token was
 * presented — a protected controller method can never silently receive a
 * null actor.
 */
@Component
public class ActorArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentActor.class)
                && parameter.getParameterType().equals(AuthenticatedActor.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                   NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = (HttpServletRequest) webRequest.getNativeRequest();
        Object actor = request.getAttribute(JwtAuthenticationFilter.ACTOR_ATTRIBUTE);
        if (actor == null) {
            throw new AuthenticationFailed("Missing or invalid access token.");
        }
        return actor;
    }
}
