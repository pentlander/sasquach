package com.pentlander.sasquach;

import com.pentlander.sasquach.ast.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static com.pentlander.sasquach.ast.BinaryExpression.*;
import static org.assertj.core.api.Assertions.assertThat;

class BytecodeGeneratorTest {
    private static final String MOD_NAME = "Test";
    private static final Range.Single NR = new Range.Single(new Position(1, 1), 1);

    private SasquachClassloader cl;
    private Scope scope;

    @BeforeEach
    void setUp() {
        cl = new SasquachClassloader();
        scope = new Scope(new Metadata(MOD_NAME));
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
            var varDecl = new VariableDeclaration("bar", expr, 0, NR, NR);
            var block = new Block(scope,
                    List.of(varDecl), new Identifier("bar", type, NR), NR);
            var func = func(scope, "foo", List.of(), type, block);
            scope.addIdentifier(varDecl.toIdentifier());

            var clazz = genClass(compUnit(List.of(), List.of(), List.of(func)));
            return invokeFirst(clazz, null);
        }
    }

    @Test
    void functionCall() throws Exception {
        var calleeFunc = func(scope, "foo", List.of(), BuiltinType.INT, intValue("5"));
        var callerFunc = func(scope, "baz", List.of(), BuiltinType.INT, new FunctionCall("foo",
                calleeFunc.functionSignature(),  List.of(), null, NR));

        var clazz = genClass(compUnit(List.of(), List.of(), List.of(callerFunc, calleeFunc)));
        int result = invokeFirst(clazz, null);

        assertThat(result).isEqualTo(5);
    }

    @Test
    void structLiteralFields() throws Exception {
        var boolField = new Struct.Field("f1", boolValue("true"), NR);
        var struct = Struct.literalStruct(List.of(boolField), List.of(), NR);
        var func = func(scope, "foo", List.of(), struct.type(), struct);

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
        var compare = new CompareExpression(compareOp, paramA.toIdentifier(), paramB.toIdentifier(), NR);
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
        var compare = new CompareExpression(compareOp, paramA.toIdentifier(), paramB.toIdentifier(), NR);
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
        var plus = new MathExpression(mathOp, paramA.toIdentifier(), paramB.toIdentifier(), NR);
        var func = func(scope, "foo", List.of(paramA, paramB), BuiltinType.INT, plus);

        var clazz = genClass(compUnit(List.of(), List.of(), List.of(func)));
        int result = invokeFirst(clazz, null, 6, 3);

        assertThat(result).isEqualTo(actualResult);
    }

    private FunctionParameter param(String name, Type type) {
        var param = new FunctionParameter(name, type, NR, NR);
        scope.addIdentifier(param.toIdentifier());
        return param;
    }

    @SuppressWarnings("unchecked")
    private <T> T invokeFirst(Class<?> clazz, Object obj, Object... args) throws Exception {
        return (T) clazz.getMethods()[0].invoke(obj, args);
    }

    private Class<?> genClass(CompilationUnit compilationUnit) throws Exception {
        var result = new BytecodeGenerator().generateBytecode(compilationUnit);
        result.generatedBytecode().forEach(cl::addClass);
        return cl.loadClass(MOD_NAME);
    }

    private static CompilationUnit compUnit(List<Use> useList, List<Struct.Field> fields, List<Function> functions) {
        return new CompilationUnit(
                new ModuleDeclaration(MOD_NAME, Struct.moduleStruct(MOD_NAME, useList, fields, functions, NR)));
    }

    private static Function func(Scope scope, String name, List<FunctionParameter> functionParameters,
                                 Type returnType, Expression expression) {
        return new Function(scope, name, new FunctionSignature(functionParameters, returnType, NR, NR), expression, NR);
    }

    private static Value intValue(String value) {
        return new Value(BuiltinType.INT, value, NR);
    }

    private static Value boolValue(String value) {
        return new Value(BuiltinType.BOOLEAN, value, NR);
    }
}