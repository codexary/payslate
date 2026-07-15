package com.elsworne.payslate.api;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
class HttpMetrics implements MeterFilter {
  private static final Set<String> HTTP_TAGS = Set.of("uri", "method", "status");

  @Override
  public Meter.Id map(Meter.Id id) {
    if (!id.getName().equals("http.server.requests")) return id;
    return id.replaceTags(
        id.getTags().stream().filter(tag -> HTTP_TAGS.contains(tag.getKey())).toList());
  }
}
