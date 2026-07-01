package com.elsworne.payslate.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.Yaml;

@SpringBootTest
class ApiOpenApiContractTest {
  @Autowired
  @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
  RequestMappingHandlerMapping mappings;

  @Test
  void documentedPathsAndSchemasMatchControllerContract() throws Exception {
    Map<String, Object> document = new Yaml().load(Files.readString(Path.of("openapi.yaml")));
    assertThat(document.get("openapi")).isEqualTo("3.1.0");
    Map<String, Object> paths = map(document.get("paths"));
    var implemented =
        mappings.getHandlerMethods().keySet().stream()
            .flatMap(info -> info.getPathPatternsCondition().getPatterns().stream())
            .map(Object::toString)
            .filter(path -> path.startsWith("/v1/"))
            .collect(java.util.stream.Collectors.toSet());
    assertThat(paths.keySet()).containsExactlyInAnyOrderElementsOf(implemented);
    assertSchema(
        paths,
        "/v1/employees",
        "post",
        true,
        "application/json",
        Set.of("name", "employment", "hourlyRate", "bsb", "account", "deductions"),
        "application/json",
        Set.of("id"));
    assertSchema(
        paths,
        "/v1/employees/{id}",
        "get",
        false,
        null,
        Set.of(),
        "application/json",
        Set.of("id", "name", "employment", "hourly_rate"));
    assertSchema(
        paths,
        "/v1/timesheets",
        "post",
        true,
        "multipart/form-data",
        Set.of("file"),
        "application/json",
        Set.of("rows"));
    assertSchema(
        paths,
        "/v1/pay-runs",
        "post",
        true,
        "application/json",
        Set.of("periodStart", "periodEnd"),
        "application/json",
        Set.of("id"));
    assertSchema(
        paths,
        "/v1/pay-runs/calendar",
        "post",
        true,
        "application/json",
        Set.of("frequency", "anchorPayDate", "payDate"),
        "application/json",
        Set.of("id"));
    assertSchema(
        paths,
        "/v1/pay-runs/{id}",
        "get",
        false,
        null,
        Set.of(),
        "application/json",
        Set.of("state"));
    assertSchema(
        paths,
        "/v1/pay-runs/{id}/{event}",
        "post",
        false,
        null,
        Set.of(),
        "application/json",
        Set.of("state"));
    assertSchema(
        paths,
        "/v1/pay-runs/{id}/payslips/{employeeId}",
        "get",
        false,
        null,
        Set.of(),
        "application/json",
        Set.of("gross", "tax", "net"));
    assertSchema(
        paths, "/v1/pay-runs/{id}/aba", "get", false, null, Set.of(), "text/plain", Set.of());
  }

  @Test
  void everyOperationDeclaresSharedErrorResponses() throws Exception {
    Map<String, Object> document = new Yaml().load(Files.readString(Path.of("openapi.yaml")));
    var errors =
        Map.ofEntries(
            Map.entry("400", "BadRequest"),
            Map.entry("401", "Unauthorized"),
            Map.entry("403", "Forbidden"),
            Map.entry("404", "NotFound"),
            Map.entry("409", "Conflict"));
    for (var path : map(document.get("paths")).entrySet()) {
      for (var method :
          Set.of("get", "put", "post", "delete", "options", "head", "patch", "trace")) {
        if (!map(path.getValue()).containsKey(method)) continue;
        var responses = map(map(map(path.getValue()).get(method)).get("responses"));
        assertThat(responses).as("%s %s", method, path.getKey()).containsKeys("401", "403");
        if (path.getKey().contains("{")) assertThat(responses).containsKey("404");
        for (var error : errors.entrySet())
          if (responses.containsKey(error.getKey()))
            assertThat(map(responses.get(error.getKey())))
                .isEqualTo(Map.of("$ref", "#/components/responses/" + error.getValue()));
      }
    }
    var components = map(document.get("components"));
    for (var name : errors.values()) {
      var response = map(map(components.get("responses")).get(name));
      assertThat(response.get("description")).asString().isNotBlank();
      var content = map(map(response.get("content")).get("application/problem+json"));
      assertThat(map(content.get("schema")))
          .isEqualTo(Map.of("$ref", "#/components/schemas/Problem"));
    }
  }

  private static void assertSchema(
      Map<String, Object> paths,
      String path,
      String method,
      boolean hasRequest,
      String requestMedia,
      Set<String> requestFields,
      String responseMedia,
      Set<String> responseFields) {
    Map<String, Object> operation = map(map(paths.get(path)).get(method));
    if (hasRequest) {
      Map<String, Object> request = map(operation.get("requestBody"));
      assertThat(request.get("required")).isEqualTo(true);
      Map<String, Object> schema =
          map(map(map(request.get("content")).get(requestMedia)).get("schema"));
      assertThat(schema.get("type")).isEqualTo("object");
      assertThat(map(schema.get("properties")).keySet())
          .containsExactlyInAnyOrderElementsOf(requestFields);
      assertThat((java.util.List<?>) schema.get("required")).isNotEmpty();
    } else assertThat(operation).doesNotContainKey("requestBody");
    Map<String, Object> response = map(map(operation.get("responses")).get("200"));
    Map<String, Object> schema =
        map(map(map(response.get("content")).get(responseMedia)).get("schema"));
    if (responseFields.isEmpty()) assertThat(schema.get("type")).isEqualTo("string");
    else {
      assertThat(schema.get("type")).isEqualTo("object");
      assertThat(map(schema.get("properties")).keySet())
          .containsExactlyInAnyOrderElementsOf(responseFields);
      assertThat(
              ((java.util.List<?>) schema.get("required"))
                  .stream().map(Object::toString).collect(java.util.stream.Collectors.toSet()))
          .isEqualTo(responseFields);
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object value) {
    return (Map<String, Object>) value;
  }
}
