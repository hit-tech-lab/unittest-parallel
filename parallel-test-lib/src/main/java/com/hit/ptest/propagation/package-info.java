/**
 * Mang binding của test sang thread khác: header HTTP ({@code BindingHeaderInterceptor} ở phía client,
 * {@code BindingFilter} ở phía server) và {@code TaskDecorator} cho executor Spring.
 *
 * <p>Chỉ {@link com.hit.ptest.propagation.BindingHeaderInterceptor} là API công khai (consumer gắn vào RestClient);
 * các lớp còn lại là nội bộ (@apiNote internal).
 */
package com.hit.ptest.propagation;
