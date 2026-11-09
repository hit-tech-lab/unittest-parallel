package com.hit.ptest.context;

import com.hit.ptest.intercept.InterceptMode;
import com.hit.ptest.ParallelTestException;
import com.hit.ptest.PerTestMock;
import com.hit.ptest.PerTestSpy;

import java.beans.Introspector;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;

/**
 * Đọc khai báo {@code @PerTestMock}/{@code @PerTestSpy} của lớp test: field và tham số (handle theo từng test), khai báo CẤP CLASS
 * (mock thuộc khóa context, spy theo từng test).
 */
public final class BeanDeclarations {

    private BeanDeclarations() {
    }

    /** Lớp test và các lớp bao ngoài của nó (chỉ {@code @Nested} = lớp lồng nhau không static), từ trong ra ngoài. */
    public static List<Class<?>> classAndEnclosing(Class<?> testClass) {
        List<Class<?>> out = new ArrayList<>();
        for (Class<?> c = testClass; c != null; c = isInner(c) ? c.getEnclosingClass() : null) {
            out.add(c);
        }
        return out;
    }

    private static boolean isInner(Class<?> c) {
        return c.isMemberClass() && !Modifier.isStatic(c.getModifiers());
    }

    /**
     * Mỗi yêu cầu ở field của phân cấp lớp test (lớp bao ngoài &lt; lớp cha &lt; lớp con) cộng các khai báo SPY cấp class
     * (mỗi test có sẵn một spy), đã kiểm tra cú pháp khai báo.
     */
    public static List<BeanRequest> fieldRequests(Class<?> testClass) {
        List<BeanRequest> out = new ArrayList<>();
        for (Class<?> c : hierarchy(testClass)) {
            out.addAll(requestsDeclaredIn(c));
        }
        out.addAll(classLevelSpies(testClass));
        return out;
    }

    /** Một khai báo cấp class đã đọc: {@code name} null nếu chọn theo kiểu. */
    private record ClassDecl(String name, Class<?> type, InterceptMode mode, String where) {
        String key() {
            return name != null ? "name:" + name : "type:" + type.getName();
        }

        String describe() {
            return "@" + (mode == InterceptMode.MOCK ? "PerTestMock" : "PerTestSpy") + "(" + (name == null ? "" : "name = \"" + name + "\", classes = ")
                    + type.getSimpleName() + ".class) on " + where;
        }
    }

    /**
     * Khai báo {@code @PerTestMock} CẤP CLASS của khóa context, từ MỌI lớp trong phân cấp của lớp test (lớp bao ngoài, lớp cha,
     * chính nó), giống {@code @MockBean} đặt ở lớp base. Khai báo trùng hệt nhau (vd lặp ở lớp cha và lớp con) gộp một.
     * Lỗi: cùng tên khác kiểu là {@code MOCK_ONLY_CONFLICT} (kể cả khi hai khai báo khác chế độ); cùng bean cùng kiểu vừa mock vừa spy là {@code SPY_NO_BEAN}
     * (mock cấp class thay bean cho cả context nên không còn bản thật để spy).
     */
    public static List<MockOnlyBean> classLevelMocks(Class<?> testClass) {
        List<MockOnlyBean> out = new ArrayList<>();
        for (ClassDecl d : classLevel(testClass)) {
            if (d.mode() == InterceptMode.MOCK) {
                MockOnlyBean bean = new MockOnlyBean(d.name() != null ? d.name() : defaultName(d.type()), d.name() != null, ResolvableType.forClass(d.type()));
                if (!out.contains(bean)) {
                    out.add(bean);
                }
            }
        }
        return out;
    }

    /** Khai báo {@code @PerTestSpy} cấp class, dưới dạng yêu cầu spy theo từng test (không có field để tiêm). */
    static List<BeanRequest> classLevelSpies(Class<?> testClass) {
        List<BeanRequest> out = new ArrayList<>();
        for (ClassDecl d : classLevel(testClass)) {
            if (d.mode() == InterceptMode.SPY) {
                out.add(new BeanRequest(null, d.where(), d.name() != null ? d.name() : defaultName(d.type()), ResolvableType.forClass(d.type()),
                        d.name(), InterceptMode.SPY));
            }
        }
        return out;
    }

