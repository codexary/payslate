package com.elsworne.payslate.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest
class ApiOpenApiContractTest {
  @Autowired
  @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
  RequestMappingHandlerMapping mappings;

  @Test
  void documentedPathsMatchRunningController() throws Exception {
    String yaml = Files.readString(Path.of("openapi.yaml"));
    assertThat(yaml).contains("openapi: 3.1.0");
    var implemented =
        mappings.getHandlerMethods().keySet().stream()
            .flatMap(info -> info.getPathPatternsCondition().getPatterns().stream())
            .map(Object::toString)
            .filter(path -> path.startsWith("/v1/"))
            .collect(java.util.stream.Collectors.toSet());
    for (String path : implemented) assertThat(yaml).contains("  " + path + ":");
    assertThat(implemented).hasSize(8);
  }
}
