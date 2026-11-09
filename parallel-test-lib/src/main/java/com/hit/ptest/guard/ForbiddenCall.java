package com.hit.ptest.guard;

import com.hit.ptest.ParallelTestException;

import java.util.Arrays;
import java.util.List;

/** {@code com.foo.Type#method(param.Type, ...)}, {@code #method()} (không tham số) hoặc {@code #method(*)} (bất kỳ). */
public record ForbiddenCall(String type, String method, List<String> params) {

    public static ForbiddenCall parse(String spec) {
        int hash = spec.indexOf('#');
        int open = spec.indexOf('(', Math.max(hash, 0));
        if (hash < 1 || open < 0 || !spec.endsWith(")")) {
            throw new ParallelTestException.Config("CONFIG: bad forbidden-call spec '" + spec + "', expected Type#method(params)");
        }
        String inner = spec.substring(open + 1, spec.length() - 1).trim();
        List<String> params = inner.isEmpty() ? List.of() : Arrays.stream(inner.split(",")).map(String::trim).toList();
        return new ForbiddenCall(spec.substring(0, hash).trim(), spec.substring(hash + 1, open).trim(), params);
    }

    boolean anyParams() {
        return params.size() == 1 && params.get(0).equals("*");
    }

    @Override
    public String toString() {
        return type + "#" + method + "(" + String.join(", ", params) + ")";
    }
}
