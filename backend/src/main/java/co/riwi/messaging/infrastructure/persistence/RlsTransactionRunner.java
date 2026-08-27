package co.riwi.messaging.infrastructure.persistence;

import co.riwi.messaging.domain.model.UserId;
import co.riwi.messaging.domain.port.TransactionRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * D-02: the ONLY place a transaction begins. {@code set_config(...)} is the
 * first statement of the transaction, bound as a parameter — never string
 * concatenation (MSG-03) — and scoped {@code is_local => true}, so it is
 * discarded on commit AND rollback and can never leak from one pooled
 * connection's request into the next (R2). Every JdbcTemplate call inside
 * {@code work} participates in this same transaction/connection because
 * Spring's DataSourceTransactionManager binds the connection to the current
 * thread for the duration of the callback.
 */
@Component
public class RlsTransactionRunner implements TransactionRunner {

    private final TransactionTemplate transactionTemplate;
    private final JdbcTemplate jdbcTemplate;

    public RlsTransactionRunner(PlatformTransactionManager transactionManager, JdbcTemplate jdbcTemplate) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public <T> T runAsActor(UserId actor, Supplier<T> work) {
        Objects.requireNonNull(actor, "actor must not be null for runAsActor — use runAnonymous if there is genuinely none");
        return transactionTemplate.execute(status -> {
            // set_config(...) returns its own new value as a one-row result
            // set (even conceptually "returning void" functions do the same
            // via SELECT) — JdbcTemplate#update() calls executeUpdate(),
            // which PostgreSQL's JDBC driver rejects whenever a statement
            // unexpectedly produces a result set. query(..., ResultSetExtractor)
            // uses executeQuery() instead, which is what a SELECT of any kind
            // requires. Found by exercising the login flow end-to-end against
            // a live database — this would have broken every authenticated
            // request had it shipped.
            jdbcTemplate.query("SELECT set_config('app.current_user_id', ?, true)", rs -> null, actor.value().toString());
            return work.get();
        });
    }

    @Override
    public <T> T runAnonymous(Supplier<T> work) {
        return transactionTemplate.execute(status -> work.get());
    }
}
