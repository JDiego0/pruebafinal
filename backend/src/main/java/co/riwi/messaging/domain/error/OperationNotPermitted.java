package co.riwi.messaging.domain.error;

/**
 * The actor can see the resource but may not perform this operation on it
 * (e.g. a MEMBER trying to rename a channel). Maps to 403. Reserved for
 * exactly this case — anything the actor should not even know exists is
 * {@link ResourceNotVisible} / 404 instead (D-14, SEC-14).
 */
public class OperationNotPermitted extends DomainError {
    public OperationNotPermitted(String message) {
        super(message);
    }
}
