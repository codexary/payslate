package com.elsworne.payslate.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

@Configuration
class ApiSecurity {
  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, JsonMapper mapper) throws Exception {
    var authenticationEntryPoint = new BearerTokenAuthenticationEntryPoint();
    var accessDeniedHandler = new BearerTokenAccessDeniedHandler();
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
            oauth ->
                oauth
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter()))
                    .authenticationEntryPoint(
                        (request, response, error) -> {
                          authenticationEntryPoint.commence(request, response, error);
                          writeProblem(mapper, request, response, "Authentication is required.");
                        })
                    .accessDeniedHandler(
                        (request, response, error) -> {
                          accessDeniedHandler.handle(request, response, error);
                          writeProblem(mapper, request, response, "Access is denied.");
                        }))
        .build();
  }

  private static void writeProblem(
      JsonMapper mapper, HttpServletRequest request, HttpServletResponse response, String detail)
      throws IOException {
    var status = HttpStatus.valueOf(response.getStatus());
    var problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(status.getReasonPhrase());
    problem.setInstance(URI.create(request.getRequestURI()));
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    mapper.writeValue(response.getOutputStream(), problem);
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
