package com.telemed.identityaccess.adapter.out.persistence;

import jakarta.persistence.*;

@Entity
@Table(name = "roles")
public class RoleJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String name;

    protected RoleJpaEntity() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}