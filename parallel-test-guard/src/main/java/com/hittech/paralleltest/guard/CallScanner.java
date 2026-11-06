package com.hittech.paralleltest.guard;

import java.io.IOException;
import java.io.InputStream;
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
 * Finds calls and method references to forbidden methods by reading the BYTECODE of a class (and its
 * member, local, anonymous classes and lambda bodies) with the ASM copy shipped in spring-core: no extra dependency.
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

    /** The class plus every member, local and anonymous class declared inside it (JVM nest members). */
    static Set<Class<?>> withNestedClasses(Class<?> root) {
        Set<Class<?>> all = new LinkedHashSet<>();
        all.add(root);
        String prefix = root.getName() + "$";
        for (Class<?> member : root.getNestHost().getNestMembers()) {
            if (member.getName().startsWith(prefix)) {
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
                    String callerMethod = name;
                    return new MethodVisitor(SpringAsmInfo.ASM_VERSION) {
                        int line = -1;

                        @Override
                        public void visitLineNumber(int lineNumber, Label start) {
                            line = lineNumber;
                        }

                        @Override
                        public void visitMethodInsn(int opcode, String owner, String mName, String desc, boolean itf) {
                            report(owner, mName, desc, "calls");
                        }

                        /** Method references (repository::deleteAll) compile to invokedynamic + a method handle. */
                        @Override
                        public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
                            for (Object arg : bsmArgs) {
                                if (arg instanceof Handle h) {
                                    report(h.getOwner(), h.getName(), h.getDesc(), "references");
                                }
                            }
                        }

                        private void report(String owner, String mName, String desc, String verb) {
                            for (ForbiddenCall f : forbidden) {
                                if (matches(f, owner, mName, desc, loader)) {
                                    String source = c.getName().replace('$', '.');
                                    out.add(new Violation(Rule.PT07,
                                            source + "." + displayMethod(callerMethod) + "(" + simpleFile(c) + ":" + line + ")",
                                            verb + " " + Type.getObjectType(owner).getClassName() + "." + mName
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
