package com.hit.ptest.guard;

import com.hit.ptest.ParallelTestException;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Quét tĩnh cả thư mục class của source set test: mọi lớp test (PT03, PT06, PT07, PT08, PT13; thêm PT01 cho lớp Spring
 * test) và mọi lớp hàm tiện ích (PT07), để liệt kê TẤT CẢ vi phạm một lần, trước khi khởi động bất kỳ context nào.
 * Chỉ quét lớp có method test hoặc lớp tiện ích cấp cao; lớp abstract, lớp trong package thuộc
 * {@code ptest.guard.scan-exclude} và lớp ẩn danh bị bỏ qua.
 */
public final class ParallelSafetyScanner {

    private ParallelSafetyScanner() {
    }

    /** Quét thư mục output chứa {@code anchor} (vd target/test-classes). Kết quả chưa áp mức FAIL/WARN/OFF. */
    public static List<Violation> scan(Class<?> anchor) {
        return scan(anchor, new String[0]);
    }

    /** Như {@link #scan(Class)} nhưng chỉ quét lớp có tên bắt đầu bằng một trong các tiền tố (rỗng = tất cả). */
    public static List<Violation> scan(Class<?> anchor, String... onlyPrefixes) {
        Path root;
        try {
            root = Path.of(anchor.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException | NullPointerException e) {
            throw new IllegalStateException("Cannot locate the classes of " + anchor, e);
        }
        if (!Files.isDirectory(root)) {
            throw new IllegalStateException("Expected a class directory, got " + root);
        }
        List<String> excluded = GuardSettings.scanExclude();
        List<Violation> out = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".class")).sorted(Comparator.naturalOrder()).toList()) {
                String name = root.relativize(p).toString().replace(p.getFileSystem().getSeparator(), ".");
                name = name.substring(0, name.length() - ".class".length());
                if (name.endsWith("package-info") || name.endsWith("module-info") || excluded.stream().anyMatch(name::startsWith)
                        || onlyPrefixes.length > 0 && Arrays.stream(onlyPrefixes).noneMatch(name::startsWith)) {
                    continue;
                }
                Class<?> c;
                try {
                    c = Class.forName(name, false, anchor.getClassLoader());
                } catch (Throwable e) {
                    continue; // lớp không nạp được (thiếu dependency tùy chọn): không thuộc phạm vi quét
                }
                if (c.isAnonymousClass() || c.isSynthetic() || c == anchor || anchor.isAssignableFrom(c)) {
                    continue;
                }
                if (ClassRules.hasTests(c)) {
                    if (!Modifier.isAbstract(c.getModifiers())) {
                        out.addAll(ClassRules.checkManaged(c)); // PT01: chỉ cho lớp Spring test
                        out.addAll(ClassRules.check(c));
                    }
                } else if (c.getEnclosingClass() == null) {
                    out.addAll(ClassRules.checkHelper(c));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.stream().distinct().toList();
    }

    /** Quét và ném {@link ParallelTestException.Guard} nếu còn vi phạm FAIL (vi phạm WARN chỉ log). */
    public static void scanAndEnforce(Class<?> anchor) {
        GuardEnforcer.enforce("Test code base is not parallel-safe", scan(anchor));
    }
}
