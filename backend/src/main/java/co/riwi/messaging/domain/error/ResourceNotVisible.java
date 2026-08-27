package co.riwi.messaging.domain.error;

/**
 * The resource is either missing or exists but is not authorized for the
 * caller — deliberately indistinguishable at the API boundary (SEC-14,
 * D-14). Maps to 404. Repositories throw this whenever an RLS-filtered
 * query returns zero rows for an operation that expected exactly one.
 */
public class ResourceNotVisible extends DomainError {
    public ResourceNotVisible(String resource, Object id) {
        super(resource + " " + id + " not found");
    }
}
