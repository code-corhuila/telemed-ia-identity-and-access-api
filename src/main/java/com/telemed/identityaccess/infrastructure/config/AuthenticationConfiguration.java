package com.telemed.identityaccess.infrastructure.config;

import com.telemed.identityaccess.application.port.in.LoginUseCase;
import com.telemed.identityaccess.application.port.out.AccessTokenProviderPort;
import com.telemed.identityaccess.application.port.out.PasswordHasherPort;
import com.telemed.identityaccess.application.port.out.UserRepositoryPort;
import com.telemed.identityaccess.application.usecase.LoginService;
import com.telemed.identityaccess.infrastructure.security.JwtAccessTokenProvider;
import com.telemed.identityaccess.infrastructure.security.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class AuthenticationConfiguration {

    @Bean
    Clock authenticationClock() {
        return Clock.systemUTC();
    }

    @Bean
    AccessTokenProviderPort accessTokenProvider(
            JwtProperties properties,
            Clock authenticationClock
    ) {
        return new JwtAccessTokenProvider(
                properties,
                authenticationClock
        );
    }

    @Bean
    LoginUseCase loginUseCase(
            UserRepositoryPort users,
            PasswordHasherPort passwordHasher,
            AccessTokenProviderPort accessTokens
    ) {
        return new LoginService(
                users,
                passwordHasher,
                accessTokens
        );
    }
}