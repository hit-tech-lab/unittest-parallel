package io.hit.ptest.db;

import java.util.function.BooleanSupplier;

/**
 * Tài nguyên DB riêng của một context (database, schema hoặc H2 in-memory) cùng cách giải phóng; {@code exists} chỉ dùng để
 * báo cáo "còn sót sau khi đóng" (thật: còn tồn tại).
 */
public record DbResource(String description, String url, String username, String password, Runnable release, BooleanSupplier exists) {
}
