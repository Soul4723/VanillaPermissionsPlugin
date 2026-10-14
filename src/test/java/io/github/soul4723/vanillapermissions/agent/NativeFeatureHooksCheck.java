package io.github.soul4723.vanillapermissions.agent;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

public final class NativeFeatureHooksCheck {
    private NativeFeatureHooksCheck() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Pass Paper 1.20.6 server jar and libraries directory");

        int checked = 0;
        Path paperPath = Path.of(args[0]);
        ArrayList<URL> classPath = new ArrayList<>();
        classPath.add(paperPath.toUri().toURL());
        try (var files = Files.walk(Path.of(args[1]))) {
            files.filter(path -> path.toString().endsWith(".jar"))
                .map(NativeFeatureHooksCheck::toUrl)
                .forEach(classPath::add);
        }

        try (JarFile paper = new JarFile(paperPath.toFile());
             URLClassLoader dependencies = new URLClassLoader(classPath.toArray(URL[]::new),
                 NativeFeatureHooksCheck.class.getClassLoader())) {
            for (NmsFeatureHook hook : NativeFeatureHooks.create()) {
                String entryName = hook.targetClass() + ".class";
                byte[] original;
                var entry = paper.getJarEntry(entryName);
                if (entry == null) throw new AssertionError("Missing Paper class " + entryName);
                try (var input = paper.getInputStream(entry)) {
                    original = input.readAllBytes();
                }

                byte[] transformed = hook.transform(original);
                if (transformed == null || java.util.Arrays.equals(original, transformed)) {
                    throw new AssertionError("Hook made no change: " + hook.id());
                }
                new ClassReader(transformed).accept(new ClassVisitor(Opcodes.ASM9) {}, ClassReader.EXPAND_FRAMES);
                verifyClass(hook.targetClass().replace('/', '.'), transformed, dependencies);
                checked++;
            }
        }
        if (checked != 11) throw new AssertionError("Expected 11 native hook targets, checked " + checked);
        System.out.println("Transformed and JVM-verified " + checked + " Paper 1.20.6 native hook targets.");
    }

    private static URL toUrl(Path path) {
        try {
            return path.toUri().toURL();
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
    }

    private static void verifyClass(String name, byte[] bytes, ClassLoader dependencies) throws Exception {
        ClassLoader transformedLoader = new ClassLoader(dependencies) {
            @Override
            protected Class<?> loadClass(String requested, boolean resolve) throws ClassNotFoundException {
                if (!requested.equals(name)) return super.loadClass(requested, resolve);
                synchronized (getClassLoadingLock(requested)) {
                    Class<?> loaded = findLoadedClass(requested);
                    if (loaded == null) loaded = defineClass(requested, bytes, 0, bytes.length);
                    if (resolve) resolveClass(loaded);
                    return loaded;
                }
            }
        };
        Class<?> verified = Class.forName(name, false, transformedLoader);
        verified.getDeclaredMethods();
    }
}
