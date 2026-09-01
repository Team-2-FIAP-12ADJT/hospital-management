package com.fiap.hospital.history.config;

import org.junit.jupiter.api.Test;
import org.mockito.quality.Strictness;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.annotation.web.configurers.SessionManagementConfigurer;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.DefaultSecurityFilterChain;

import java.time.Instant;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class SecurityConfigTest {

    private final SecurityConfig securityConfig = new SecurityConfig();
    private final JwtAuthenticationConverter converter = securityConfig.jwtAuthenticationConverter();

    @Test
    void mapsRoleClaimToGrantedAuthority() {
        Collection<GrantedAuthority> authorities = converter.convert(jwt("DOCTOR")).getAuthorities();
        assertTrue(authorities.stream().anyMatch(a -> "ROLE_DOCTOR".equals(a.getAuthority())));
    }

    @Test
    void blankRoleDoesNotAddRoleAuthority() {
        Collection<GrantedAuthority> authorities = converter.convert(jwt("  ")).getAuthorities();
        assertTrue(authorities.stream().noneMatch(a -> a.getAuthority().startsWith("ROLE_")));
    }

    @Test
    void missingRoleDoesNotAddRoleAuthority() {
        Instant now = Instant.parse("2026-08-31T12:00:00Z");
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("00000000-0000-4000-8000-000000000001")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .build();
        Collection<GrantedAuthority> authorities = converter.convert(jwt).getAuthorities();
        assertTrue(authorities.stream().noneMatch(a -> a.getAuthority().startsWith("ROLE_")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void filterChainAppliesCsrfSessionAndOauth2Rules() throws Exception {
        HttpSecurity http = mock(HttpSecurity.class, withSettings().strictness(Strictness.LENIENT));
        DefaultSecurityFilterChain chain = mock(DefaultSecurityFilterChain.class);

        when(http.csrf(any())).thenAnswer(invocation -> {
            Customizer<CsrfConfigurer<HttpSecurity>> customizer = invocation.getArgument(0);
            CsrfConfigurer<HttpSecurity> csrf = mock(CsrfConfigurer.class);
            when(csrf.disable()).thenReturn(http);
            customizer.customize(csrf);
            return http;
        });

        when(http.sessionManagement(any())).thenAnswer(invocation -> {
            Customizer<SessionManagementConfigurer<HttpSecurity>> customizer = invocation.getArgument(0);
            SessionManagementConfigurer<HttpSecurity> session = mock(SessionManagementConfigurer.class);
            when(session.sessionCreationPolicy(any())).thenReturn(session);
            customizer.customize(session);
            return http;
        });

        when(http.authorizeHttpRequests(any())).thenAnswer(invocation -> {
            Customizer<AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry> customizer =
                    invocation.getArgument(0);
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry =
                    mock(AuthorizeHttpRequestsConfigurer.AuthorizationManagerRequestMatcherRegistry.class, RETURNS_DEEP_STUBS);
            customizer.customize(registry);
            return http;
        });

        when(http.oauth2ResourceServer(any())).thenAnswer(invocation -> {
            Customizer<OAuth2ResourceServerConfigurer<HttpSecurity>> customizer = invocation.getArgument(0);
            OAuth2ResourceServerConfigurer<HttpSecurity> oauth2 = mock(OAuth2ResourceServerConfigurer.class);
            when(oauth2.jwt(any())).thenAnswer(jwtInvocation -> {
                Customizer<OAuth2ResourceServerConfigurer<HttpSecurity>.JwtConfigurer> jwtCustomizer =
                        jwtInvocation.getArgument(0);
                OAuth2ResourceServerConfigurer<HttpSecurity>.JwtConfigurer jwtConfigurer =
                        mock(OAuth2ResourceServerConfigurer.JwtConfigurer.class);
                when(jwtConfigurer.jwtAuthenticationConverter(any())).thenReturn(jwtConfigurer);
                jwtCustomizer.customize(jwtConfigurer);
                return oauth2;
            });
            customizer.customize(oauth2);
            return http;
        });

        when(http.build()).thenReturn(chain);

        DefaultSecurityFilterChain built = (DefaultSecurityFilterChain) securityConfig.filterChain(http);
        assertSame(chain, built);
        assertNotNull(built);
    }

    private static Jwt jwt(String role) {
        Instant now = Instant.parse("2026-08-31T12:00:00Z");
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("00000000-0000-4000-8000-000000000001")
                .claim("role", role)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .build();
    }
}
