package com.glv.gsysportal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * TEMPORARY stub Security configuration.
 *
 * Full Authentication (Technical Design 7章: Spring Security session Form Login
 * backed by a Prototype {@code portal_user} table) is explicitly OUT OF SCOPE for
 * this Step (see implementation instructions 11章: "本格Authenticationはまだ実装しない").
 *
 * This stub permits all requests so the Order Candidate List API/UI vertical
 * slice can be verified without blocking on login. It MUST be replaced before
 * any write-capable endpoint (Draft, Supplier Response, etc.) is implemented.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // no cookie-based session writes yet; revisit with real auth
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
