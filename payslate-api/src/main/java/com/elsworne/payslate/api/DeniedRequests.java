package com.elsworne.payslate.api;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.server.RequestPath;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;

@Component
final class DeniedRequests implements SmartInitializingSingleton {
  private static final Logger LOG = LoggerFactory.getLogger(DeniedRequests.class);

  private final MeterRegistry registry;
  private final ObjectProvider<RequestMappingHandlerMapping> mappings;

  DeniedRequests(
      MeterRegistry registry,
      @Qualifier("requestMappingHandlerMapping")
          ObjectProvider<RequestMappingHandlerMapping> mappings) {
    this.registry = registry;
    this.mappings = mappings;
  }

  @Override
  public void afterSingletonsInstantiated() {
    mappings.getObject().getHandlerMethods().keySet().stream()
        .flatMap(mapping -> mapping.getPatternValues().stream())
        // Business routes only: /error is Spring's internal error dispatch, not an API route.
        .filter(route -> route.startsWith("/v1/"))
        .distinct()
        .forEach(
            route -> {
              for (String status : new String[] {"401", "403", "404"})
                registry.counter("payslate.access.denied", "route", route, "status", status);
            });
    for (String status : new String[] {"401", "403"})
      registry.counter("payslate.access.denied", "route", "UNKNOWN", "status", status);
  }

  void record(HttpServletRequest request, int status) {
    String route = route(request);
    registry
        .counter("payslate.access.denied", "route", route, "status", Integer.toString(status))
        .increment();
    var event =
        LOG.atInfo()
            .addKeyValue("event.action", "access-denied")
            .addKeyValue("event.outcome", "failure")
            .addKeyValue("http.response.status_code", status)
            .addKeyValue("http.request.method", request.getMethod())
            .addKeyValue("http.route", route);
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof JwtAuthenticationToken jwt && jwt.isAuthenticated()) {
      if (jwt.getToken().getSubject() != null)
        event = event.addKeyValue("sub", jwt.getToken().getSubject());
      String employer = jwt.getToken().getClaimAsString("employer_id");
      if (employer != null) event = event.addKeyValue("employer_id", employer);
    }
    event.log("Request denied");
  }

  private String route(HttpServletRequest request) {
    Object matched = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    if (matched != null) return matched.toString();
    // Security can reject before MVC selects a handler. Match registered templates only;
    // never fall back to the raw URI or invoke a controller to discover its route.
    var path =
        RequestPath.parse(request.getRequestURI(), request.getContextPath())
            .pathWithinApplication();
    return mappings.getObject().getHandlerMethods().keySet().stream()
        .filter(mapping -> mapping.getMethodsCondition().getMatchingCondition(request) != null)
        .flatMap(mapping -> Stream.ofNullable(mapping.getPathPatternsCondition()))
        .flatMap(condition -> condition.getPatterns().stream())
        .filter(pattern -> pattern.matches(path))
        .sorted(PathPattern.SPECIFICITY_COMPARATOR)
        .map(PathPattern::getPatternString)
        .findFirst()
        .orElse("UNKNOWN");
  }
}
