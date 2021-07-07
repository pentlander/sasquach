package com.pentlander.sasquach;

import com.pentlander.sasquach.ast.*;
import com.pentlander.sasquach.type.*;
import java.io.PrintStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static com.pentlander.sasquach.Fixtures.*;
import static com.pentlander.sasquach.ast.BinaryExpression.*;
import static org.assertj.core.api.Assertions.assertThat;

class BytecodeGeneratorTest {
    private static final Range.Single NR = new Range.Single(new Position(1, 1), 1);

    private SasquachClassloader cl;
    private Scope scope;
    private TypeResolver typeResolver;

    @BeforeEach
    void setUp() {
        cl = new SasquachClassloader();
        scope = Scope.topLevel(new Metadata(MOD_NAME));
        typeResolver = new TypeResolver();
    }

    @ParameterizedTest
    @CsvSource({"true, true", "false, false"})
    void booleanValue(String boolStr, boolean actualResult) throws Exception {
        var func = func(scope, "bool", List.of(), BuiltinType.BOOLEAN, boolValue(boolStr));

        var clazz = genClass(compUnit(List.of(), List.of(), List.of(func)));
        boolean result = invokeFirst(clazz, null);

        assertThat(result).isEqualTo(actualResult);
    }

    @Nested
    class VariableDeclarations {
        @Test
        void booleanDecl() throws Exception {
            boolean result = declResult(BuiltinType.BOOLEAN, "true");
            assertThat(result).isTrue();
        }

        @Test
        void intDecl() throws Exception {
            int result = declResult(BuiltinType.INT, "10");
            assertThat(result).isEqualTo(10);
        }

        @Test
        void longDecl() throws Exception {
            long result = declResult(BuiltinType.LONG, "10");
            assertThat(result).isEqualTo(10L);
        }

        @Test
        void floatDecl() throws Exception {
            float result = declResult(BuiltinType.FLOAT, "3.14");
            assertThat(result).isEqualTo(3.14F);
        }

        @Test
        void doubleDecl() throws Exception {
            double result = declResult(BuiltinType.DOUBLE, "3.14");
            assertThat(result).isEqualTo(3.14D);
        }

        @Test
        void stringDecl() throws Exception {
            String result = declResult(BuiltinType.STRING, "hello");
            assertThat(result).isEqualTo("hello");
        }

        @Test
        void voidDecl() throws Exception {
            Object result = declResult(BuiltinType.VOID, "hello");
            assertThat(result).isNull();
        }

        private <T> T declResult(Type type, String value) throws Exception {
            return declResult(type, new Value(type, value, NR));
        }

        private <T> T declResult(Type type, Expression expr) throws Exception {
            var varDecl = new VariableDeclaration(id("bar"), expr, 0, NR);
            scope.addLocalIdentifier(varDecl.id());
            var block = new Block(scope,
                    List.of(varDecl, VarReference.of("bar", NR)), NR);
            var func = func(scope, "foo", List.of(), type, block);

            var clazz = genClass(compUnit(List.of(), List.of(), List.of(func)));
            return invokeFirst(clazz, null);
        }
    }

    @Nested
    class ForeignFunctionCalls {
        @Test
        void constructor() throws Exception {
            var className = "java/lang/StringBuilder";
            var alias = "StringBuilder";
            scope.addUse(new Use.Foreign(qualId(className), id(alias), NR));
            var type = new ClassType(className);
            var call = new ForeignFunctionCall(id(alias), id("new"), List.of(stringValue("hi")),
                    NR);
            var func = func(scope, "baz", List.of(), type, call);

            var clazz = genClass(compUnit(List.of(), List.of(), List.of(func)));
            StringBuilder result = invokeFirst(clazz, null);

            assertThat(result.toString()).isEqualTo("hi");
        }

        @Test
        void staticFunc() throws Exception {
            scope.addUse(new Use.Foreign(qualId("java/nio/file/Paths"), id("Paths"), NR));
            var type = new ClassType( "java.nio.file.Path");
            List<Expression> args = List.of(stringValue("hi.txt"),
                    ArrayValue.ofElementType(BuiltinType.STRING, List.of(), NR));
            var call = new ForeignFunctionCall(id("Paths"), id("get"), args,
                    NR);
            var func = func(scope, "baz", List.of(), type, call);

            var clazz = genClass(compUnit(List.of(), List.of(), List.of(func)), true);
            Path result = invokeFirst(clazz, null);

            assertThat(result).isEqualTo(Paths.get("hi.txt"));
        }

        @Test
        void virtualFunc() throws Exception {
            scope.addUse(new Use.Foreign(qualId("java/lang/String"), id("String"), NR));
            List<Expression> args = List.of(stringValue("he"), stringValue("llo"));
            var call = new ForeignFunctionCall(id("String"), id("concat"), args,
                    NR);
            var func = func(scope, "baz", List.of(), BuiltinType.STRING, call);

            var clazz = genClass(compUnit(List.of(), List.of(), List.of(func)));
            String result = invokeFirst(clazz, null);

            assertThat(result).isEqualTo("hello");
        }
    }

    @Nested
    class ForeignFieldAccessTest {
        @Test
        void staticField() throws Exception {
            scope.addUse(new Use.Foreign(qualId("java/lang/System"), id("System"), NR));
            var fieldAccess = new ForeignFieldAccess(id("System"), id("out"));
            var func = func(scope, "foo", List.of(), new ClassType(PrintStream.class), fieldAccess);

            var clazz = genClass(compUnit(func));
            PrintStream ps = invokeFirst(clazz, null);

            assertThat(ps).isEqualTo(System.out);
        }
    }

