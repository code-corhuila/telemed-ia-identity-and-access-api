package com.telemed.identityaccess.application.port.out;

import com.telemed.identityaccess.application.port.out.AccessTokenProviderPort.IssuedAccessToken;

/** Signs a short-lived token whose subject is the service and whose role is SERVICE. */
public interface ServiceTokenProviderPort {

    IssuedAccessToken issue(String clientId);
}
