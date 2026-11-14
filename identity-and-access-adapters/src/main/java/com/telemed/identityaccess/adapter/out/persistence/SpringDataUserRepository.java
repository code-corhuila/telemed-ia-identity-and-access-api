package com.telemed.identityaccess.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataUserRepository
        extends JpaRepository<UserJpaEntity, UUID>,
        JpaSpecificationExecutor<UserJpaEntity> {

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByIdentityDocument(
            String identityDocument
    );

    boolean existsByRoleName(String roleName);

    Optional<UserJpaEntity> findByEmailIgnoreCase(
            String email
    );

    @Modifying(
            clearAutomatically = true,
            flushAutomatically = true
    )
    @Transactional
    @Query("""
            update UserJpaEntity user
            set user.passwordHash = :passwordHash
            where user.id = :userId
            """)
    int updatePasswordHashById(
            @Param("userId") UUID userId,
            @Param("passwordHash") String passwordHash
    );

    @Modifying(
            clearAutomatically = true,
            flushAutomatically = true
    )
    @Transactional
    @Query(
            value = "UPDATE users SET last_access = CURRENT_TIMESTAMP WHERE id = :userId",
            nativeQuery = true
    )
    int updateLastAccessById(
            @Param("userId") UUID userId
    );

    @Modifying(
            clearAutomatically = true,
            flushAutomatically = true
    )
    @Transactional
    @Query("""
            update UserJpaEntity user
            set user.active = :active
            where user.id = :userId
            """)
    int updateActiveById(
            @Param("userId") UUID userId,
            @Param("active") boolean active
    );

    // FOR UPDATE: a concurrent deactivation waits, then counts what this one committed.
    @Query(
            value = """
                    SELECT count(*) FROM (
                        SELECT u.id
                        FROM users u
                        JOIN roles r ON r.id = u.role_id
                        WHERE r.name = :roleName AND u.active
                        FOR UPDATE OF u
                    ) locked
                    """,
            nativeQuery = true
    )
    long countActiveByRoleNameLocking(
            @Param("roleName") String roleName
    );
}
