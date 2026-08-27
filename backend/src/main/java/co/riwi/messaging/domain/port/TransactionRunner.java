package co.riwi.messaging.domain.port;

import co.riwi.messaging.domain.model.UserId;

import java.util.function.Supplier;

/**
 * The only place a database transaction begins (D-02). Every content-bearing
 * repository call MUST happen inside {@link #runAsActor}, which sets
 * {@code app.current_user_id} via a bound parameter (never string
 * concatenation, MSG-03) before running the work, and lets it fall out of
 * scope on commit or rollback — a pooled connection can never carry one
 * request's actor into the next (R2).
 * <p>
 * {@link #runAnonymous} exists ONLY for the handful of operations that must
 * run before an actor is known (credential lookup during login, refresh
 * token rotation) — those reach SECURITY DEFINER functions that authorize
 * themselves by a different means (password hash comparison, token
 * possession), never by a spoofable actor id.
 */
public interface TransactionRunner {

    <T> T runAsActor(UserId actor, Supplier<T> work);

    <T> T runAnonymous(Supplier<T> work);
}
