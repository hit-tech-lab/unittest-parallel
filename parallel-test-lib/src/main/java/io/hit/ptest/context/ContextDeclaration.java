package io.hit.ptest.context;

import io.hit.ptest.ParallelSpringTest;

import java.util.List;

/**
 * Khai báo của một context profile, chuẩn hóa (đã sắp xếp) để so sánh và làm khóa cache.
 *
 * @param name tên context
 * @param wrap mẫu bean được bọc, xem {@link ParallelSpringTest#wrap()}
 * @param exclude mẫu bean không bọc
 * @param schemas schema DB tùy chỉnh
 * @param mockOnly khai báo {@code @PerTestMock} cấp class (thay hoặc tạo bean; nằm trong khóa context)
 */
public record ContextDeclaration(String name, List<String> wrap, List<String> exclude, List<String> schemas,
                                 List<MockOnlyBean> mockOnly) {

    public static ContextDeclaration of(String name, String[] wrap, String[] exclude, String[] schemas, List<MockOnlyBean> mockOnly) {
        return new ContextDeclaration(name, sorted(wrap), sorted(exclude), sorted(schemas),
                mockOnly.stream().sorted(MockOnlyBean.ORDER).toList());
    }

    private static List<String> sorted(String[] values) {
        return List.of(values).stream().sorted().toList();
    }

    /** Cùng khai báo như nhau ở phần dùng để phát hiện gõ nhầm tên context (không tính mock-only: đó chỉ tách context). */
    boolean sameNameSemantics(ContextDeclaration o) {
        return wrap.equals(o.wrap) && exclude.equals(o.exclude) && schemas.equals(o.schemas);
    }

    /** Mô tả không kèm mock cấp class (diff khóa context in mỗi mock thành một dòng riêng). */
    String describeWithoutMocks() {
        return "ptest[context=" + name + ", wrap=" + wrap + ", exclude=" + exclude + ", schemas=" + schemas + "]";
    }

    @Override
    public String toString() {
        return "ptest[context=" + name + ", wrap=" + wrap + ", exclude=" + exclude + ", schemas=" + schemas
                + ", mockOnly=" + mockOnly + "]";
    }
}
