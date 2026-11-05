package com.hittech.paralleltest.guard;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;

/**
 * Static check of a whole test source set: every Spring test class (all rules) and every helper class
 * (forbidden calls), so ALL violations are listed at once, before any context is started.
 */
public final class ParallelSafetyScanner {

    private static final String BOOTSTRAP_WITH = "org.springframework.test.context.BootstrapWith";
    private static final String EXTEND_WITH = "org.junit.jupiter.api.extension.ExtendWith";
    private static final String TESTABLE = "org.junit.platform.commons.annotation.Testable";

    private ParallelSafetyScanner() {
    }

    /** Scans the classes of the output directory that contains {@code anchor} (e.g. target/test-classes). */
    public static List<Violation> scan(Class<?> anchor) {
        GuardSettings settings = GuardSettings.get();
        Path root;
        try {
            root = Path.of(anchor.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException | NullPointerException e) {
            throw new IllegalStateException("Cannot locate the classes of " + anchor, e);
        }
        if (!Files.isDirectory(root)) {
            throw new IllegalStateException("Expected a class directory, got " + root);
        }
        List<Violation> out = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".class")).sorted(Comparator.naturalOrder()).toList()) {
                String name = root.relativize(p).toString().replace(p.getFileSystem().getSeparator(), ".");
                name = name.substring(0, name.length() - ".class".length());
                if (name.endsWith("package-info") || name.endsWith("module-info")) {
                    continue;
                }
                Class<?> c;
                try {
                    c = Class.forName(name, false, anchor.getClassLoader());
                } catch (Throwable e) {
                    continue;
                }
                if (c.isAnonymousClass() || c.isSynthetic() || c == anchor) {
                    continue;
                }
                if (isSpringTest(c)) {
                    if (!Modifier.isAbstract(c.getModifiers()) && hasTests(c)) {
                        out.addAll(ClassRules.doCheck(c, settings));
                    }
                } else if (c.getEnclosingClass() == null && !hasTests(c) && !ClassRules.isJUnitIsolated(c)) {
                    out.addAll(CallScanner.scan(c, settings.forbiddenCalls())); // test helpers
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.stream().distinct().toList();
    }

    static boolean isSpringTest(Class<?> c) {
        for (Class<?> h : ClassRules.hierarchyAndEnclosing(c)) {
            MergedAnnotations a = MergedAnnotations.from(h, SearchStrategy.DIRECT);
            if (a.isPresent(BOOTSTRAP_WITH)) {
                return true;
            }
            if (a.stream(EXTEND_WITH).anyMatch(e -> List.of(e.getClassArray("value")).stream()
                    .anyMatch(x -> x.getName().equals("org.springframework.test.context.junit.jupiter.SpringExtension")))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasTests(Class<?> c) {
        for (Class<?> h = c; h != null && h != Object.class; h = h.getSuperclass()) {
            for (Method m : h.getDeclaredMethods()) {
                if (MergedAnnotations.from(m).isPresent(TESTABLE)) {
                    return true;
                }
            }
        }
        for (Class<?> nested : c.getDeclaredClasses()) {
            if (!Modifier.isStatic(nested.getModifiers()) && hasTests(nested)) {
                return true; // @Nested
            }
        }
        return false;
    }
}
