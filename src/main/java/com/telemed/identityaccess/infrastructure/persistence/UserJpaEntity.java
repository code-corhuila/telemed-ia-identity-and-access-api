package com.telemed.identityaccess.infrastructure.persistence;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "users")
public class UserJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Column(nullable = false, length = 180)
    private String email;

    @Column(name = "identity_document", nullable = false, unique = true, length = 50)
    private String identityDocument;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private RoleJpaEntity role;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private boolean verified;

    @Column(nullable = false)
    private boolean active;

    @Column(
            name = "registration_date",
            insertable = false,
            updatable = false
    )
    private OffsetDateTime registrationDate;

    @Column(name = "last_access")
    private OffsetDateTime lastAccess;

    protected UserJpaEntity() {
    }

    public UserJpaEntity(
            String fullName,
            String email,
            String identityDocument,
            RoleJpaEntity role,
            String passwordHash,
            boolean verified,
            boolean active
    ) {
        this.fullName = fullName;
        this.email = email;
        this.identityDocument = identityDocument;
        this.role = role;
        this.passwordHash = passwordHash;
        this.verified = verified;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getIdentityDocument() {
        return identityDocument;
    }

    public RoleJpaEntity getRole() {
        return role;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isVerified() {
        return verified;
    }

    public boolean isActive() {
        return active;
    }
}