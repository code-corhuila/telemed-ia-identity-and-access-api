package com.telemed.identityaccess.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataRefreshTokenRepository
        extends JpaRepository<RefreshTokenJpaEntity, Long> {
}