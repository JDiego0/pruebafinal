package co.riwi.messaging.domain.model;

/**
 * The single source of truth for "who is making this call." Built exactly
 * once per request, from the verified JWT's claims — never from a request
 * body, path or query parameter (AUTH-09). Every use case that needs to know
 * the caller receives this, not a bare {@link UserId} plucked from a DTO.
 */
public record AuthenticatedActor(UserId userId, Role role, DisplayName displayName, Locale locale) {
}
