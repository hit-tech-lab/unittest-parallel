package io.hit.ptest.guard;

import io.hit.ptest.ParallelTestException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Áp mức FAIL/WARN/OFF lên danh sách vi phạm: WARN ghi log, OFF bỏ, còn lại ném {@link ParallelTestException.Guard}. */
public final class GuardEnforcer {

    private static final Logger LOG = LoggerFactory.getLogger(GuardEnforcer.class);

    private GuardEnforcer() {
    }

    /** Ném {@code ParallelTestException.Guard} nếu còn vi phạm mức FAIL; vi phạm mức WARN chỉ được ghi log. */
    public static void enforce(String header, List<Violation> violations) {
        List<Violation> failing = new ArrayList<>();
        List<Violation> warning = new ArrayList<>();
        for (Violation v : violations) {
            switch (GuardSettings.severity(v.rule())) {
                case FAIL -> failing.add(v);
                case WARN -> warning.add(v);
                case OFF -> { }
            }
        }
        if (!warning.isEmpty()) {
            GuardStats.VIOLATIONS_WARNED.add(warning.size());
            LOG.warn("{}", format(header + " (warning only: ptest.guard.<rule>=warn)", warning));
        }
        if (!failing.isEmpty()) {
            GuardStats.VIOLATIONS_FAILED.add(failing.size());
            throw error(header, failing);
        }
    }

    /** Tạo lỗi guard từ các vi phạm (không áp mức; dùng khi đã biết mức FAIL). */
    public static ParallelTestException.Guard error(String header, List<Violation> violations) {
        return new ParallelTestException.Guard(format(header, violations),
                violations.stream().map(v -> v.rule().name()).toList());
    }

    static String format(String header, List<Violation> violations) {
        return "[ptest-guard] " + header + " (" + violations.size() + " violation" + (violations.size() == 1 ? "" : "s") + ")\n\n"
                + violations.stream().map(Violation::toString).collect(Collectors.joining("\n\n"));
    }
}
