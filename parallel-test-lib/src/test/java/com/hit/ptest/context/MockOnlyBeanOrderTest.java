package com.hit.ptest.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;

/** Khóa context phải độc lập với thứ tự khai báo: hai khai báo chỉ khác {@code qualified} cũng phải sắp xếp ổn định. */
class MockOnlyBeanOrderTest {

    @Test
    void orderIsTotalIncludingQualified() {
        MockOnlyBean byType = new MockOnlyBean("rate", false, ResolvableType.forClass(String.class));
        MockOnlyBean byName = new MockOnlyBean("rate", true, ResolvableType.forClass(String.class));

        assertThat(List.of(byType, byName).stream().sorted(MockOnlyBean.ORDER).toList())
                .isEqualTo(List.of(byName, byType).stream().sorted(MockOnlyBean.ORDER).toList())
                .containsExactly(byType, byName);
    }
}
