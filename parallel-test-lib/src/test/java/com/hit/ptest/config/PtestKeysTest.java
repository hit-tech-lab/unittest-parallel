package com.hit.ptest.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Khóa đồng bộ giữa PtestKeys, additional-spring-configuration-metadata.json và source (không còn literal key rải rác). */
class PtestKeysTest {

    private static final String METADATA = "/META-INF/additional-spring-configuration-metadata.json";

    private static List<String> metadataNames() throws IOException {
        try (InputStream in = PtestKeysTest.class.getResourceAsStream(METADATA)) {
            assertThat(in).as(METADATA).isNotNull();
            return JsonPath.read(new String(in.readAllBytes(), StandardCharsets.UTF_8), "$.properties[*].name");
        }
    }

    @Test
    void metadataParsesAndMatchesContextKeys() throws IOException {
        assertThat(metadataNames()).containsExactlyInAnyOrderElementsOf(PtestKeys.CONTEXT_KEYS);
    }

    @Test
    void metadataHintsOnlyReferenceDeclaredProperties() throws IOException {
        try (InputStream in = PtestKeysTest.class.getResourceAsStream(METADATA)) {
            List<String> hinted = JsonPath.read(new String(in.readAllBytes(), StandardCharsets.UTF_8), "$.hints[*].name");
            assertThat(metadataNames()).containsAll(hinted);
        }
    }

    @Test
    void globalKeysAreNeverInMetadata() throws IOException {
        List<String> names = metadataNames();
        assertThat(PtestKeys.GLOBAL_KEYS).doesNotContainAnyElementsOf(names);
        assertThat(names).noneMatch(n -> n.startsWith(PtestKeys.GUARD_PREFIX));
    }

    @Test
    void everyKeyConstantIsClassifiedExactlyOnce() throws IllegalAccessException {
        List<String> all = new ArrayList<>();
        for (Field f : PtestKeys.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == String.class && f.getName() != null) {
                all.add((String) f.get(null));
            }
        }
        List<String> classified = new ArrayList<>(PtestKeys.CONTEXT_KEYS);
        classified.addAll(PtestKeys.GLOBAL_KEYS);
        classified.add(PtestKeys.GUARD_PREFIX);
        classified.add(PtestKeys.CONTEXT_KEY);
        classified.add(PtestKeys.CONTEXT_NAME);
        assertThat(classified).as("mọi hằng số key phải thuộc đúng một nhóm").containsExactlyInAnyOrderElementsOf(all);
    }

    @Test
    void isGlobalClassifiesKeys() {
        assertThat(PtestKeys.isGlobal(PtestKeys.MAX_CONTEXTS)).isTrue();
        assertThat(PtestKeys.isGlobal(PtestKeys.guardRule("PT07"))).isTrue();
        assertThat(PtestKeys.isGlobal(PtestKeys.DB_MODE)).isFalse();
    }

    @Test
    void noHardcodedPtestKeyLiteralOutsidePtestKeys() throws IOException {
        Path root = Path.of("src/main/java");
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                if (p.getFileName().toString().equals("PtestKeys.java")) {
                    continue;
                }
                if (Files.readString(p).contains("\"ptest.")) {
                    offenders.add(p.toString());
                }
            }
        }
        assertThat(offenders).as("dùng hằng số trong PtestKeys thay cho literal").isEmpty();
    }
}
