package com.fintrack.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Boots the full Spring application against an externally-managed Postgres, fetches the springdoc
 * OpenAPI document at {@code /v3/api-docs}, normalises it (sorted keys, pretty-printed) and writes
 * it to {@code <repo>/frontend/openapi.json}. The committed file is the canonical contract surface
 * consumed by the frontend type generator.
 *
 * <p>Opt-in: only runs when {@code -Dopenapi.generate=true} is supplied. The {@code
 * scripts/regen-openapi.sh} wrapper sets the flag (and starts an ephemeral Postgres container) so
 * day-to-day {@code mvn verify} stays unaffected.
 *
 * <p>Postgres connection is read from {@code SPRING_DATASOURCE_*} env vars; the script wires those
 * so the test does not depend on Testcontainers' Docker auto-detection (which is unreliable on
 * Windows + Docker Desktop's user-mode pipe).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "openapi.generate", matches = "true")
@TestPropertySource(
        properties = {
            "fintrack.receipt-dir=${java.io.tmpdir}/fintrack-openapi-gen/receipts",
            "fintrack.mail.enabled=",
            "spring.flyway.enabled=true",
            "jwt.secret=openapi-gen-secret-must-be-at-least-256-bits-long-padding-padding",
            "spring.task.scheduling.pool.size=0",
            "price-api.coingecko.enabled=false",
            "price-api.exchange-rate.enabled=false",
            "price-api.tefas.enabled=false"
        })
class OpenApiSpecGeneratorTest {

    @Autowired MockMvc mockMvc;

    @Test
    void writesNormalisedOpenApiSpec() throws Exception {
        MvcResult result =
                mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        String raw = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(raw).isNotBlank();

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        JsonNode parsed = mapper.readTree(raw);
        JsonNode normalised = sortKeys(mapper, parsed);

        assertThat(normalised.path("paths").size())
                .as("OpenAPI document should contain at least one path")
                .isPositive();

        Path target = repoRoot().resolve("frontend").resolve("openapi.json");
        Files.createDirectories(target.getParent());
        String pretty = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(normalised);
        Files.writeString(target, pretty + "\n", StandardCharsets.UTF_8);
    }

    private static JsonNode sortKeys(ObjectMapper mapper, JsonNode node) {
        if (node.isObject()) {
            ObjectNode src = (ObjectNode) node;
            ObjectNode sorted = mapper.createObjectNode();
            List<String> keys = new ArrayList<>();
            src.fieldNames().forEachRemaining(keys::add);
            Collections.sort(keys);
            for (String key : keys) {
                sorted.set(key, sortKeys(mapper, src.get(key)));
            }
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode src = (ArrayNode) node;
            ArrayNode arr = mapper.createArrayNode();
            for (JsonNode child : src) {
                arr.add(sortKeys(mapper, child));
            }
            return arr;
        }
        return node;
    }

    private static Path repoRoot() throws IOException {
        Path cwd = Paths.get("").toAbsolutePath();
        Path candidate = cwd;
        for (int i = 0; i < 5 && candidate != null; i++) {
            if (Files.isDirectory(candidate.resolve("frontend"))
                    && Files.isDirectory(candidate.resolve("backend"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IOException("Could not locate repository root from " + cwd);
    }
}
