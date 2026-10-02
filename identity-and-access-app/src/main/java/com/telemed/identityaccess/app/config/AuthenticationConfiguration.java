package com.telemed.identityaccess.app.config;

import com.telemed.identityaccess.adapter.out.security.JwtAccessTokenProvider;
import com.telemed.identityaccess.adapter.out.security.JwtAccessTokenVerifier;
import com.telemed.identityaccess.adapter.out.security.JwtProperties;
import com.telemed.identityaccess.adapter.out.security.RefreshTokenProperties;
import com.telemed.identityaccess.adapter.out.security.SecureRefreshTokenProvider;
import com.telemed.identityaccess.adapter.out.security.Sha256RefreshTokenHasher;
import com.telemed.identityaccess.application.port.in.LoginUseCase;
import com.telemed.identityaccess.application.port.in.RefreshSessionUseCase;
import com.telemed.identityaccess.application.port.out.AccessTokenProviderPort;
import com.telemed.identityaccess.application.port.out.AccessTokenVerifierPort;
import com.telemed.identityaccess.application.port.out.PasswordHasherPort;
import com.telemed.identityaccess.application.port.out.RefreshTokenHasherPort;
import com.telemed.identityaccess.application.port.out.RefreshTokenProviderPort;
import com.telemed.identityaccess.application.port.out.RefreshTokenRepositoryPort;
import com.telemed.identityaccess.application.port.out.UserRepositoryPort;
import com.telemed.identityaccess.application.usecase.LoginService;
import com.telemed.identityaccess.application.usecase.RefreshSessionService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.telemed.identityaccess.application.port.in.LogoutUseCase;
import com.telemed.identityaccess.application.usecase.LogoutService;
import java.time.Clock;

@Configuration
@EnableConfigurationProperties({
        JwtProperties.class,
        RefreshTokenProperties.class
})
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
    AccessTokenVerifierPort accessTokenVerifier(
            JwtProperties properties
    ) {
        return new JwtAccessTokenVerifier(
                properties
        );
    }

    @Bean
    RefreshTokenProviderPort refreshTokenProvider(
            RefreshTokenProperties properties,
            Clock authenticationClock
    ) {
        return new SecureRefreshTokenProvider(
                properties,
                authenticationClock
        );
    }

    @Bean
    RefreshTokenHasherPort refreshTokenHasher() {
        return new Sha256RefreshTokenHasher();
    }

    @Bean
    LoginUseCase loginUseCase(
            UserRepositoryPort users,
            PasswordHasherPort passwordHasher,
            AccessTokenProviderPort accessTokens,
            RefreshTokenProviderPort refreshTokens,
            RefreshTokenRepositoryPort refreshTokenRepository
    ) {
        return new LoginService(
                users,
                passwordHasher,
                accessTokens,
                refreshTokens,
                refreshTokenRepository
        );
    }

    @Bean
LogoutUseCase logoutUseCase(
        RefreshTokenRepositoryPort refreshTokens,
        RefreshTokenHasherPort refreshTokenHasher
) {
    return new LogoutService(
            refreshTokens,
            refreshTokenHasher
    );
}

    @Bean
    RefreshSessionUseCase refreshSessionUseCase(
            RefreshTokenRepositoryPort refreshTokens,
            RefreshTokenHasherPort refreshTokenHasher,
            RefreshTokenProviderPort refreshTokenProvider,
            AccessTokenProviderPort accessTokens,
            UserRepositoryPort users,
            Clock authenticationClock
    ) {
        return new RefreshSessionService(
                refreshTokens,
                refreshTokenHasher,
                refreshTokenProvider,
                accessTokens,
                users,
                authenticationClock
        );
    }
}