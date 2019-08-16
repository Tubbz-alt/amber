/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import java.io.IOException;
import java.util.List;

import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import tools.javac.combo.JavacTemplateTestBase;

import static java.util.stream.Collectors.toList;

/**
 * RecordCompilationTests
 *
 * @test
 * @summary Negative compilation tests, and positive compilation (smoke) tests for records
 * @library /lib/combo
 * @modules jdk.compiler/com.sun.tools.javac.util
 * @run testng RecordCompilationTests
 */
@Test
public class RecordCompilationTests extends JavacTemplateTestBase {

    private static final List<String> BAD_COMPONENT_NAMES
            = List.of("hashCode", "toString", "getClass",
            "readObjectNoData", "readResolve", "writeReplace", "serialPersistentFields");

    // @@@ When records become a permanent feature, we don't need these any more
    private static String[] PREVIEW_OPTIONS = {"--enable-preview", "-source",
            Integer.toString(Runtime.version().feature())};

    @AfterMethod
    public void dumpTemplateIfError(ITestResult result) {
        // Make sure offending template ends up in log file on failure
        if (!result.isSuccess()) {
            System.err.printf("Diagnostics: %s%nTemplate: %s%n", diags.errorKeys(),
                    sourceFiles.stream().map(p -> p.snd).collect(toList()));
        }
    }

    private String expand(String... constructs) {
        String s = "#";
        for (String c : constructs)
            s = s.replace("#", c);
        return s;
    }

