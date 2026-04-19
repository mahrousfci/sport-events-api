package com.sportevents.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * API-key security.
 *
 * Rules:
 *  - GET  /api/events/**     → public (anyone can read/subscribe)
 *  - POST, PATCH /api/events/** → require X-API-Key header
 *  - Swagger UI, WS docs     → public
 *  - WebSocket /ws-events    → public (token-in-URL not implemented for brevity)
 *  - H2 console (dev only)   → public
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final RequestMatcher PUBLIC_ENDPOINTS = new OrRequestMatcher(
        new AntPathRequestMatcher("/api/events/**", HttpMethod.GET.name()),
        new AntPathRequestMatcher("/swagger-ui.html"),
        new AntPathRequestMatcher("/swagger-ui/**"),
        new AntPathRequestMatcher("/api-docs"),
        new AntPathRequestMatcher("/api-docs/**"),
        new AntPathRequestMatcher("/api/ws-docs"),
        new AntPathRequestMatcher("/ws-events/**"),
        new AntPathRequestMatcher("/h2-console/**")
    );

    @Value("${app.security.api-key}")
    private String apiKey;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(h -> h.frameOptions(f -> f.disable()))  // needed for H2 console
            .addFilterBefore(apiKeyFilter(), UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                // Public read access
                .requestMatchers(HttpMethod.GET, "/api/events/**").permitAll()
                // Swagger / docs
                .requestMatchers(
                    "/swagger-ui.html", "/swagger-ui/**",
                    "/api-docs", "/api-docs/**",
                    "/api/ws-docs"
                ).permitAll()
                // WebSocket
                .requestMatchers("/ws-events/**").permitAll()
                // H2 console (dev)
                .requestMatchers("/h2-console/**").permitAll()
                // Everything else requires a valid API key
                .anyRequest().authenticated()
            );
        return http.build();
    }

    /**
     * Filter that checks the {@code X-API-Key} request header on protected routes.
     * Returns 401 immediately if the key is absent or wrong, otherwise it
     * marks the request as authenticated for downstream authorization checks.
     */
    private OncePerRequestFilter apiKeyFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected boolean shouldNotFilter(HttpServletRequest request) {
                return PUBLIC_ENDPOINTS.matches(request);
            }

            @Override
            protected void doFilterInternal(HttpServletRequest req,
                                            HttpServletResponse res,
                                            FilterChain chain)
                    throws ServletException, IOException {
                String key = req.getHeader("X-API-Key");
                if (key == null || !key.equals(apiKey)) {
                    SecurityContextHolder.clearContext();
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\","
                            + "\"message\":\"Missing or invalid API key\"}");
                    return;
                }

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                "api-key-client",
                                null,
                                AuthorityUtils.NO_AUTHORITIES
                        );
                SecurityContextHolder.getContext().setAuthentication(authentication);
                chain.doFilter(req, res);
            }
        };
    }
}
