package com.pentlander.sasquach;

import static com.pentlander.sasquach.Fixtures.CLASS_NAME;
import static com.pentlander.sasquach.Fixtures.MOD_NAME;
import static com.pentlander.sasquach.Fixtures.PACKAGE_NAME;
import static com.pentlander.sasquach.Fixtures.QUAL_MOD_ID;
import static com.pentlander.sasquach.Fixtures.QUAL_MOD_NAME;
import static com.pentlander.sasquach.Fixtures.SOURCE_PATH;
import static com.pentlander.sasquach.Fixtures.boolValue;
import static com.pentlander.sasquach.Fixtures.id;
import static com.pentlander.sasquach.Fixtures.intValue;
import static com.pentlander.sasquach.Fixtures.qualId;
import static com.pentlander.sasquach.Fixtures.range;
import static com.pentlander.sasquach.Fixtures.stringValue;
import static com.pentlander.sasquach.Fixtures.tfunc;
import static com.pentlander.sasquach.TestUtils.invokeFirst;
import static com.pentlander.sasquach.TestUtils.invokeName;
import static com.pentlander.sasquach.Util.seqMap;
import static com.pentlander.sasquach.ast.expression.BinaryExpression.BooleanOperator.AND;
import static com.pentlander.sasquach.ast.expression.BinaryExpression.BooleanOperator.OR;
import static com.pentlander.sasquach.ast.expression.BinaryExpression.BooleanOperator.fromString;
import static com.pentlander.sasquach.ast.expression.BinaryExpression.CompareOperator;
import static com.pentlander.sasquach.ast.expression.BinaryExpression.MathOperator;
import static org.assertj.core.api.Assertions.assertThat;