    private void assertCompile(String program, Runnable postTest) {
        reset();
        addCompileOptions(PREVIEW_OPTIONS);
        addSourceFile("R.java", new StringTemplate(program));
        try {
            compile();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        postTest.run();
    }

    private void assertOK(String... constructs) {
        assertCompile(expand(constructs), this::assertCompileSucceeded);
    }

    private void assertOKWithWarning(String warning, String... constructs) {
        assertCompile(expand(constructs), () -> assertCompileSucceededWithWarning(warning));
    }

    private void assertFail(String expectedDiag, String... constructs) {
        assertCompile(expand(constructs), () -> assertCompileFailed(expectedDiag));
    }

    // -- Actual test cases start here --

    public void testMalformedDeclarations() {
        assertFail("compiler.err.premature.eof", "record R()");
        assertFail("compiler.err.premature.eof", "record R();");
        assertFail("compiler.err.illegal.start.of.type", "record R(,) { }");
        assertFail("compiler.err.illegal.start.of.type", "record R((int x)) { }");
        assertFail("compiler.err.expected", "record R(foo) { }");
        assertFail("compiler.err.expected", "record R(int int) { }");
        assertFail("compiler.err.restricted.type.not.allowed.here", "record R(var x) { }");
        assertFail("compiler.err.restricted.type.not.allowed.here", "record R(record x) { }");
        assertFail("compiler.err.record.cant.declare.field.modifiers", "record R(public String foo) { }");
        assertFail("compiler.err.record.cant.declare.field.modifiers", "record R(private String foo) { }");
        assertFail("compiler.err.mod.not.allowed.here", "abstract record R(String foo) { }");
        assertFail("compiler.err.illegal.combination.of.modifiers", "non-sealed record R(String foo) { }");
        assertFail("compiler.err.repeated.modifier", "public public record R(String foo) { }");
        assertFail("compiler.err.repeated.modifier", "private private record R(String foo) { }");
        assertFail("compiler.err.record.cant.declare.duplicate.fields", "record R(int x, int x) {}");
    }

    public void testGoodDeclarations() {
        assertOK("public record R() { }");
        assertOK("record R() { }");
        assertOK("record R() implements java.io.Serializable, Runnable { public void run() { } }");
        assertOK("record R(int x) { }");
        assertOK("record R(int x, int y) { }");
        assertOK("@Deprecated record R(int x, int y) { }");
        assertOK("record R(@Deprecated int x, int y) { }");
        assertOK("record R<T>(T x, T y) { }");
    }

    public void testBadComponentNames() {
        // @@@ Duplicates IllegalRecordComponentNameTest
        for (String s : BAD_COMPONENT_NAMES)
            assertFail("compiler.err.illegal.record.component.name", "record R(int #) { } ", s);
    }

    public void testRestrictedIdentifiers() {
        for (String s : List.of("interface record { void m(); }",
                "@interface record { }",
                "class record { }",
                "record record(int x) { }",
                "enum record { A, B }",
                "class R<record> { }")) {
            assertFail("compiler.err.restricted.type.not.allowed", s);
        }
    }

    public void testValidMembers() {
        for (String s : List.of("record X(int j) { }",
                "interface I { }",
                "static { }",
                "{}",
                "enum E { A, B }",
                "class C { }"
        )) {
            assertOK("record R(int i) { # }", s);
        }
    }

    public void testCyclic() {
        // Cyclic records are OK, but cyclic inline records would not be
        assertOK("record R(R r) { }");
    }

    public void testBadExtends() {
        assertFail("compiler.err.expected", "record R(int x) extends Object { }");
        assertFail("compiler.err.expected", "record R(int x) {}\n"
                + "record R2(int x) extends R { }");
        assertFail("compiler.err.cant.inherit.from.final", "record R(int x) {}\n"
                + "class C extends R { }");
    }

    public void testNoExtendRecord() {
        assertFail("compiler.err.invalid.supertype.record",
                   "class R extends Record { public String toString() { return null; } public int hashCode() { return 0; } public boolean equals(Object o) { return false; } } }");
    }

    public void testFieldDeclarations() {
        // static fields are OK
        assertOK("public record R(int x) {\n" +
                "    static int I = 1;\n" +
                "    static final String S = \"Hello World!\";\n" +
                "    static private Object O = null;\n" +
                "    static protected Object O2 = null;\n" +
                "}");

        // instance fields are not
        assertFail("compiler.err.record.fields.must.be.in.header",
                "public record R(int x) {\n" +
                        "    private final int y = 0;" +
                        "}");

        // mutable instance fields definitely not
        assertFail("compiler.err.record.fields.must.be.in.header",
                "public record R(int x) {\n" +
                        "    private int y = 0;" +
                        "}");

        // redeclaring components also not
        assertFail("compiler.err.record.fields.must.be.in.header",
                "public record R(int x) {\n" +
                        "    private final int x;" +
                        "}");
    }

    public void testAccessorRedeclaration() {
        assertOK("public record R(int x) {\n" +
                "    public int x() { return x; };" +
                "}");

        assertOK("public record R(int x) {\n" +
                "    public final int x() { return 0; };" +
                "}");

        assertFail("compiler.err.method.must.be.public",
                "public record R(int x) {\n" +
                        "    final int x() { return 0; };" +
                        "}");

        assertFail("compiler.err.method.must.be.public",
                "public record R(int x) {\n" +
                        "    int x() { return 0; };" +
                        "}");

        // @@@ Error: should fail, but doesn't
//        assertFail("something",
//                   "public record R(int x) {\n" +
//                   "    public int x() throws Exception { return 0; };" +
//                   "}");

        for (String s : List.of("List", "List<?>", "Object", "ArrayList<String>", "int"))
            assertFail("compiler.err.accessor.return.type.doesnt.match",
                    "import java.util.*;\n" +
                            "public record R(List<String> x) {\n" +
                            "    public # x() { return null; };" +
                            "}", s);
    }

    public void testConstructorRedeclaration() {
        for (String goodCtor : List.of("public R(int x) { this(x, 0); }",
                "public R(int x, int y) { this.x = x; this.y = y; }",
                "public R { }",
                "public R { x = 0; }"))
            assertOK("record R(int x, int y) { # }", goodCtor);

        // Not OK to redeclare canonical without DA
        // @@@ Should fail
//        assertFail("", "record R(int x, int y) { # }",
//                   "public R(int x, int y) { this.x = x; }");

        // canonical ctor must be public
        assertFail("compiler.err.canonical.constructor.must.be.public", "record R(int x, int y) { # }",
                   "R(int x, int y) { this.x = x; this.y = y; }");

        // ctor args must match types
        assertFail("compiler.err.constructor.with.same.erasure.as.canonical",
                "import java.util.*;\n" +
                        "record R(List<String> list) { # }",
                "R(List list) { this.list = list; }");
    }

    public void testAnnotationCriteria() {
        // OK to anno with FIELD, METHOD, PARAM, TYPE_USE, and COMPONENT, no @Target, or group thereof
        // Not OK to anno when @Target specified and none of those are available
        // OK to redeclare with or without same annos
    }

    public void testIllegalSerializationMembers() {
        // readResolve, writeReplace, readObject, writeObject, readObjectNoData, serialPersistentFields
    }

    public void testLocalRecords() {
        assertOK("class R { \n" +
                "    void m() { \n" +
                "        record RR(int x) { };\n" +
                "    }\n" +
                "}");

        // Capture locals from local record
        assertOK("class R { \n" +
                "    void m(int y) { \n" +
                "        record RR(int x) { public int x() { return y; }};\n" +
                "    }\n" +
                "}");
    }
}
