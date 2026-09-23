package com.telemed.identityaccess.infrastructure.persistence;

import com.telemed.identityaccess.application.port.out.UserRepositoryPort;
import com.telemed.identityaccess.domain.model.Role;
import com.telemed.identityaccess.domain.model.User;
import org.springframework.stereotype.Repository;

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

        UserJpaEntity saved = users.save(entity);

        return new User(
                saved.getId(),
                saved.getFullName(),
                saved.getEmail(),
                saved.getIdentityDocument(),
                saved.getPasswordHash(),
                Role.valueOf(saved.getRole().getName()),
                saved.isActive(),
                saved.isVerified()
        );
    }
}