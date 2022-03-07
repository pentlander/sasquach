package com.pentlander.sasquach;

import static com.pentlander.sasquach.Fixtures.CLASS_NAME;
import static com.pentlander.sasquach.Fixtures.MOD_NAME;
import static com.pentlander.sasquach.Fixtures.PACKAGE_NAME;
import static com.pentlander.sasquach.Fixtures.SOURCE_PATH;
import static com.pentlander.sasquach.Fixtures.boolValue;
import static com.pentlander.sasquach.Fixtures.func;
import static com.pentlander.sasquach.Fixtures.id;
import static com.pentlander.sasquach.Fixtures.intValue;
import static com.pentlander.sasquach.Fixtures.literalStruct;
import static com.pentlander.sasquach.Fixtures.qualId;
import static com.pentlander.sasquach.Fixtures.range;
import static com.pentlander.sasquach.Fixtures.stringValue;
import static com.pentlander.sasquach.ast.expression.BinaryExpression.CompareExpression;
import static com.pentlander.sasquach.ast.expression.BinaryExpression.CompareOperator;
import static com.pentlander.sasquach.ast.expression.BinaryExpression.MathExpression;
import static com.pentlander.sasquach.ast.expression.BinaryExpression.MathOperator;
import static org.assertj.core.api.Assertions.assertThat;

