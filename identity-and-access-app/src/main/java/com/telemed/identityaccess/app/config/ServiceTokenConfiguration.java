package com.telemed.identityaccess.app.config;

import com.telemed.identityaccess.adapter.out.security.ConfiguredServiceClients;
import com.telemed.identityaccess.adapter.out.security.JwtProperties;
import com.telemed.identityaccess.adapter.out.security.JwtServiceTokenProvider;
import com.telemed.identityaccess.application.port.in.IssueServiceTokenUseCase;
import com.telemed.identityaccess.application.port.out.PasswordHasherPort;
import com.telemed.identityaccess.application.port.out.ServiceTokenProviderPort;
import com.telemed.identityaccess.application.usecase.IssueServiceTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/** Norma 5.7: service tokens, for the services listed in SERVICE_CLIENTS (none by default). */
@Configuration
public class ServiceTokenConfiguration {

    @Bean
    ServiceTokenProviderPort serviceTokenProvider(
            JwtProperties jwtProperties,
            @Value("${security.service-token.ttl:PT10M}") Duration ttl,
            Clock authenticationClock
    ) {
        return new JwtServiceTokenProvider(jwtProperties, ttl, authenticationClock);
    }

    @Bean
    IssueServiceTokenUseCase issueServiceTokenUseCase(
            @Value("${security.service-clients:}") String serviceClients,
            PasswordHasherPort passwordHasher,
            ServiceTokenProviderPort serviceTokenProvider
    ) {
        return new IssueServiceTokenService(
                new ConfiguredServiceClients(serviceClients),
                passwordHasher,
                serviceTokenProvider
        );
    }
}
