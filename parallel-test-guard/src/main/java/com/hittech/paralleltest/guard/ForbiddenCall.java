package com.hittech.paralleltest.guard;

import java.util.Arrays;
import java.util.List;

/** {@code com.foo.Type#method(param.Type, ...)}, {@code #method()} (no params) or {@code #method(*)} (any). */
public record ForbiddenCall(String type, String method, List<String> params) {

    public static ForbiddenCall parse(String spec) {
        int hash = spec.indexOf('#');
        int open = spec.indexOf('(', hash);
        if (hash < 1 || open < 0 || !spec.endsWith(")")) {
            throw new IllegalArgumentException("Bad forbidden-call spec '" + spec + "', expected Type#method(params)");
        }
        String inner = spec.substring(open + 1, spec.length() - 1).trim();
        List<String> params = inner.isEmpty() ? List.of()
                : Arrays.stream(inner.split(",")).map(String::trim).toList();
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
