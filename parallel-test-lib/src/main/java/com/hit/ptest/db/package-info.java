/**
 * Cấp DB riêng cho mỗi context: {@code DatabaseProvisioner} với H2 và PostgreSQL (Testcontainers), kết quả là
 * {@code DbResource}. Không phụ thuộc package nào khác ngoài API gốc.
 *
 * @apiNote internal: lớp ở đây public chỉ để xuyên package.
 */
package com.hit.ptest.db;
