package com.hittech.paralleltest.guard;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.Label;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.SpringAsmInfo;
import org.springframework.asm.Type;

/**
 * Finds calls to forbidden methods by reading the BYTECODE of a class (and its nested, anonymous and
 * lambda bodies) with the ASM copy shipped in spring-core: no extra dependency.
 * The receiver is matched by type hierarchy, so {@code accountRepository.deleteAll()} matches
 * {@code CrudRepository#deleteAll()}.
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

    static Set<Class<?>> withNestedClasses(Class<?> root) {
        Set<Class<?>> all = new LinkedHashSet<>();
        collect(root, all);
        return all;
    }

    private static void collect(Class<?> c, Set<Class<?>> all) {
        if (!all.add(c)) {
            return;
        }
        for (Class<?> nested : c.getDeclaredClasses()) {
            collect(nested, all);
        }
        for (int i = 1; i < 500; i++) { // anonymous classes are not returned by getDeclaredClasses()
            try {
                collect(Class.forName(c.getName() + "$" + i, false, c.getClassLoader()), all);
            } catch (ClassNotFoundException | LinkageError e) {
                break;
            }
        }
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
                    String callerMethod = name;
                    return new MethodVisitor(SpringAsmInfo.ASM_VERSION) {
                        int line = -1;

                        @Override
                        public void visitLineNumber(int lineNumber, Label start) {
                            line = lineNumber;
                        }

                        @Override
                        public void visitMethodInsn(int opcode, String owner, String mName, String desc, boolean itf) {
                            for (ForbiddenCall f : forbidden) {
                                if (matches(f, owner, mName, desc, loader)) {
                                    String source = c.getName().replace('$', '.');
                                    out.add(new Violation(Rule.PT07,
                                            source + "." + displayMethod(callerMethod) + "(" + simpleFile(c) + ":" + line + ")",
                                            "calls " + Type.getObjectType(owner).getClassName() + "." + mName
                                                    + " (forbidden: " + f + ")"));
                                }
                            }
                        }
                    };
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
            return false; // e.g. Spring Data not on the classpath
        }
    }
}