import com.pentlander.sasquach.ast.BasicTypeNode;
import com.pentlander.sasquach.ast.CompilationUnit;
import com.pentlander.sasquach.ast.FunctionSignature;
import com.pentlander.sasquach.ast.ModuleDeclaration;
import com.pentlander.sasquach.ast.StructTypeNode;
import com.pentlander.sasquach.ast.TypeNode;
import com.pentlander.sasquach.ast.Use;
import com.pentlander.sasquach.ast.expression.ArrayValue;
import com.pentlander.sasquach.ast.expression.Block;
import com.pentlander.sasquach.ast.expression.Expression;
import com.pentlander.sasquach.ast.expression.ForeignFieldAccess;
import com.pentlander.sasquach.ast.expression.ForeignFunctionCall;
import com.pentlander.sasquach.ast.expression.Function;
import com.pentlander.sasquach.ast.expression.FunctionParameter;
import com.pentlander.sasquach.ast.expression.IfExpression;
import com.pentlander.sasquach.ast.expression.LocalFunctionCall;
import com.pentlander.sasquach.ast.expression.MemberFunctionCall;
import com.pentlander.sasquach.ast.expression.NamedFunction;
import com.pentlander.sasquach.ast.expression.Struct;
import com.pentlander.sasquach.ast.expression.Struct.Field;
import com.pentlander.sasquach.ast.expression.Value;
import com.pentlander.sasquach.ast.expression.VarReference;
import com.pentlander.sasquach.ast.expression.VariableDeclaration;
import com.pentlander.sasquach.backend.BytecodeGenerator;
import com.pentlander.sasquach.name.ModuleResolver;
import com.pentlander.sasquach.runtime.StructBase;
import com.pentlander.sasquach.type.BuiltinType;
import com.pentlander.sasquach.type.ClassType;
import com.pentlander.sasquach.type.LocalNamedType;
import com.pentlander.sasquach.type.StructType;
import com.pentlander.sasquach.type.Type;
import com.pentlander.sasquach.type.TypeParameter;
import com.pentlander.sasquach.type.TypeResolver;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BytecodeGeneratorTest {
    private static final Range.Single NR = new Range.Single(SOURCE_PATH, new Position(1, 1), 1);

    private SasquachClassloader cl;
    private TypeResolver typeResolver;
    private ModuleResolver nameResolver;

    @BeforeEach
    void setUp() {
        cl = new SasquachClassloader();
        nameResolver = new ModuleResolver();
    }

    @ParameterizedTest
    @CsvSource({"true, true", "false, false"})
    void booleanValue(String boolStr, boolean actualResult) throws Exception {
        var func = func("bool", List.of(), BuiltinType.BOOLEAN, boolValue(boolStr));

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

        private <T> T declResult(BuiltinType type, String value) throws Exception {
            return declResult(type, new Value(type, value, NR));
        }

        private <T> T declResult(Type type, Expression expr) throws Exception {
            var varDecl = new VariableDeclaration(id("bar"), expr, NR);
            var block = new Block(List.of(varDecl, VarReference.of("bar", NR)), NR);
            var func = func("foo", List.of(), type, block);

            var clazz = genClass(compUnit(List.of(), List.of(), List.of(func)));
            return invokeFirst(clazz, null);
        }
    }

    @Nested
    class ForeignFunctionHandleCalls {
        @Test
        void constructor() throws Exception {
            var className = "java/lang/StringBuilder";
            var alias = "StringBuilder";
            var type = new ClassType(className);
            var call = new ForeignFunctionCall(id(alias), id("new"), List.of(stringValue("hi")),
                    NR);
            var func = func("baz", List.of(), type, call);

            var clazz = genClass(compUnit(new Use.Foreign(qualId(className), id(alias), range()),
                func));
            StringBuilder result = invokeFirst(clazz, null);

            assertThat(result.toString()).isEqualTo("hi");
        }

        @Test
        void staticFunc() throws Exception {
            var type = new ClassType( "java.nio.file.Path");
            List<Expression> args = List.of(stringValue("hi.txt"),
                    ArrayValue.ofElementType(BuiltinType.STRING, List.of(), NR));
            var call = new ForeignFunctionCall(id("Paths"), id("get"), args,
                    NR);
            var func = func("baz", List.of(), type, call);

            var clazz = genClass(compUnit(new Use.Foreign(qualId("java/nio/file/Paths"),
                id("Paths"),
                range()), func), true);
            Path result = invokeFirst(clazz, null);

            assertThat(result).isEqualTo(Paths.get("hi.txt"));
        }

        @Test
        void virtualFunc() throws Exception {
            List<Expression> args = List.of(stringValue("he"), stringValue("llo"));
            var call = new ForeignFunctionCall(id("String"), id("concat"), args, NR);
            var func = func("baz", List.of(), BuiltinType.STRING, call);

            var clazz = genClass(compUnit(new Use.Foreign(qualId("java/lang/String"),
                id("String"),
                range()), func));
            String result = invokeFirst(clazz, null);

            assertThat(result).isEqualTo("hello");
        }
    }

    @Nested
    class ForeignFieldAccessTest {
        @Test
        void staticField() throws Exception {
            var use = new Use.Foreign(qualId("java/lang/System"), id("System"), NR);
            var fieldAccess = new ForeignFieldAccess(id("System"), id("out"));
            var func = func("foo", List.of(), new ClassType(PrintStream.class), fieldAccess);

            var clazz = genClass(compUnit(use, func));
            PrintStream ps = invokeFirst(clazz, null);

            assertThat(ps).isEqualTo(System.out);
        }
    }

    @Test
    void functionCall() throws Exception {
        var calleeFunc = func("foo", List.of(), BuiltinType.INT, intValue("5"));
        var callerFunc = func("baz", List.of(), BuiltinType.INT, new LocalFunctionCall(id("foo"),
            List.of(), NR));

        var clazz = genClass(compUnit(List.of(), List.of(), List.of(callerFunc, calleeFunc)));
        int result = invokeFirst(clazz, null);

        assertThat(result).isEqualTo(5);
    }

    @Test
    void structLiteralFields() throws Exception {
        var id = id("f1");
        var boolField = new Struct.Field(id, boolValue("true"));
        var struct = literalStruct(List.of(boolField), List.of());
        var structType = new StructType(Map.of(id.name(), BuiltinType.BOOLEAN));
        var func = func("foo", List.of(), structType, struct);

        var clazz = genClass(compUnit(func));
        Object result = invokeFirst(clazz, null);
        var boolValue = (boolean) result.getClass().getField("f1").get(result);

        assertThat(boolValue).isTrue();
    }

    @Test
    void structLiteralFunctions() throws Exception {
        var structId = id("member");
        var structFunc = new NamedFunction(structId, new Function(
            new FunctionSignature(List.of(), new BasicTypeNode<>(BuiltinType.STRING, range()), range()),
            stringValue("string")));
        var struct = Struct.literalStruct(List.of(), List.of(structFunc), range());
        var memberFuncCall = new MemberFunctionCall(struct, id("member"), List.of(), range());
        var func = func(
            "foo",
            List.of(),
            BuiltinType.STRING,
            memberFuncCall);

        var clazz = genClass(compUnit(func), true);
        String result = invokeFirst(clazz, null);

        assertThat(result).isEqualTo("string");
    }

    @Nested
    class Parameterized {
        @Test
        void singleGenericClass() throws Exception {
            var boxParam = param(
                "box",
                new StructTypeNode(Map.of("value",
                    new BasicTypeNode<>(new LocalNamedType(id("T")), range())), range()));
            var boxValueParam = param("boxValue", new LocalNamedType(id("U")));
            var parameterizedFuncExpr = literalStruct(
                List.of(new Field(id("value"), new VarReference(id("boxValue")))),
                List.of());
            var parameterizedFunc = func(
                "foo",
                List.of(boxParam, boxValueParam),
                List.of(new TypeParameter(id("T")), new TypeParameter(id("U"))),
                new StructType(Map.of("value", new LocalNamedType(id("U")))),
                parameterizedFuncExpr);
            var callerFunc = func(
                "baz",
                List.of(),
                new StructType(Map.of("value", BuiltinType.STRING)),
                new LocalFunctionCall(id("foo"),
                    List.of(literalStruct(
                        List.of(new Field(id("value"), intValue(10))),
                        List.of()), stringValue("ten")),
                    NR));

            var compUnit = compUnit(List.of(), List.of(), List.of(callerFunc, parameterizedFunc));
            System.err.println(compUnit.modules().get(0).toPrettyString());
            var resolutionResult = nameResolver.resolveCompilationUnit(compUnit);
            if (!resolutionResult.errors().errors().isEmpty()) {
                throw new IllegalStateException(resolutionResult.errors().errors().toString());
            }
            typeResolver = new TypeResolver(resolutionResult);
            typeResolver.resolve(compUnit);
            var result = new BytecodeGenerator(nameResolver, typeResolver).generateBytecode(compUnit);
            result.generatedBytecode().forEach(cl::addClass);
            var clazz =  cl.loadClass(CLASS_NAME);
            Object box = invokeName(clazz, "baz", null);

            assertThat(result.generatedBytecode()).hasSize(3);
            assertThat(box).isInstanceOf(StructBase.class);
        }
    }


    @ParameterizedTest
    @CsvSource({"true, 10", "false, 5"})
    void ifExpression(String bool, int actualResult) throws Exception {
        var ifExpr = new IfExpression(boolValue(bool), intValue("10"), intValue("5"), NR);
        var func = func("foo", List.of(), BuiltinType.INT, ifExpr);

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
        var func = func("foo", List.of(paramA, paramB), BuiltinType.BOOLEAN, compare);

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
        var func = func("foo", List.of(paramA, paramB), BuiltinType.BOOLEAN, compare);

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
        var func = func("foo", List.of(paramA, paramB), BuiltinType.INT, plus);

        var clazz = genClass(compUnit(List.of(), List.of(), List.of(func)));
        int result = invokeFirst(clazz, null, 6, 3);

        assertThat(result).isEqualTo(actualResult);
    }

    private FunctionParameter param(String name, Type type) {
        return param(name, new BasicTypeNode<>(type, range()));
    }

    private <T extends TypeNode<? extends Type>> FunctionParameter param(String name, T typeNode) {
        var id = id(name);
        return new FunctionParameter(id, (TypeNode<Type>) typeNode);
    }

    @SuppressWarnings("unchecked")
    private <T> T invokeFirst(Class<?> clazz, Object obj, Object... args) throws Exception {
        return (T) clazz.getMethods()[0].invoke(obj, args);
    }

    @SuppressWarnings("unchecked")
    private <T> T invokeName(Class<?> clazz, String name, Object obj, Object... args) throws Exception {
         for (var method : clazz.getMethods()) {
             if (method.getName().equals(name)) {
                 return (T) method.invoke(obj, args);
             }
         }
         throw new NoSuchMethodException();
    }

    private Class<?> genClass(CompilationUnit compilationUnit) throws Exception {
        return genClass(compilationUnit, false);
    }

    private Class<?> genClass(CompilationUnit compilationUnit, boolean dumpClasses) throws Exception {
        var resolutionResult = nameResolver.resolveCompilationUnit(compilationUnit);
        if (!resolutionResult.errors().errors().isEmpty()) {
            throw new IllegalStateException(resolutionResult.errors().toString());
        }
        typeResolver = new TypeResolver(resolutionResult);
        typeResolver.resolve(compilationUnit);
        var result = new BytecodeGenerator(nameResolver, typeResolver).generateBytecode(compilationUnit);
        if (dumpClasses) {
            dumpGeneratedClasses(result.generatedBytecode());
        }
        result.generatedBytecode().forEach(cl::addClass);
        return cl.loadClass(CLASS_NAME);
    }

    public static void dumpGeneratedClasses(Map<String, byte[]> generatedClasses) {
        try {
            var tempPath = Files.createTempDirectory("class_dump_");
            for (Map.Entry<String, byte[]> entry : generatedClasses.entrySet()) {
                String name = entry.getKey();
                byte[] bytecode = entry.getValue();
                Compiler.saveBytecodeToFile(tempPath, name, bytecode);
            }
            System.err.println("Dumped files to: " + tempPath);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private CompilationUnit compUnit(List<Use> useList, List<Struct.Field> fields,
        List<NamedFunction> functions) {
        return new CompilationUnit(new SourcePath("test.sasq"), PACKAGE_NAME,
            List.of(new ModuleDeclaration(qualId(MOD_NAME),
            Struct.moduleStruct(MOD_NAME, useList, List.of(), fields, functions, NR), NR)));
    }

    private CompilationUnit compUnit(NamedFunction function) {
        return compUnit(List.of(), List.of(), List.of(function));
    }

    private CompilationUnit compUnit(Use use, NamedFunction function) {
        return compUnit(List.of(use), List.of(), List.of(function));
    }
}