    private static List<ClassDecl> classLevel(Class<?> testClass) {
        Map<String, ClassDecl> byKey = new LinkedHashMap<>();
        for (Class<?> c : hierarchy(testClass)) {
            MergedAnnotations annotations = MergedAnnotations.from(c, MergedAnnotations.SearchStrategy.DIRECT);
            String where = c.getSimpleName();
            annotations.stream(PerTestMock.class).forEach(a -> add(byKey, read(a, InterceptMode.MOCK, where)));
            annotations.stream(PerTestSpy.class).forEach(a -> add(byKey, read(a, InterceptMode.SPY, where)));
        }
        List<ClassDecl> out = new ArrayList<>(byKey.values());
        // cùng kiểu và cùng cách chọn (cùng tên hoặc cùng theo kiểu) không được khác chế độ
        for (ClassDecl a : out) {
            for (ClassDecl b : out) {
                if (a.mode() != b.mode() && a.type().equals(b.type()) && java.util.Objects.equals(a.name(), b.name())) {
                    throw mockAndSpy(a, b);
                }
            }
        }
        return out;
    }

    private static void add(Map<String, ClassDecl> byKey, List<ClassDecl> decls) {
        for (ClassDecl d : decls) {
            ClassDecl previous = byKey.putIfAbsent(d.key(), d);
            if (previous == null || (previous.type().equals(d.type()) && previous.mode() == d.mode())) {
                continue;
            }
            if (!previous.type().equals(d.type())) {
                // cùng khóa (cùng name hoặc cùng kiểu) mà khác kiểu: nguyên nhân là hai kiểu cho một tên, dù chế độ có khác nhau hay không
                throw new ParallelTestException.Config("MOCK_ONLY_CONFLICT: bean '" + d.name() + "' is declared with two types: " + previous.describe()
                        + " and " + d.describe() + ". Use one type or different name values.");
            }
            throw mockAndSpy(previous, d);
        }
    }

    private static ParallelTestException.Config mockAndSpy(ClassDecl a, ClassDecl b) {
        ClassDecl mock = a.mode() == InterceptMode.MOCK ? a : b;
        ClassDecl spy = mock == a ? b : a;
        return new ParallelTestException.Config("SPY_NO_BEAN: " + mock.describe() + " replaces the bean for the whole context, so there is no real bean "
                + "for " + spy.describe() + " to spy on. Keep only one of them; to mock in some classes and spy in others, declare neither at class "
                + "level and use field @PerTestMock/@PerTestSpy.");
    }

    private static List<ClassDecl> read(MergedAnnotation<?> a, InterceptMode mode, String where) {
        String annotation = mode == InterceptMode.MOCK ? "@PerTestMock" : "@PerTestSpy";
        Set<Class<?>> types = new LinkedHashSet<>();
        types.addAll(Arrays.asList(a.getClassArray("value")));
        types.addAll(Arrays.asList(a.getClassArray("classes")));
        String name = a.getString("name").isBlank() ? null : a.getString("name");
        if (types.isEmpty()) {
            throw new ParallelTestException.Config("INVALID_DECLARATION: " + annotation + " on class " + where + " names no type. Class-level "
                    + "declarations need the bean type: " + annotation + "(MyBean.class). To get a per-test handle use a field.");
        }
        if (name != null && types.size() > 1) {
            throw new ParallelTestException.Config("INVALID_DECLARATION: " + annotation + "(name = \"" + name + "\") on class " + where
                    + " lists " + types.size() + " types; a name belongs to exactly one bean type. Declare one annotation per named bean.");
        }
        return types.stream().map(t -> new ClassDecl(name, t, mode, where)).toList();
    }

    /** Tên bean được tạo khi khai báo cấp class chọn theo kiểu và context chưa có: tên lớp viết thường đầu. */
    private static String defaultName(Class<?> type) {
        return Introspector.decapitalize(type.getSimpleName());
    }

    /** Lớp bao ngoài đến lớp con, mỗi lớp từ lớp cha xuống chính nó. */
    private static List<Class<?>> hierarchy(Class<?> testClass) {
        List<Class<?>> out = new ArrayList<>();
        List<Class<?>> levels = classAndEnclosing(testClass);
        for (int i = levels.size() - 1; i >= 0; i--) {
            out.addAll(superclassFirst(levels.get(i)));
        }
        return out;
    }

