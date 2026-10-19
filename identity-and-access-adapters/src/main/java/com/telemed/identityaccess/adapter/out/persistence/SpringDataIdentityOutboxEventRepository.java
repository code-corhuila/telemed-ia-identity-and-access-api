package com.telemed.identityaccess.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataIdentityOutboxEventRepository
        extends JpaRepository<IdentityOutboxEventJpaEntity, UUID> {
}
