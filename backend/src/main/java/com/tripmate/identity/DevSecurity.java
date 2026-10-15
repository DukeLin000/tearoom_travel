package com.tripmate.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripmate.shared.Errors;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class DevSecurity {
    public static void assertDevOnly(String[] profiles) {
        if (profiles.length != 1 || !profiles[0].equals("dev")) {
            throw new IllegalStateException("Phase 1 local authentication requires ONLY the dev profile; production OIDC is not implemented. Active profiles: " + Arrays.toString(profiles));
        }
    }
    public DevSecurity(Environment environment) { assertDevOnly(environment.getActiveProfiles()); }
    @Bean
    SecurityContextRepository contextRepository() { return new HttpSessionSecurityContextRepository(); }
    @Bean
    SecurityFilterChain security(HttpSecurity http, SecurityContextRepository repository, ObjectMapper mapper) throws Exception {
        http.securityContext(config -> config.securityContextRepository(repository))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/csrf", "/api/v1/auth/dev-login", "/actuator/health/**").permitAll()
                .anyRequest().authenticated())
            .requestCache(cache -> cache.disable())
            .formLogin(form -> form.disable()).httpBasic(basic -> basic.disable())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(401); response.setContentType("application/json");
                    mapper.writeValue(response.getWriter(), Errors.body("UNAUTHENTICATED", "Sign in required", request));
                })
                .accessDeniedHandler((request, response, exception) -> {
                    response.setStatus(403); response.setContentType("application/json");
                    mapper.writeValue(response.getWriter(), Errors.body("FORBIDDEN", "Request not permitted; refresh the security token", request));
                }))
            .logout(logout -> logout.logoutUrl("/api/v1/auth/logout")
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
            .addFilterBefore(new OncePerRequestFilter() {
                @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
                    String trace = UUID.randomUUID().toString();
                    request.setAttribute("traceId", trace); response.setHeader("X-Trace-Id", trace);
                    chain.doFilter(request, response);
                }
            }, CsrfFilter.class);
        return http.build();
    }
}
