package com.documind;

import com.documind.common.security.JwtAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@SpringBootApplication
public class DocumindApplication {

    // Register JWT handling only inside the Spring Security filter chain.
    // Prevent Spring Boot from registering this @Component as a separate servlet filter.
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter jwtFilter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(jwtFilter);
        registration.setEnabled(false);
        return registration;
    }


    public static void main(String[] args) {
        SpringApplication.run(DocumindApplication.class, args);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Configuration
    @EnableWebSecurity
    static class SecurityConfig {

        @Bean
        public SecurityFilterChain filterChain(
                HttpSecurity http,
                JwtAuthenticationFilter jwtFilter
        ) throws Exception {

            http
                    .cors(cors -> {})
                    .exceptionHandling(exceptions -> exceptions
                            .authenticationEntryPoint((request, response, error) ->
                                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required"))
                    )
                    .csrf(csrf -> csrf
                            .csrfTokenRepository(
                                    CookieCsrfTokenRepository.withHttpOnlyFalse()
                            )
                            .ignoringRequestMatchers(
                                    "/auth/**",
                                    "/organizations/**",
                                    "/documents/**",
                                    "/health/**",
                                    "/system/info",
                                    "/actuator/**"
                            )
                    )

                    .httpBasic(httpBasic -> httpBasic.disable())
                    .formLogin(form -> form.disable())

                    .sessionManagement(session -> session
                            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                    )

                    .authorizeHttpRequests(authz -> authz
                            .requestMatchers(
                                    "/health/**",
                                    "/system/info",
                                    "/actuator/**",
                                    "/auth/register",
                                    "/auth/login",
                                    "/auth/refresh"
                            ).permitAll()
                            .anyRequest().authenticated()
                    )

                    .headers(headers -> headers
                            .contentSecurityPolicy(csp ->
                                    csp.policyDirectives(
                                            "default-src 'self'; " +
                                                    "script-src 'self'; " +
                                                    "style-src 'self' 'unsafe-inline'; " +
                                                    "img-src 'self' data:; " +
                                                    "font-src 'self'"
                                    )
                            )
                            .frameOptions(frame -> frame.deny())
                            .contentTypeOptions(contentTypeOptions -> {})
                            .httpStrictTransportSecurity(hsts ->
                                    hsts.maxAgeInSeconds(31536000)
                                            .includeSubDomains(true)
                            )
                            .referrerPolicy(referrer ->
                                    referrer.policy(
                                            ReferrerPolicyHeaderWriter.ReferrerPolicy
                                                    .STRICT_ORIGIN_WHEN_CROSS_ORIGIN
                                    )
                            )
                            .permissionsPolicy(permissions ->
                                    permissions.policy(
                                            "geolocation=(), microphone=(), camera=()"
                                    )
                            )
                    )

                    .addFilterBefore(
                            jwtFilter,
                            UsernamePasswordAuthenticationFilter.class
                    );

            return http.build();
        }
    }
}