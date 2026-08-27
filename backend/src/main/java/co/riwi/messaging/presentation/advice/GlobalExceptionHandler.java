package co.riwi.messaging.presentation.advice;

import co.riwi.messaging.domain.error.*;
import co.riwi.messaging.infrastructure.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * API-03: every failure — domain or unexpected — renders as RFC 9457
 * Problem Details, in exactly this one place (docs/API.md §1). A raw
 * exception never reaches the client with its message or stack trace;
 * unmapped errors fall through to a generic 500 with no internal detail.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TYPE_BASE = "https://riwi.co/errors/";

    @ExceptionHandler(ValidationError.class)
    public ProblemDetail handleValidation(ValidationError e, HttpServletRequest request) {
        ProblemDetail pd = base(HttpStatus.UNPROCESSABLE_ENTITY, "validation", "Validation failed", e.getMessage(), request);
        pd.setProperty("errors", List.of(Map.of("field", e.field(), "message", e.getMessage())));
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleBeanValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        ProblemDetail pd = base(HttpStatus.UNPROCESSABLE_ENTITY, "validation", "Validation failed",
                "One or more fields are invalid.", request);
        List<Map<String, String>> errors = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(), "message", messageOf(fe)))
                .toList();
        pd.setProperty("errors", errors);
        return pd;
    }

    @ExceptionHandler(AuthenticationFailed.class)
    public ProblemDetail handleAuthFailed(AuthenticationFailed e, HttpServletRequest request) {
        String code = e.getMessage() != null && e.getMessage().contains("reuse")
                ? "token-reuse-detected" : "authentication-failed";
        return base(HttpStatus.UNAUTHORIZED, code, "Authentication failed", e.getMessage(), request);
    }

    @ExceptionHandler(OperationNotPermitted.class)
    public ProblemDetail handleForbidden(OperationNotPermitted e, HttpServletRequest request) {
        return base(HttpStatus.FORBIDDEN, "operation-not-permitted", "Operation not permitted", e.getMessage(), request);
    }

    @ExceptionHandler(ResourceNotVisible.class)
    public ProblemDetail handleNotFound(ResourceNotVisible e, HttpServletRequest request) {
        // SEC-14: identical shape whether the resource is missing or merely
        // unauthorized — never disclose which.
        return base(HttpStatus.NOT_FOUND, "not-found", "Not found", e.getMessage(), request);
    }

    @ExceptionHandler(ConflictError.class)
    public ProblemDetail handleConflict(ConflictError e, HttpServletRequest request) {
        return base(HttpStatus.CONFLICT, "conflict", "Conflict", e.getMessage(), request);
    }

    @ExceptionHandler(ProviderUnavailable.class)
    public ProblemDetail handleProviderUnavailable(ProviderUnavailable e, HttpServletRequest request) {
        return base(HttpStatus.SERVICE_UNAVAILABLE, "provider-unavailable", "AI provider unavailable", e.getMessage(), request);
    }

    /**
     * Translates specific PostgreSQL SQLSTATEs raised by RAISE EXCEPTION
     * inside a SECURITY DEFINER function/procedure (V4/V5) into the correct
     * domain-shaped response, instead of letting them fall through to the
     * generic 500 — e.g. rw_sp_modify_user's internal "only an admin may..."
     * check (42501) is a genuine 403, not an unexpected server error. Found
     * by exercising the admin-permission rejection path against a live
     * database: without this handler it incorrectly returned 500.
     */
    @ExceptionHandler(DataAccessException.class)
    public ProblemDetail handleDataAccess(DataAccessException e, HttpServletRequest request) {
        String sqlState = sqlStateOf(e);
        if (sqlState != null) {
            switch (sqlState) {
                case "42501" -> { return base(HttpStatus.FORBIDDEN, "operation-not-permitted", "Operation not permitted", detailOf(e), request); }
                case "P0002" -> { return base(HttpStatus.NOT_FOUND, "not-found", "Not found", "The requested resource was not found.", request); }
                case "23514", "23505" -> { return base(HttpStatus.CONFLICT, "conflict", "Conflict", detailOf(e), request); }
                case "28000" -> { return base(HttpStatus.UNAUTHORIZED, "authentication-failed", "Authentication failed", "Not authenticated.", request); }
                case "22023" -> { return base(HttpStatus.UNPROCESSABLE_ENTITY, "validation", "Validation failed", detailOf(e), request); }
                default -> { /* fall through to generic 500 below */ }
            }
        }
        return handleUnexpected(e, request);
    }

    private String sqlStateOf(Throwable e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof PSQLException psql) {
                return psql.getSQLState();
            }
            cause = cause.getCause();
        }
        return null;
    }

    private String detailOf(Throwable e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof PSQLException psql && psql.getServerErrorMessage() != null) {
                return psql.getServerErrorMessage().getMessage();
            }
            cause = cause.getCause();
        }
        return "Request rejected by the database.";
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception e, HttpServletRequest request) {
        String correlationId = correlationId(request);
        log.error("Unhandled exception, correlationId={}", correlationId, e);
        return base(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal error",
                "An unexpected error occurred. Reference: " + correlationId, request);
    }

    private ProblemDetail base(HttpStatus status, String code, String title, String detail, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatus(status);
        pd.setType(URI.create(TYPE_BASE + code));
        pd.setTitle(title);
        pd.setDetail(detail);
        pd.setInstance(URI.create(request.getRequestURI()));
        pd.setProperty("code", code.toUpperCase().replace('-', '_'));
        pd.setProperty("correlationId", correlationId(request));
        return pd;
    }

    private String correlationId(HttpServletRequest request) {
        Object id = request.getAttribute(CorrelationIdFilter.ATTRIBUTE);
        return id == null ? "unknown" : id.toString();
    }

    private String messageOf(FieldError fe) {
        return fe.getDefaultMessage() == null ? "invalid value" : fe.getDefaultMessage();
    }
}
