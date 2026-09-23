package com.telemed.identityaccess.application.port.out;

import com.telemed.identityaccess.domain.model.User;

public interface UserRepositoryPort {

    boolean existsByEmail(String email);

    boolean existsByIdentityDocument(String identityDocument);

    User save(User user);
}