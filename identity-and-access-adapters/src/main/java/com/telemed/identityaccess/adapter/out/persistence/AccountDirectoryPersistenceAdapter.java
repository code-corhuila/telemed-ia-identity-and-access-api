package com.telemed.identityaccess.adapter.out.persistence;

import com.telemed.identityaccess.application.port.in.BrowseAccountsUseCase.AccountPage;
import com.telemed.identityaccess.application.port.in.BrowseAccountsUseCase.AccountView;
import com.telemed.identityaccess.application.port.in.BrowseAccountsUseCase.Query;
import com.telemed.identityaccess.application.port.out.AccountDirectoryPort;
import com.telemed.identityaccess.domain.model.Role;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AccountDirectoryPersistenceAdapter
        implements AccountDirectoryPort {

    // Newest registration first; the id breaks ties so pages never overlap.
    private static final Sort NEWEST_FIRST =
            Sort.by(Sort.Order.desc("registrationDate"), Sort.Order.desc("id"));

    private final SpringDataUserRepository users;

    public AccountDirectoryPersistenceAdapter(
            SpringDataUserRepository users
    ) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public AccountPage search(Query query) {

        Page<UserJpaEntity> page = users.findAll(
                matching(query),
                PageRequest.of(query.page() - 1, query.limit(), NEWEST_FIRST)
        );

        return new AccountPage(
                page.getContent().stream().map(this::toView).toList(),
                query.page(),
                query.limit(),
                page.getTotalElements()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccountView> findById(UUID accountId) {
        return users.findById(accountId).map(this::toView);
    }

    private static Specification<UserJpaEntity> matching(Query query) {
        return (root, criteria, builder) -> {
            List<Predicate> filters = new ArrayList<>();

            if (query.role() != null) {
                filters.add(builder.equal(root.get("role").get("name"), query.role().name()));
            }

            if (query.active() != null) {
                filters.add(builder.equal(root.get("active"), query.active()));
            }

            if (query.text() != null) {
                String pattern = "%" + escapeLike(query.text().toLowerCase(Locale.ROOT)) + "%";
                filters.add(builder.or(
                        builder.like(builder.lower(root.get("fullName")), pattern, '\\'),
                        builder.like(builder.lower(root.get("email")), pattern, '\\'),
                        builder.like(builder.lower(root.get("identityDocument")), pattern, '\\')
                ));
            }

            return builder.and(filters.toArray(Predicate[]::new));
        };
    }

    /** The search text is data, never a pattern: % and _ match themselves. */
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private AccountView toView(UserJpaEntity entity) {
        return new AccountView(
                entity.getId(),
                entity.getFullName(),
                entity.getEmail(),
                entity.getIdentityDocument(),
                Role.valueOf(entity.getRole().getName()),
                entity.isActive(),
                toInstant(entity.getRegistrationDate()),
                toInstant(entity.getLastAccess())
        );
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
