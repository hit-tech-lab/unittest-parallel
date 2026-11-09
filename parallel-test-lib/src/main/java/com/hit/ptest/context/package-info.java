/**
 * Vòng đời context: phát hiện context do thư viện quản lý ({@code ParallelContextCustomizer}), sổ sách và giới hạn số
 * context ({@code ContextRegistry}), trạng thái mỗi context ({@code ManagedContext}), dọn dẹp ({@code ContextLifecycle}),
 * cấu hình toàn cục ({@code PtestSettings}) và nguồn thuộc tính cấp DB ({@code ProvisionedDbPropertySource}, nối
 * {@code db} với context nên nằm ở đây để {@code db} không phụ thuộc ngược).
 *
 * @apiNote internal: consumer chỉ dùng API gốc {@code com.hit.ptest}; lớp ở đây public chỉ để xuyên package.
 */
package com.hit.ptest.context;
