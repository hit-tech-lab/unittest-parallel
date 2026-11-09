/**
 * Guard an toàn song song: luật tĩnh (quét reflection/bytecode lớp test) và luật lúc chạy (JDBC, pool, ngân sách context).
 * Dùng được độc lập với cơ chế can thiệp bean: chỉ phụ thuộc API gốc ({@code com.hit.ptest}) và Spring/JUnit; việc gán
 * vi phạm trên thread khác cho đúng test đi qua {@link com.hit.ptest.guard.GuardScope#useOwnerResolver}. Xem GUARD.md.
 */
package com.hit.ptest.guard;
