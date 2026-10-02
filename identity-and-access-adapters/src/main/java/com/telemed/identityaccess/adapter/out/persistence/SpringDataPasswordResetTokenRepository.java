package com.telemed.identityaccess.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataPasswordResetTokenRepository
        extends JpaRepository<PasswordResetTokenJpaEntity, Long> {
}