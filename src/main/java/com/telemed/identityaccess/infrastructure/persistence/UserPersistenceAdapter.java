package com.telemed.identityaccess.infrastructure.persistence;

import com.telemed.identityaccess.application.exception.RegistrationException;
import com.telemed.identityaccess.application.port.out.UserRepositoryPort;
import com.telemed.identityaccess.domain.model.Role;
import com.telemed.identityaccess.domain.model.User;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import static com.telemed.identityaccess.application.exception.RegistrationException.Reason.EMAIL_ALREADY_REGISTERED;
import static com.telemed.identityaccess.application.exception.RegistrationException.Reason.IDENTITY_DOCUMENT_ALREADY_REGISTERED;

@Repository
public class UserPersistenceAdapter implements UserRepositoryPort {

    private final SpringDataUserRepository users;
    private final SpringDataRoleRepository roles;

    public UserPersistenceAdapter(
            SpringDataUserRepository users,
            SpringDataRoleRepository roles
    ) {
        this.users = users;
        this.roles = roles;
    }

    @Override
    public boolean existsByEmail(String email) {
        return users.existsByEmailIgnoreCase(email);
    }

    @Override
    public boolean existsByIdentityDocument(String identityDocument) {
        return users.existsByIdentityDocument(identityDocument);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return users.findByEmailIgnoreCase(email)
                .map(this::toDomain);
    }

    @Override
    public User save(User user) {

        RoleJpaEntity role = roles.findByName(user.role().name())
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Required role is not configured."
                        )
                );

        UserJpaEntity entity = new UserJpaEntity(
                user.fullName(),
                user.email(),
                user.identityDocument(),
                role,
                user.passwordHash(),
                user.verified(),
                user.active()
        );

        UserJpaEntity saved;

        try {
            saved = users.saveAndFlush(entity);

        } catch (DataIntegrityViolationException exception) {

            if (users.existsByEmailIgnoreCase(user.email())) {
                throw new RegistrationException(
                        EMAIL_ALREADY_REGISTERED,
                        "Email is already registered."
                );
            }

            if (users.existsByIdentityDocument(user.identityDocument())) {
                throw new RegistrationException(
                        IDENTITY_DOCUMENT_ALREADY_REGISTERED,
                        "Identity document is already registered."
                );
            }

            throw exception;
        }

        return toDomain(saved);
    }

    private User toDomain(UserJpaEntity entity) {
        return new User(
                entity.getId(),
                entity.getFullName(),
                entity.getEmail(),
                entity.getIdentityDocument(),
                entity.getPasswordHash(),
                Role.valueOf(entity.getRole().getName()),
                entity.isActive(),
                entity.isVerified()
        );
    }
}