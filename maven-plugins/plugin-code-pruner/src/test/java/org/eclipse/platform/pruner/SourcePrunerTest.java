/*
 * Copyright (c) 2026 Eclipse contributors.
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.platform.pruner;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourcePrunerTest {
    @TempDir
    Path directory;

    private Path source(String relative, String contents) throws IOException {
        Path file = directory.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, contents);
        return file;
    }

    private SourcePruner.Plan plan(String... kept) throws IOException {
        return new SourcePruner(List.of(directory.resolve("src"), directory.resolve("vendor")),
                directory.resolve("vendor"), List.of(), StandardCharsets.UTF_8, "21", List.of(kept)).plan();
    }

    @Test
    void removesUnreachableCyclesAndMembersButKeepsTransitiveOverloadedCalls() throws Exception {
        Path root = source("src/app/Main.java", """
                package app;
                import lib.Used;
                public class Main { public int run() { return new Used().value(1); } }
                """);
        Path used = source("vendor/lib/Used.java", """
                package lib;
                public class Used {
                    private int retained, discarded;
                    private int unusedLiteral = 3;
                    public static final int UNUSED_CONSTANT = 7;
                    public int value(int n) { retained = n; return helper() + retained; }
                    public int value(String n) { return n.length(); }
                    private int helper() { return 3; }
                    private void cycleA() { cycleB(); }
                    private void cycleB() { cycleA(); }
                    public static class UnusedNested { }
                }
                """);
        Path dead = source("vendor/lib/Dead.java", """
                package lib;
                class Dead { Other other; }
                class Other { Dead dead; }
                """);
        String original = Files.readString(used);
        SourcePruner.Plan plan = plan();
        assertEquals(original, Files.readString(used), "Planning must not write source files");
        assertEquals(2, plan.changes().size());
        assertFalse(plan.changes().containsKey(root));
        plan.apply();
        String pruned = Files.readString(used);
        assertTrue(pruned.contains("value(int n)"));
        assertTrue(pruned.contains("helper()"));
        assertTrue(pruned.contains("retained"));
        assertFalse(pruned.contains("discarded"));
        assertFalse(pruned.contains("unusedLiteral"));
        assertFalse(pruned.contains("UNUSED_CONSTANT"));
        assertFalse(pruned.contains("value(String n)"));
        assertFalse(pruned.contains("cycleA"));
        assertFalse(pruned.contains("UnusedNested"));
        assertFalse(Files.exists(dead));
        assertTrue(plan().changes().isEmpty(), "Pruning must be idempotent");
    }

    @Test
    void retainsConstructorsInitializersAndVirtualDispatchContracts() throws Exception {
        source("src/app/Main.java", """
                package app;
                import lib.Child;
                public class Main { public Object run() { return new Child(); } }
                """);
        source("vendor/lib/Base.java", """
                package lib;
                public class Base { public int value() { return 1; } public void unused() {} }
                """);
        Path child = source("vendor/lib/Child.java", """
                package lib;
                public class Child extends Base implements Runnable {
                    private int initialized = sideEffect();
                    private static int staticValue = sideEffect();
                    { sideEffect(); }
                    static { sideEffect(); }
                    public Child() {}
                    public Child(int ignored) {}
                    private static int sideEffect() { return 4; }
                    @Override public int value() { return 2; }
                    @Override public void run() {}
                    @Override public String toString() { return "child"; }
                    public void unused() {}
                    private void dead() {}
                }
                """);
        plan().apply();
        String result = Files.readString(child);
        assertTrue(result.contains("initialized"));
        assertTrue(result.contains("staticValue"));
        assertTrue(result.contains("Child(int ignored)"));
        assertTrue(result.contains("sideEffect()"));
        assertTrue(result.contains("int value()"));
        assertTrue(result.contains("void run()"));
        assertTrue(result.contains("toString()"));
        assertTrue(Files.readString(directory.resolve("vendor/lib/Base.java")).contains("int value()"));
        assertFalse(result.contains("void dead()"));
    }

    @Test
    void preservesMethodReferencesGenericsAnonymousAndLocalClasses() throws Exception {
        source("src/app/Main.java", """
                package app;
                import lib.Factory;
                public class Main { public Object run() { return new Factory().create(); } }
                """);
        Path factory = source("vendor/lib/Factory.java", """
                package lib;
                import java.util.function.Supplier;
                public class Factory {
                    public Object create() {
                        Supplier<Box<String>> constructor = Box<String>::new;
                        Runnable callback = new Runnable() {
                            @Override public void run() { invoked(); }
                        };
                        class Local { void run() { invoked(); } }
                        new Local().run();
                        callback.run();
                        return constructor.get();
                    }
                    private void invoked() {}
                    private void unused() {}
                }
                class Box<T> { Box() {} }
                """);
        plan().apply();
        String result = Files.readString(factory);
        assertTrue(result.contains("class Box"));
        assertTrue(result.contains("void invoked()"));
        assertTrue(result.contains("class Local"));
        assertFalse(result.contains("void unused()"));
    }

    @Test
    void removesImportsOfDeletedDeclarationsAndKeepsLiveStaticOverloads() throws Exception {
        source("src/app/Main.java", """
                package app;
                import lib.Used;
                public class Main { public int run() { return new Used().call(); } }
                """);
        Path used = source("vendor/lib/Used.java", """
                package lib;
                import lib.Dead;
                import static lib.Helpers.value;
                public class Used {
                    public int call() { return value(1); }
                }
                """);
        source("vendor/lib/Dead.java", "package lib; public class Dead {}");
        Path helpers = source("vendor/lib/Helpers.java", """
                package lib;
                public class Helpers {
                    public static int value(String unused) { return 0; }
                    public static int value(int used) { return used; }
                }
                """);
        plan().apply();
        assertFalse(Files.readString(used).contains("import lib.Dead"));
        assertTrue(Files.readString(used).contains("import static lib.Helpers.value"));
        assertFalse(Files.readString(helpers).contains("value(String unused)"));
    }

    @Test
    void keepsExplicitTypesAndTheirMembersAndRejectsMisspelledKeepRules() throws Exception {
        source("src/app/Main.java", "package app; public class Main {}");
        Path kept = source("vendor/lib/Reflective.java", """
                package lib;
                public class Reflective {
                    private void callback() { new Dependency(); }
                    public static class Nested { private void callback() {} }
                }
                class Dependency {}
                """);
        assertThrows(IOException.class, () -> plan("lib.Misspelled"));
        assertTrue(plan("lib.Reflective").changes().isEmpty());
        assertTrue(Files.readString(kept).contains("callback"));
    }

    @Test
    void preservesEnumAnnotationAndRecordSemantics() throws Exception {
        source("src/app/Main.java", """
                package app;
                import lib.Kinds;
                import lib.Mark;
                import lib.Point;
                @Mark public class Main {
                    public Object run() { return new Object[] { Kinds.A, new Point(1).x() }; }
                }
                """);
        Path kinds = source("vendor/lib/Kinds.java", """
                package lib;
                public enum Kinds {
                    A, B;
                    public void unused() {}
                }
                """);
        source("vendor/lib/Mark.java", """
                package lib;
                public @interface Mark { String value() default "default"; }
                """);
        Path point = source("vendor/lib/Point.java", """
                package lib;
                public record Point(int x) {
                    public int unused() { return 0; }
                }
                """);
        plan().apply();
        assertTrue(Files.readString(kinds).contains("A, B"));
        assertFalse(Files.readString(kinds).contains("unused()"));
        assertTrue(Files.readString(point).contains("record Point(int x)"));
        assertFalse(Files.readString(point).contains("unused()"));
    }

    @Test
    void refusesInvalidSourcesWithoutWriting() throws Exception {
        source("src/app/Main.java", "package app; public class Main {}");
        Path broken = source("vendor/lib/Broken.java", """
                package lib;
                class Broken { Missing field; }
                """);
        String original = Files.readString(broken);
        IOException error = assertThrows(IOException.class, this::plan);
        assertTrue(error.getMessage().contains("Missing cannot be resolved"));
        assertEquals(original, Files.readString(broken));
    }

    @Test
    void refusesConcurrentSourceChangesBeforeApplyingAnyEdits() throws Exception {
        source("src/app/Main.java", "package app; public class Main {}");
        Path first = source("vendor/lib/First.java", "package lib; class First {}");
        Path second = source("vendor/lib/Second.java", "package lib; class Second {}");
        SourcePruner.Plan plan = plan();
        Files.writeString(second, "package lib; class Second { int added; }");
        assertThrows(IOException.class, plan::apply);
        assertTrue(Files.exists(first));
        assertTrue(Files.readString(second).contains("added"));
    }

    @Test
    void rejectsFoldersOutsideSourceRootsAndSymlinks() throws Exception {
        source("src/app/Main.java", "package app; public class Main {}");
        source("vendor/lib/Dead.java", "package lib; class Dead {}");
        Path outside = Files.createDirectory(directory.resolve("not-source"));
        assertThrows(IOException.class, () -> new SourcePruner(List.of(directory.resolve("src")),
                outside, List.of(), StandardCharsets.UTF_8, "21", List.of()));
        Files.createSymbolicLink(directory.resolve("vendor/link"), directory.resolve("src"));
        assertThrows(IOException.class, this::plan);
    }
}