    /** Yêu cầu trên tham số của method test, hoặc null nếu tham số không gắn annotation. */
    public static BeanRequest parameterRequest(Method method, Parameter parameter, int index) {
        PerTestMock mock = parameter.getAnnotation(PerTestMock.class);
        PerTestSpy spy = parameter.getAnnotation(PerTestSpy.class);
        if (mock == null && spy == null) {
            return null;
        }
        String where = method.getDeclaringClass().getSimpleName() + "." + method.getName() + "(parameter " + index + ")";
        if (mock != null && spy != null) {
            throw conflictOnSameElement(where);
        }
        Qualifier q = AnnotatedElementUtils.findMergedAnnotation(parameter, Qualifier.class);
        MethodParameter mp = new MethodParameter(method, index);
        String member = parameter.isNamePresent() ? parameter.getName() : "arg" + index;
        requireNoTypes(where, mock != null ? mock.value() : spy.value(), mock != null ? mock.classes() : spy.classes(), mock != null);
        String explicit = mock != null ? mock.name() : spy.name();
        return new BeanRequest(null, where, member, ResolvableType.forMethodParameter(mp), qualifierValue(explicit, q),
                mock != null ? InterceptMode.MOCK : InterceptMode.SPY);
    }

    /** {@code value}/{@code classes} chỉ có nghĩa ở cấp class; ở field/tham số kiểu đã là kiểu của field/tham số. */
    private static void requireNoTypes(String where, Class<?>[] value, Class<?>[] classes, boolean mock) {
        if (value.length > 0 || classes.length > 0) {
            throw new ParallelTestException.Config("INVALID_DECLARATION: " + where + " sets value/classes on @" + (mock ? "PerTestMock" : "PerTestSpy")
                    + ", which only apply to class-level declarations. A field/parameter takes the bean type from its own type; use name = \"...\" "
                    + "to pick one of several beans of that type.");
        }
    }

    private static List<Class<?>> superclassFirst(Class<?> c) {
        ArrayDeque<Class<?>> chain = new ArrayDeque<>();
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            chain.push(k);
        }
        return new ArrayList<>(chain);
    }

    private static List<BeanRequest> requestsDeclaredIn(Class<?> c) {
        List<BeanRequest> out = new ArrayList<>();
        for (Field f : c.getDeclaredFields()) {
            boolean mock = f.isAnnotationPresent(PerTestMock.class);
            boolean spy = f.isAnnotationPresent(PerTestSpy.class);
            if (!mock && !spy) {
                continue;
            }
            String where = c.getSimpleName() + "." + f.getName();
            if (mock && spy) {
                throw conflictOnSameElement(where);
            }
            if (Modifier.isStatic(f.getModifiers()) || Modifier.isFinal(f.getModifiers())) {
                throw new ParallelTestException.Config("INVALID_DECLARATION: " + where + " must be a non-static, non-final instance field "
                        + "(each test gets its own mock/spy)");
            }
            Qualifier q = AnnotatedElementUtils.findMergedAnnotation(f, Qualifier.class);
            PerTestMock m = f.getAnnotation(PerTestMock.class);
            PerTestSpy sp = f.getAnnotation(PerTestSpy.class);
            requireNoTypes(where, mock ? m.value() : sp.value(), mock ? m.classes() : sp.classes(), mock);
            String explicit = mock ? m.name() : sp.name();
            out.add(new BeanRequest(f, where, f.getName(), ResolvableType.forField(f), qualifierValue(explicit, q),
                    mock ? InterceptMode.MOCK : InterceptMode.SPY));
        }
        return out;
    }

    /** {@code name()} của annotation thắng {@code @Qualifier}; rỗng thì không có qualifier. */
    private static String qualifierValue(String explicit, Qualifier q) {
        if (!explicit.isBlank()) {
            return explicit;
        }
        return q == null || q.value().isBlank() ? null : q.value();
    }

    private static ParallelTestException.Config conflictOnSameElement(String where) {
        return new ParallelTestException.Config("MODE_CONFLICT: " + where + " is annotated with both @PerTestMock and @PerTestSpy");
    }
}
