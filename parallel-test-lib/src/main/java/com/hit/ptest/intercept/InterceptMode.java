package com.hit.ptest.intercept;

/** Chế độ của một bean trong phạm vi MỘT test: bean thật, spy bọc bean thật, hoặc mock. */
public enum InterceptMode {
    /** Gọi bean thật (mặc định khi không khai báo). */
    REAL,
    /**
     * Spy chuẩn Mockito riêng của test, hành vi như {@code Mockito.spy}/{@code @MockitoSpyBean}: method thật chạy trên chính
     * spy nên {@code this.x()} bên trong bean đi qua spy (verify/doReturn/doThrow thấy nó), và spy nằm BÊN TRONG proxy AOP
     * (@Transactional, @Async, @Cacheable vẫn áp dụng quanh spy). Mỗi test có spy riêng, tạo lúc bắt đầu test bằng cách
     * sao chép field của bean thật (như Mockito): field kiểu tham chiếu dùng chung với bean thật, field nguyên thủy của spy
     * tách riêng. Bean là JDK proxy (vd repository Spring Data) không có lớp thật để spy: spy là mock ủy quyền cho proxy,
     * giống {@code @MockitoSpyBean}.
     */
    SPY,
    /** Mock riêng của test, hành vi mặc định của Mockito. */
    MOCK
}
