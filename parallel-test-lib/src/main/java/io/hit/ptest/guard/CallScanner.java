package io.hit.ptest.guard;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.Handle;
import org.springframework.asm.Label;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.SpringAsmInfo;
import org.springframework.asm.Type;

/**
 * Tìm lời gọi và method reference tới method bị cấm bằng cách đọc BYTECODE của lớp (và lớp lồng nhau, local, anonymous,
 * thân lambda) với bản ASM đóng gói trong spring-core: không thêm dependency. Receiver được khớp theo cây kiểu nên
 * {@code accountRepository.deleteAll()} khớp {@code CrudRepository#deleteAll()}.
 *
 * <p>GIỚI HẠN (heuristic): chỉ thấy lời gọi TRỰC TIẾP nằm trong bytecode của lớp được quét; không lần theo lời gọi qua
 * nhiều tầng, qua code ở {@code src/main} hay gọi bằng reflection. Receiver khai báo bằng kiểu không liên quan
 * (vd {@code Object}) không được khớp.
 */
final class CallScanner {

    private CallScanner() {
    }

    static List<Violation> scan(Class<?> root, List<ForbiddenCall> forbidden) {
        List<Violation> out = new ArrayList<>();
        for (Class<?> c : withNestedClasses(root)) {
            scanOne(c, forbidden, out);
        }
        return out;
    }

    /**
     * Lớp và mọi lớp member/local/anonymous khai báo bên trong nó (nest members của JVM), TRỪ lớp thành viên static là
     * một lớp test độc lập (có method test): lớp đó được quét riêng với miễn trừ (@Isolated, @SuppressGuard) của chính nó.
     */
    static Set<Class<?>> withNestedClasses(Class<?> root) {
        Set<Class<?>> all = new LinkedHashSet<>();
        all.add(root);
        List<String> independent = new ArrayList<>();
        String prefix = root.getName() + "$";
        List<Class<?>> members = new ArrayList<>();
        for (Class<?> member : root.getNestHost().getNestMembers()) {
            if (member.getName().startsWith(prefix)) {
                members.add(member);
                if (Modifier.isStatic(member.getModifiers()) && member.isMemberClass() && ClassRules.hasTests(member)) {
                    independent.add(member.getName() + "$");
                }
            }
        }
        for (Class<?> member : members) {
            String n = member.getName();
            boolean isIndependentRoot = independent.contains(n + "$");
            if (!isIndependentRoot && independent.stream().noneMatch(n::startsWith)) {
                all.add(member);
            }
        }
        return all;
    }

    private static void scanOne(Class<?> c, List<ForbiddenCall> forbidden, List<Violation> out) {
        String resource = c.getName().replace('.', '/') + ".class";
        ClassLoader loader = c.getClassLoader() != null ? c.getClassLoader() : ClassLoader.getSystemClassLoader();
        try (InputStream in = loader.getResourceAsStream(resource)) {
            if (in == null) {
                return;
            }
            new ClassReader(in).accept(new ClassVisitor(SpringAsmInfo.ASM_VERSION) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String sig, String[] ex) {
                    return new MethodVisitor(SpringAsmInfo.ASM_VERSION) {
                        int line = -1;

                        @Override
                        public void visitLineNumber(int lineNumber, Label start) {
                            line = lineNumber;
                        }

                        @Override
                        public void visitMethodInsn(int opcode, String owner, String mName, String desc, boolean itf) {
                            report(name, line, owner, mName, desc, "calls");
                        }

                        /** Method reference (repository::deleteAll) biên dịch thành invokedynamic + method handle. */
                        @Override
                        public void visitInvokeDynamicInsn(String n, String desc, Handle bsm, Object... bsmArgs) {
                            for (Object arg : bsmArgs) {
                                if (arg instanceof Handle h) {
                                    report(name, line, h.getOwner(), h.getName(), h.getDesc(), "references");
                                }
                            }
                        }
                    };
                }

                private void report(String callerMethod, int line, String owner, String mName, String desc, String verb) {
                    for (ForbiddenCall f : forbidden) {
                        if (matches(f, owner, mName, desc, loader)) {
                            out.add(new Violation(GuardRule.PT07,
                                    c.getName().replace('$', '.') + "." + displayMethod(callerMethod) + "(" + simpleFile(c) + ":" + line + ")",
                                    verb + " " + Type.getObjectType(owner).getClassName() + "." + mName + " (forbidden: " + f + ")"));
                        }
                    }
                }
            }, 0);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read bytecode of " + c.getName(), e);
        }
    }

    private static String displayMethod(String m) {
        if (m.startsWith("lambda$")) {
            String[] parts = m.split("\\$");
            return parts.length > 1 ? parts[1] + "<lambda>" : m;
        }
        return m;
    }

    private static String simpleFile(Class<?> c) {
        Class<?> top = c;
        while (top.getEnclosingClass() != null) {
            top = top.getEnclosingClass();
        }
        return top.getSimpleName() + ".java";
    }

    static boolean matches(ForbiddenCall f, String owner, String name, String desc, ClassLoader loader) {
        if (!f.method().equals(name)) {
            return false;
        }
        if (!f.anyParams()) {
            Type[] args = Type.getArgumentTypes(desc);
            if (args.length != f.params().size()) {
                return false;
            }
            for (int i = 0; i < args.length; i++) {
                if (!args[i].getClassName().equals(f.params().get(i))) {
                    return false;
                }
            }
        }
        String ownerName = Type.getObjectType(owner).getClassName();
        if (ownerName.equals(f.type())) {
            return true;
        }
        try {
            Class<?> spec = Class.forName(f.type(), false, loader);
            Class<?> receiver = Class.forName(ownerName, false, loader);
            return spec.isAssignableFrom(receiver);
        } catch (ClassNotFoundException | LinkageError e) {
            return false; // vd Spring Data không có trên classpath
        }
    }
}
