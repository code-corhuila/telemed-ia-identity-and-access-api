package com.telemed.identityaccess.application.port.out;

import com.telemed.identityaccess.domain.model.RefreshToken;

public interface RefreshTokenRepositoryPort {

    void save(RefreshToken refreshToken);
}