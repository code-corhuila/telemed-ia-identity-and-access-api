package com.telemed.identityaccess.application.port.in;

import com.telemed.identityaccess.domain.model.Role;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** FR-012: what an administrator sees of the registered accounts. Never the password hash. */
public interface BrowseAccountsUseCase {

    int MAX_LIMIT = 100;

    AccountPage browse(Query query);

    AccountView view(UUID accountId);

    /**
     * @param text   part of the full name, email or identity document; null for any
     * @param role   null for any role
     * @param active null for active and inactive accounts
     * @param page   from 1
     * @param limit  1 to 100
     */
    record Query(
            String text,
            Role role,
            Boolean active,
            int page,
            int limit
    ) {

        public Query {
            text = text == null || text.isBlank() ? null : text.trim();

            if (page < 1) {
                throw new IllegalArgumentException("page must be a number from 1.");
            }

            if (limit < 1 || limit > MAX_LIMIT) {
                throw new IllegalArgumentException("limit must be between 1 and 100.");
            }
        }
    }

    record AccountView(
            UUID id,
            String fullName,
            String email,
            String identityDocument,
            Role role,
            boolean active,
            Instant registrationDate,
            Instant lastAccess
    ) {
    }

    record AccountPage(
            List<AccountView> items,
            int page,
            int limit,
            long total
    ) {

        public long totalPages() {
            return (total + limit - 1) / limit;
        }
    }
}
