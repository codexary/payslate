package com.elsworne.payslate.api;

import java.util.Collection;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class ApiSecurity {
  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/actuator/health/**", "/error")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/v1/pay-runs/*/payslips/*")
                    .hasAnyRole("PAYROLL_OFFICER", "EMPLOYEE")
                    .requestMatchers("/v1/**")
                    .hasRole("PAYROLL_OFFICER")
                    .anyRequest()
                    .denyAll())
        .oauth2ResourceServer(
            oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter())))
        .build();
  }

  private static JwtAuthenticationConverter jwtConverter() {
    var converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(
        jwt -> {
          Object claim = jwt.getClaim("roles");
          if (claim == null && jwt.getClaim("realm_access") instanceof java.util.Map<?, ?> realm)
            claim = realm.get("roles");
          if (!(claim instanceof Collection<?> roles)) return List.<GrantedAuthority>of();
          return roles.stream()
              .map(Object::toString)
              .filter(role -> role.equals("PAYROLL_OFFICER") || role.equals("EMPLOYEE"))
              .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
              .toList();
        });
    return converter;
  }
}