    @Test
    void functionCall() throws Exception {
        var calleeFunc = func(scope, "foo", List.of(), BuiltinType.INT, intValue("5"));
        var callerFunc = func(scope, "baz", List.of(), BuiltinType.INT, new LocalFunctionCall(id("foo"),
            List.of(), NR));

        var clazz = genClass(compUnit(List.of(), List.of(), List.of(callerFunc, calleeFunc)));
        int result = invokeFirst(clazz, null);

        assertThat(result).isEqualTo(5);
    }

    @Test
    void structLiteralFields() throws Exception {
        var id = id("f1");
        var boolField = new Struct.Field(id, boolValue("true"));
        var struct = Struct.literalStruct(scope, List.of(boolField), List.of(), NR);
        var func = func(scope, "foo", List.of(), typeResolver.resolveExprType(struct, scope), struct);

        var clazz = genClass(compUnit(List.of(), List.of(), List.of(func)));
        Object result = invokeFirst(clazz, null);
        var boolValue = (boolean) result.getClass().getField("f1").get(result);

        assertThat(boolValue).isTrue();
    }


    @ParameterizedTest
    @CsvSource({"true, 10", "false, 5"})
    void ifExpression(String bool, int actualResult) throws Exception {
        var ifExpr = new IfExpression(boolValue(bool), intValue("10"), intValue("5"), NR);
        var func = func(scope, "foo", List.of(), BuiltinType.INT, ifExpr);

        var clazz = genClass(compUnit(List.of(), List.of(), List.of(func)));
        int result = invokeFirst(clazz, null);

        assertThat(result).isEqualTo(actualResult);
    }

    @ParameterizedTest
    @CsvSource({"EQ, false", "NE, true", "GE, true", "LE, false", "LT, false", "GT, true"})
    void intCompareOperatorNotEquals(CompareOperator compareOp, boolean actualResult) throws Exception {
        var paramA = param("a", BuiltinType.INT);
        var paramB = param("b", BuiltinType.INT);
        var compare = new CompareExpression(compareOp, paramA.toReference(), paramB.toReference(), NR);
        var func = func(scope, "foo", List.of(paramA, paramB), BuiltinType.BOOLEAN, compare);

        var clazz = genClass(compUnit(List.of(), List.of(), List.of(func)));
        boolean result = invokeFirst(clazz, null, 6, 3);

        assertThat(result).isEqualTo(actualResult);
    }

    @ParameterizedTest
    @CsvSource({"EQ, true", "NE, false", "GE, true", "LE, true", "LT, false", "GT, false"})
    void intCompareOperatorEquals(CompareOperator compareOp, boolean actualResult) throws Exception {
        var paramA = param("a", BuiltinType.INT);
        var paramB = param("b", BuiltinType.INT);
        var compare = new CompareExpression(compareOp, paramA.toReference(), paramB.toReference(), NR);
        var func = func(scope, "foo", List.of(paramA, paramB), BuiltinType.BOOLEAN, compare);

        var clazz = genClass(compUnit(List.of(), List.of(), List.of(func)));
        boolean result = invokeFirst(clazz, null, 3, 3);

        assertThat(result).isEqualTo(actualResult);
    }

    @ParameterizedTest
    @CsvSource({"PLUS, 9", "MINUS, 3", "TIMES, 18", "DIVIDE, 2"})
    void intMathOperator(MathOperator mathOp, int actualResult) throws Exception {
        var paramA = param("a", BuiltinType.INT);
        var paramB = param("b", BuiltinType.INT);
        var plus = new MathExpression(mathOp, paramA.toReference(), paramB.toReference(), NR);
        var func = func(scope, "foo", List.of(paramA, paramB), BuiltinType.INT, plus);

        var clazz = genClass(compUnit(List.of(), List.of(), List.of(func)));
        int result = invokeFirst(clazz, null, 6, 3);

        assertThat(result).isEqualTo(actualResult);
    }

    private FunctionParameter param(String name, Type type) {
        var id = id(name);
        var param = new FunctionParameter(id, new TypeNode(type, NR));
        scope.addLocalIdentifier(param.id());
        return param;
    }

    @SuppressWarnings("unchecked")
    private <T> T invokeFirst(Class<?> clazz, Object obj, Object... args) throws Exception {
        return (T) clazz.getMethods()[0].invoke(obj, args);
    }

    private Class<?> genClass(CompilationUnit compilationUnit) throws Exception {
        return genClass(compilationUnit, false);
    }

    private Class<?> genClass(CompilationUnit compilationUnit, boolean dumpClasses) throws Exception {
        typeResolver.resolve(compilationUnit);
        var result = new BytecodeGenerator(typeResolver).generateBytecode(compilationUnit);
        if (dumpClasses) {
            dumpGeneratedClasses(result.generatedBytecode());
        }
        result.generatedBytecode().forEach(cl::addClass);
        return cl.loadClass(MOD_NAME);
    }

    private void dumpGeneratedClasses(Map<String, byte[]> generatedClasses) throws Exception {
        var tempPath = Files.createTempDirectory("class_dump_");
        for (Map.Entry<String, byte[]> entry : generatedClasses.entrySet()) {
            String name = entry.getKey();
            byte[] bytecode = entry.getValue();
            Main.saveBytecodeToFile(tempPath, name, bytecode);
        }
        System.err.println("Dumped files to: " + tempPath);
    }

    private CompilationUnit compUnit(List<Use> useList, List<Struct.Field> fields, List<Function> functions) {
        return new CompilationUnit(List.of(new ModuleDeclaration(id(MOD_NAME),
            Struct.moduleStruct(scope, MOD_NAME, useList, fields, functions, NR), NR)));
    }

    private CompilationUnit compUnit(Function function) {
        return compUnit(List.of(), List.of(), List.of(function));
    }
}