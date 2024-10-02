package com.pentlander.sasquach;


import com.pentlander.sasquach.ast.Argument;
import com.pentlander.sasquach.ast.expression.Expression;
import com.pentlander.sasquach.ast.expression.Function;
import com.pentlander.sasquach.ast.expression.NamedFunction;
import com.pentlander.sasquach.ast.expression.Struct;
import com.pentlander.sasquach.ast.expression.Struct.Field;
import com.pentlander.sasquach.ast.expression.Value;
import com.pentlander.sasquach.ast.id.Id;
import com.pentlander.sasquach.ast.id.QualifiedModuleId;
import com.pentlander.sasquach.ast.id.TypeId;
import com.pentlander.sasquach.ast.typenode.BasicTypeNode;
import com.pentlander.sasquach.ast.typenode.FunctionSignature;
import com.pentlander.sasquach.ast.typenode.TypeNode;
import com.pentlander.sasquach.name.QualifiedModuleName;
import com.pentlander.sasquach.name.QualifiedTypeName;
import com.pentlander.sasquach.name.UnqualifiedName;
import com.pentlander.sasquach.nameres.ForeignFunctionHandle;
import com.pentlander.sasquach.nameres.ForeignFunctions;
import com.pentlander.sasquach.tast.TFunctionParameter;
import com.pentlander.sasquach.tast.TFunctionSignature;
import com.pentlander.sasquach.tast.TNamedFunction;
import com.pentlander.sasquach.tast.expression.TFunction;
import com.pentlander.sasquach.tast.expression.TStruct.TField;
import com.pentlander.sasquach.tast.expression.TypedExpression;
import com.pentlander.sasquach.type.BuiltinType;
import com.pentlander.sasquach.type.FunctionType;
import com.pentlander.sasquach.type.Type;
import com.pentlander.sasquach.type.TypeParameterNode;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import jdk.dynalink.linker.support.Lookup;

public class Fixtures {
  private static final Lookup LOOKUP = new Lookup(MethodHandles.lookup());
  private static final AtomicInteger RANGE_COUNTER = new AtomicInteger();
  public static final SourcePath SOURCE_PATH = new SourcePath("test.sasq");
  public static final PackageName PACKAGE_NAME = new PackageName("test");
  public static final QualifiedModuleName QUAL_MOD_NAME = new QualifiedModuleName(PACKAGE_NAME,
      "Test");
  public static final QualifiedModuleId QUAL_MOD_ID = new QualifiedModuleId(QUAL_MOD_NAME, range());
  public static final String MOD_NAME = QUAL_MOD_NAME.toString();
  public static final String CLASS_NAME = MOD_NAME.replace('/', '.');

  public static Range.Single range() {
    return new Range.Single(SOURCE_PATH, new Position(RANGE_COUNTER.getAndIncrement(), 1), 1);
  }

  public static Id id(String name) {
    return new Id(name, range());
  }

  public static Id id(UnqualifiedName name) {
    return new Id(name, range());
  }

  public static TypeId typeId(String name) {
    return new TypeId(typeName(name), range());
  }

  public static UnqualifiedName name(String name) {
    return new UnqualifiedName(name);
  }

  public static QualifiedTypeName typeName(String name) {
    return QualifiedModuleName.fromString(name).toQualifiedTypeName();
  }

  public static QualifiedModuleId qualId(String name) {
    return new QualifiedModuleId(PACKAGE_NAME, name, range());
  }

  public static TNamedFunction tfunc(String name, List<TFunctionParameter> funcParams,
      Type returnType, TypedExpression expression) {
    var paramTypes = funcParams.stream()
        .map(FunctionType.Param::from)
        .toList();
    var funcType = new FunctionType(paramTypes, List.of(), returnType);
    return tfunc(name, funcParams, List.of(), funcType, expression);
  }

  public static TField tfield(String name, TypedExpression expression) {
    return new TField(id(name), expression);
  }

  public static TypeNode typeNode(BuiltinType type) {
    return new BasicTypeNode(type, range());
  }

  public static TNamedFunction tfunc(String name, List<TFunctionParameter> functionParameters,
      List<TypeParameterNode> typeParameterNodes, FunctionType funcType, TypedExpression expression) {
    var funcId = id(name);
    return new TNamedFunction(funcId,
        new TFunction(new TFunctionSignature(functionParameters, typeParameterNodes,
            funcType.returnType(),
            range()), expression, List.of()));
  }

  public static NamedFunction voidFunc(String name, Expression expression) {
    var funcId = id(name);
    return new NamedFunction(funcId, new Function(
        new FunctionSignature(List.of(), List.of(), typeNode(BuiltinType.VOID), range()),
        expression));
  }

  public static Struct literalStruct(List<Field> fields, List<NamedFunction> functions) {
    return Struct.literalStruct(fields, functions, List.of(), range());
  }

  public static Value intValue(String value) {
    return new Value(BuiltinType.INT, value, range());
  }

  public static Value intValue(int value) {
    return new Value(BuiltinType.INT, String.valueOf(value), range());
  }

  public static Value boolValue(String value) {
    return new Value(BuiltinType.BOOLEAN, value, range());
  }

  public static Value boolValue(boolean value) {
    return new Value(BuiltinType.BOOLEAN, String.valueOf(value), range());
  }

  public static Value stringValue(String value) {
    return new Value(BuiltinType.STRING, value, range());
  }

  public static ForeignFunctions foreignMethods(Class<?> clazz, Predicate<Method> methodPredicate) {
    return new ForeignFunctions(clazz,
        Arrays.stream(clazz.getMethods()).filter(methodPredicate).map(m -> new ForeignFunctionHandle((DirectMethodHandleDesc) LOOKUP.unreflect(m)
            .describeConstable()
            .orElseThrow(), m)).toList());
  }

  public static ForeignFunctions foreignMethods(Class<?> clazz, String name) {
    return foreignMethods(clazz, method -> method.getName().equals(name));
  }

  public static ForeignFunctions foreignConstructors(Class<?> clazz) {
    return new ForeignFunctions(clazz,
        Arrays.stream(clazz.getConstructors())
            .map(c -> new ForeignFunctionHandle((DirectMethodHandleDesc) LOOKUP.unreflectConstructor(
                c).describeConstable().orElseThrow(), c))
            .toList());
  }

  public static List<Argument> args(Expression... exprs) {
    return Arrays.stream(exprs).map(Argument::new).toList();
  }
}
