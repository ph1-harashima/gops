package com.glv.gsysportal.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.glv.gsysportal.security.PortalUserDetailsService;
import com.glv.gsysportal.security.PortalUserPrincipal;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spring Security Session Form Login backed by Prototype DB {@code portal_user}
 * (Technical Design 7章, Requirements MD 29.8/34章-C). Replaces the Step 0/1
 * temporary permitAll stub now that write-capable Draft endpoints exist and
 * need real created_by/updated_by/performed_by identities
 * (implementation instructions 15章).
 *
 * The Order Candidate List endpoints remain publicly readable (unchanged from
 * Step 0/1 - browsing candidates does not itself write Audit data). Every
 * {@code /api/orders/**} endpoint (Draft create/read/update) requires an
 * authenticated session.
 *
 * NOTE (residual, not addressed this Step): CSRF protection is disabled for
 * this Prototype's session-cookie API, consistent with the Step 0/1 stub.
 * This is an accepted simplification for a local-only Prototype, not a
 * decision meant to carry forward into any later Step that touches a
 * non-local environment.
 */
@Configuration
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(PortalUserDetailsService userDetailsService,
                                                              PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // see class Javadoc
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/order-candidates").permitAll()
                .requestMatchers("/api/order-candidates/**").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginProcessingUrl("/api/auth/login")
                .usernameParameter("username")
                .passwordParameter("password")
                .successHandler((request, response, authentication) -> {
                    response.setStatus(HttpServletResponse.SC_OK);
                    writeUserJson(response, objectMapper, (PortalUserPrincipal) authentication.getPrincipal());
                })
                .failureHandler((request, response, exception) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"errorCode\":\"INVALID_CREDENTIALS\"}");
                })
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler((request, response, authentication) ->
                        response.setStatus(HttpServletResponse.SC_OK))
                .permitAll()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"errorCode\":\"NOT_AUTHENTICATED\"}");
                })
                // Phase 7-C1: authenticated but role-insufficient (e.g. an
                // OPERATOR calling the ADMIN-only approve API directly) ->
                // 403 with the same errorCode JSON shape as every other error.
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"errorCode\":\"FORBIDDEN\"}");
                }));
        return http.build();
    }

    private static void writeUserJson(HttpServletResponse response, ObjectMapper objectMapper,
                                       PortalUserPrincipal principal) throws java.io.IOException {
        response.setContentType("application/json;charset=UTF-8");
        Map<String, String> body = new LinkedHashMap<>();
        body.put("username", principal.getUsername());
        body.put("displayName", principal.displayName());
        body.put("role", principal.role());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