import com.pentlander.sasquach.ast.Use;
import com.pentlander.sasquach.ast.expression.Struct.StructKind;
import com.pentlander.sasquach.ast.expression.Value;
import com.pentlander.sasquach.backend.BytecodeGenerator;
import com.pentlander.sasquach.name.NameResolutionResult;
import com.pentlander.sasquach.runtime.StructBase;
import com.pentlander.sasquach.tast.TCompilationUnit;
import com.pentlander.sasquach.tast.TFunctionParameter;
import com.pentlander.sasquach.tast.TModuleDeclaration;
import com.pentlander.sasquach.tast.TNamedFunction;
import com.pentlander.sasquach.tast.expression.TArrayValue;
import com.pentlander.sasquach.tast.expression.TBinaryExpression.TBooleanExpression;
import com.pentlander.sasquach.tast.expression.TBinaryExpression.TCompareExpression;
import com.pentlander.sasquach.tast.expression.TBinaryExpression.TMathExpression;
import com.pentlander.sasquach.tast.expression.TBlock;
import com.pentlander.sasquach.tast.expression.TForeignFieldAccess;
import com.pentlander.sasquach.tast.expression.TForeignFunctionCall;
import com.pentlander.sasquach.tast.expression.TIfExpression;
import com.pentlander.sasquach.tast.expression.TLiteralStructBuilder;
import com.pentlander.sasquach.tast.expression.TLocalFunctionCall;
import com.pentlander.sasquach.tast.expression.TLocalFunctionCall.TargetKind;
import com.pentlander.sasquach.tast.expression.TMemberFunctionCall;
import com.pentlander.sasquach.tast.expression.TModuleStructBuilder;
import com.pentlander.sasquach.tast.expression.TNot;
import com.pentlander.sasquach.tast.expression.TStruct.TField;
import com.pentlander.sasquach.tast.expression.TVarReference;
import com.pentlander.sasquach.tast.expression.TVarReference.RefDeclaration;
import com.pentlander.sasquach.tast.expression.TVarReference.RefDeclaration.Local;
import com.pentlander.sasquach.tast.expression.TVariableDeclaration;
import com.pentlander.sasquach.tast.expression.TypedExpression;
import com.pentlander.sasquach.type.BuiltinType;
import com.pentlander.sasquach.type.ClassType;
import com.pentlander.sasquach.type.FieldAccessKind;
import com.pentlander.sasquach.type.ForeignFieldType;
import com.pentlander.sasquach.type.ForeignFunctionType;
import com.pentlander.sasquach.type.FunctionType;
import com.pentlander.sasquach.type.NamedTypeResolver;
import com.pentlander.sasquach.type.StructType;
import com.pentlander.sasquach.type.Type;
import com.pentlander.sasquach.type.TypeParameter;
import com.pentlander.sasquach.type.UniversalType;
import java.io.PrintStream;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jdk.dynalink.linker.support.Lookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BytecodeGeneratorTest {
  private static final Range.Single NR = new Range.Single(SOURCE_PATH, new Position(1, 1), 1);

  private SasquachClassloader cl;

  @BeforeEach
  void setUp() {
    cl = new SasquachClassloader();
  }

  @ParameterizedTest
  @CsvSource({"true, true", "false, false"})
  void booleanValue(String boolStr, boolean actualResult) throws Exception {
    var func = tfunc("bool", List.of(), BuiltinType.BOOLEAN, boolValue(boolStr));

    var clazz = genClass(compUnit(List.of(), List.of(), List.of(func)));
    boolean result = invokeFirst(clazz);

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

    private <T> T declResult(BuiltinType type, String value) throws Exception {
      return declResult(type, new Value(type, value, NR));
    }

    private <T> T declResult(Type type, TypedExpression expr) throws Exception {
      var varDecl = new TVariableDeclaration(id("bar"), expr, NR);
      var varRef = new TVarReference(id("bar"), new RefDeclaration.Local(varDecl), expr.type());
      var block = new TBlock(List.of(varDecl, varRef), NR);
      var func = tfunc("foo", List.of(), type, block);

      var clazz = genClass(compUnit(List.of(), List.of(), List.of(func)), false);
      return invokeFirst(clazz);
    }
  }

  @Nested
  class ForeignFunctionHandleCalls {
    private DirectMethodHandleDesc declaredMethod(Class<?> clazz, String methodName,
        Class<?>... paramTypes) throws NoSuchMethodException {
      return Lookup.unreflect(MethodHandles.lookup(),
              clazz.getDeclaredMethod(methodName, paramTypes))
          .describeConstable()
          .map(DirectMethodHandleDesc.class::cast)
          .orElseThrow();
    }

    private DirectMethodHandleDesc declaredConstuctor(Class<?> clazz, Class<?>... paramTypes)
        throws NoSuchMethodException {
      return Lookup.unreflectConstructor(MethodHandles.lookup(),
              clazz.getDeclaredConstructor(paramTypes))
          .describeConstable()
          .map(DirectMethodHandleDesc.class::cast)
          .orElseThrow();
    }

    @Test
    void constructor() throws Exception {
      var className = "java/lang/StringBuilder";
      var alias = "StringBuilder";
      var type = new ClassType(className);
      var funcType = new ForeignFunctionType(declaredConstuctor(StringBuilder.class, String.class),
          null);
      var call = new TForeignFunctionCall(id(alias),
          id("new"),
          funcType,
          List.of(stringValue("hi")),
          type,
          NR);
      var func = tfunc("baz", List.of(), type, call);

      var clazz = genClass(compUnit(new Use.Foreign(qualId(className), id(alias), range()), func));
      StringBuilder result = invokeFirst(clazz);

      assertThat(result.toString()).isEqualTo("hi");
    }

    @Test
    void staticFunc() throws Exception {
      var type = new ClassType("java.nio.file.Path");
      List<TypedExpression> args = List.of(stringValue("hi.txt"),
          TArrayValue.ofElementType(BuiltinType.STRING, List.of(), NR));

      var funcType = new ForeignFunctionType(declaredMethod(Paths.class,
          "get",
          String.class,
          String[].class), null);
      var call = new TForeignFunctionCall(id("Paths"), id("get"), funcType, args, type, NR);
      var func = tfunc("baz", List.of(), type, call);

      var use = new Use.Foreign(qualId("java/nio/file/Paths"), id("Paths"), range());
      var clazz = genClass(compUnit(use, func), true);
      Path result = invokeFirst(clazz);

      assertThat(result).isEqualTo(Paths.get("hi.txt"));
    }

    @Test
    void virtualFunc() throws Exception {
      List<TypedExpression> args = List.of(stringValue("he"), stringValue("llo"));
      var funcType = new ForeignFunctionType(declaredMethod(String.class, "concat", String.class),
          null);
      var call = new TForeignFunctionCall(id("String"),
          id("concat"),
          funcType,
          args,
          BuiltinType.STRING,
          NR);
      var func = tfunc("baz", List.of(), BuiltinType.STRING, call);

      var clazz = genClass(compUnit(new Use.Foreign(qualId("java/lang/String"),
          id("String"),
          range()), func));
      String result = invokeFirst(clazz);

      assertThat(result).isEqualTo("hello");
    }
  }

  @Nested
  class ForeignFieldAccessTest {
    @Test
    void staticField() throws Exception {
      var use = new Use.Foreign(qualId("java/lang/System"), id("System"), NR);
      var type = new ForeignFieldType(new ClassType(PrintStream.class),
          new ClassType(System.class),
          FieldAccessKind.STATIC);
      var fieldAccess = new TForeignFieldAccess(id("System"), id("out"), type);
      var func = tfunc("foo", List.of(), new ClassType(PrintStream.class), fieldAccess);

      var clazz = genClass(compUnit(use, func));
      PrintStream ps = invokeFirst(clazz);

      assertThat(ps).isEqualTo(System.out);
    }
  }

  @Test
  void functionCall() throws Exception {
    var calleeFunc = tfunc("foo", List.of(), BuiltinType.INT, intValue("5"));
    var funcType = calleeFunc.type();
    var callerFunc = tfunc("baz", List.of(), BuiltinType.INT, new TLocalFunctionCall(id("foo"),
        new TargetKind.QualifiedFunction(QUAL_MOD_ID),
        List.of(),
        funcType,
        funcType.returnType(),
        NR));

    var clazz = genClass(compUnit(List.of(), List.of(), List.of(callerFunc, calleeFunc)));
    int result = invokeFirst(clazz);

    assertThat(result).isEqualTo(5);
  }

  @Test
  void structLiteralFields() throws Exception {
    var id = id("f1");
    var boolField = new TField(id, boolValue("true"));
    var struct = literalStructBuilder().fields(List.of(boolField)).build();
    var func = tfunc("foo", List.of(), struct.type(), struct);

    var clazz = genClass(compUnit(func));
    Object result = invokeFirst(clazz);
    var boolValue = (boolean) result.getClass().getField("f1").get(result);

    assertThat(boolValue).isTrue();
  }

  @Test
  void structLiteralFunctions() throws Exception {
    var returnType = BuiltinType.STRING;
    var funcType = new FunctionType(List.of(), List.of(), returnType);
    var structFunc = tfunc("member", List.of(), returnType, stringValue("string"));
    var struct = literalStructBuilder().addFields(new TField(
        structFunc.id(),
        structFunc.function())).build();
    var memberFuncCall = new TMemberFunctionCall(struct,
        id("member"),
        funcType,
        List.of(),
        returnType,
        range());
    var func = tfunc("foo", List.of(), BuiltinType.STRING, memberFuncCall);

    var clazz = genClass(compUnit(func), true);
    String result = invokeFirst(clazz);

    assertThat(result).isEqualTo("string");
  }

  @Nested
  class Parameterized {
    @Test
    void singleGenericClass() throws Exception {
      // Struct called "box" with a single field called "value" of type T
      var boxParam = tparam("box", new StructType(seqMap("value", new UniversalType("T", 0))));
      //  A value of type "U"
      var boxValueParam = tparam("boxValue", new UniversalType("U", 0));
      // Create a box struct with the field "value" set to the value of the "boxValue" param
      var parameterizedFuncExpr = literalStructBuilder().fields(List.of(new TField(id("value"),
          new TVarReference(id("boxValue"),
              new Local(boxValueParam),
              boxValueParam.variableType())))).build();

      var returnType = parameterizedFuncExpr.type();
      var typeParams = List.of(new TypeParameter(id("T")), new TypeParameter(id("U")));
      var funcType = new FunctionType(List.of(boxParam.type(), boxValueParam.type()),
          typeParams,
          returnType);

      var parameterizedFunc = tfunc("foo",
          List.of(boxParam, boxValueParam),
          typeParams,
          funcType,
          parameterizedFuncExpr);

      var callReturnType = (StructType) new NamedTypeResolver(NameResolutionResult.empty()).resolveNames(boxParam.type(),
          Map.of("T", BuiltinType.STRING),
          NR);
      var funcCall = new TLocalFunctionCall(id("foo"),
          new TargetKind.QualifiedFunction(QUAL_MOD_ID),
          List.of(
              literalStructBuilder().fields(List.of(new TField(id("value"), intValue(10)))).build(),
              stringValue("ten")),
          funcType,
          callReturnType,
          NR);
      var callerFunc = tfunc("baz",
          List.of(),
          new StructType(seqMap("value", BuiltinType.STRING)),
          funcCall);

      var compUnit = compUnit(List.of(), List.of(), List.of(callerFunc, parameterizedFunc));

      var result = new BytecodeGenerator().generateBytecode(compUnit.modules());
      result.generatedBytecode().forEach(cl::addClass);
      var clazz = cl.loadClass(CLASS_NAME);
      Object box = invokeName(clazz, "baz");

      assertThat(result.generatedBytecode()).hasSize(3);
      assertThat(box).isInstanceOf(StructBase.class);
    }
  }


  @ParameterizedTest
  @CsvSource({"true, 10", "false, 5"})
  void ifExpression(String bool, int actualResult) throws Exception {
    var ifExpr = new TIfExpression(boolValue(bool),
        intValue("10"),
        intValue("5"),
        BuiltinType.INT,
        NR);
    var func = tfunc("foo", List.of(), BuiltinType.INT, ifExpr);

    var clazz = genClass(compUnit(List.of(), List.of(), List.of(func)));
    int result = invokeFirst(clazz);

    assertThat(result).isEqualTo(actualResult);
  }

  @ParameterizedTest
  @CsvSource({"true, false", "false, true"})
  void notExpression(boolean left, boolean right) throws Exception {
    var notExpr = new TNot(boolValue(left), NR);
    var func = tfunc("foo", List.of(), BuiltinType.BOOLEAN, notExpr);

    var clazz = genClass(compUnit(List.of(), List.of(), List.of(func)));
    boolean result = invokeFirst(clazz);

    assertThat(result).isEqualTo(right);
  }

  @ParameterizedTest
  @CsvSource({"true, &&, true", "false, ||, true", "true, ||, false"})
  void booleanExpressionTrue(boolean left, String operator, boolean right) throws Exception {
    var op = fromString(operator);
    var boolExpr = new TBooleanExpression(op, boolValue(left), boolValue(right), NR);
    var func = tfunc("foo", List.of(), BuiltinType.BOOLEAN, boolExpr);

    var clazz = genClass(compUnit(List.of(), List.of(), List.of(func)));
    boolean result = invokeFirst(clazz);

    assertThat(result).isEqualTo(true);
  }

  @ParameterizedTest
  @CsvSource({"false, &&, true", "true, &&, false", "false, ||, false"})
  void booleanExpressionFalse(boolean left, String operator, boolean right) throws Exception {
    var op = fromString(operator);
    var boolExpr = new TBooleanExpression(op, boolValue(left), boolValue(right), NR);
    var func = tfunc("foo", List.of(), BuiltinType.BOOLEAN, boolExpr);

    var clazz = genClass(compUnit(List.of(), List.of(), List.of(func)));
    boolean result = invokeFirst(clazz);

    assertThat(result).isEqualTo(false);
  }

  @Test
  void complexBooleanExpression() throws Exception {
    var boolExpr = new TBooleanExpression(OR,
        new TBooleanExpression(AND, boolValue(true), boolValue(false), NR),
        boolValue(true),
        NR);
    var func = tfunc("foo", List.of(), BuiltinType.BOOLEAN, boolExpr);

    var clazz = genClass(compUnit(List.of(), List.of(), List.of(func)));
    boolean result = invokeFirst(clazz);

    assertThat(result).isEqualTo(true);
  }

  @ParameterizedTest
  @CsvSource({"EQ, false", "NE, true", "GE, true", "LE, false", "LT, false", "GT, true"})
  void intCompareOperatorNotEquals(CompareOperator compareOp, boolean actualResult)
      throws Exception {
    var paramA = tparam("a", BuiltinType.INT);
    var paramB = tparam("b", BuiltinType.INT);
    var compare = new TCompareExpression(compareOp, paramToRef(paramA), paramToRef(paramB), NR);
    var func = tfunc("foo", List.of(paramA, paramB), BuiltinType.BOOLEAN, compare);

    var clazz = genClass(compUnit(List.of(), List.of(), List.of(func)));
    boolean result = invokeFirst(clazz, 6, 3);

    assertThat(result).isEqualTo(actualResult);
  }

  @ParameterizedTest
  @CsvSource({"EQ, true", "NE, false", "GE, true", "LE, true", "LT, false", "GT, false"})
  void intCompareOperatorEquals(CompareOperator compareOp, boolean actualResult) throws Exception {
    var paramA = tparam("a", BuiltinType.INT);
    var paramB = tparam("b", BuiltinType.INT);
    var compare = new TCompareExpression(compareOp, paramToRef(paramA), paramToRef(paramB), NR);
    var func = tfunc("foo", List.of(paramA, paramB), BuiltinType.BOOLEAN, compare);

    var clazz = genClass(compUnit(List.of(), List.of(), List.of(func)));
    boolean result = invokeFirst(clazz, 3, 3);

    assertThat(result).isEqualTo(actualResult);
  }

  @ParameterizedTest
  @CsvSource({"PLUS, 9", "MINUS, 3", "TIMES, 18", "DIVIDE, 2"})
  void intMathOperator(MathOperator mathOp, int actualResult) throws Exception {
    var paramA = tparam("a", BuiltinType.INT);
    var paramB = tparam("b", BuiltinType.INT);
    var plus = new TMathExpression(mathOp, paramToRef(paramA), paramToRef(paramB), NR);
    var func = tfunc("foo", List.of(paramA, paramB), BuiltinType.INT, plus);

    var clazz = genClass(compUnit(List.of(), List.of(), List.of(func)));
    int result = invokeFirst(clazz, 6, 3);

    assertThat(result).isEqualTo(actualResult);
  }

  private TFunctionParameter tparam(String name, Type type) {
    return new TFunctionParameter(id(name), type, range());
  }

  private Class<?> genClass(TCompilationUnit compilationUnit) throws Exception {
    return genClass(compilationUnit, false);
  }

  private Class<?> genClass(TCompilationUnit compilationUnit, boolean dumpClasses)
      throws Exception {
    var result = new BytecodeGenerator().generateBytecode(compilationUnit.modules());
    if (dumpClasses) {
      TestUtils.dumpGeneratedClasses(result.generatedBytecode());
    }
    result.generatedBytecode().forEach(cl::addClass);
    return cl.loadClass(CLASS_NAME);
  }

  private TCompilationUnit compUnit(List<Use> useList, List<TField> fields,
      List<TNamedFunction> functions) {
    var struct = TModuleStructBuilder.builder()
        .name(QUAL_MOD_NAME)
        .useList(useList)
        .fields(fields)
        .functions(functions)
        .range(NR)
        .build();
    return new TCompilationUnit(new SourcePath("test.sasq"),
        PACKAGE_NAME,
        List.of(new TModuleDeclaration(QUAL_MOD_ID, struct, NR)));
  }

  private TCompilationUnit compUnit(TNamedFunction function) {
    return compUnit(List.of(), List.of(), List.of(function));
  }

  private TCompilationUnit compUnit(Use use, TNamedFunction function) {
    return compUnit(List.of(use), List.of(), List.of(function));
  }

  private TLiteralStructBuilder literalStructBuilder() {
    return TLiteralStructBuilder.builder()
        .fields(List.of())
        .range(NR);
  }

  private TVarReference paramToRef(TFunctionParameter param) {
    return new TVarReference(id(param.name()), new Local(param), param.type());
  }
}
