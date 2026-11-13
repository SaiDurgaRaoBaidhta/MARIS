package com.bart.maris.middleware.security;

import com.bart.maris.middleware.config.MarisProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * Session-based security for the browser UI. Sign-on goes through MRS02A (see AuthController); every other API
 * call needs a signed-on session. Errors are JSON problem details, never redirects or HTML.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SecurityFilterChain api(HttpSecurity http, MarisProperties props, ObjectMapper om,
                            SecurityContextRepository contextRepository) throws Exception {
        http.authorizeHttpRequests(a -> a
                        .requestMatchers("/api/v1/auth/signon", "/api/v1/auth/csrf").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .securityContext(sc -> sc.securityContextRepository(contextRepository))
                .sessionManagement(s -> s.sessionFixation(f -> f.changeSessionId()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> ProblemWriter.write(om, res, HttpStatus.UNAUTHORIZED,
                                "NOT_SIGNED_ON", "Sign on to MARIS first."))
                        .accessDeniedHandler((req, res, ex) -> ProblemWriter.write(om, res, HttpStatus.FORBIDDEN,
                                "FORBIDDEN", "The request was refused. Reload the page and try again.")));
        if (props.security().csrfEnabled()) {
            http.csrf(c -> c.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()));
        } else {
            http.csrf(AbstractHttpConfigurer::disable);
        }
        return http.build();
    }
}
