package io.hit.ptest.guard;

/** Mức xử lý khi một luật guard bị vi phạm: làm test FAIL, chỉ ghi cảnh báo WARN, hoặc tắt hẳn OFF. */
public enum Severity {
    FAIL, WARN, OFF
}
