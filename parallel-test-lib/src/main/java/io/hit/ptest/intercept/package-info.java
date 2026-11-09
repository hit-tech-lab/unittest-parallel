/**
 * Cơ chế chặn bean (real/spy/mock theo tên bean): {@code SpyHostPostProcessor} (trước AOP) đặt host của spy bên trong proxy,
 * {@code InterceptingBeanPostProcessor} (sau AOP) bọc bean,
 * {@code BeanInterceptor} định tuyến lời gọi, {@code TestBinding} gắn test với thread. Chỉ phụ thuộc API gốc;
 * thông tin context đi qua {@code InterceptOwner} để không phụ thuộc {@code context}.
 *
 * @apiNote internal: lớp ở đây public chỉ để xuyên package.
 */
package io.hit.ptest.intercept;
