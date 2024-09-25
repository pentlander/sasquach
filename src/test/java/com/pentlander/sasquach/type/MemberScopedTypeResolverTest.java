package com.pentlander.sasquach.type;

import static com.pentlander.sasquach.Fixtures.*;
import static com.pentlander.sasquach.Fixtures.boolValue;
import static com.pentlander.sasquach.Fixtures.foreignConstructors;
import static com.pentlander.sasquach.Fixtures.foreignMethods;
import static com.pentlander.sasquach.Fixtures.id;
import static com.pentlander.sasquach.Fixtures.intValue;
import static com.pentlander.sasquach.Fixtures.literalStruct;
import static com.pentlander.sasquach.Fixtures.name;
import static com.pentlander.sasquach.Fixtures.range;
import static com.pentlander.sasquach.Fixtures.stringValue;
import static com.pentlander.sasquach.Fixtures.typeId;
import static com.pentlander.sasquach.Fixtures.typeNode;
import static com.pentlander.sasquach.Util.seqMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pentlander.sasquach.PackageName;
import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Argument;
import com.pentlander.sasquach.ast.FunctionSignature;
import com.pentlander.sasquach.ast.Id;
import com.pentlander.sasquach.ast.Node;
import com.pentlander.sasquach.ast.QualifiedModuleName;
import com.pentlander.sasquach.ast.UnqualifiedName;
import com.pentlander.sasquach.ast.expression.ArrayValue;
import com.pentlander.sasquach.ast.expression.BinaryExpression.CompareExpression;
import com.pentlander.sasquach.ast.expression.BinaryExpression.CompareOperator;
import com.pentlander.sasquach.ast.expression.BinaryExpression.MathExpression;
import com.pentlander.sasquach.ast.expression.BinaryExpression.MathOperator;
import com.pentlander.sasquach.ast.expression.Expression;
import com.pentlander.sasquach.ast.expression.MemberAccess;
import com.pentlander.sasquach.ast.expression.ForeignFunctionCall;
import com.pentlander.sasquach.ast.expression.Function;
import com.pentlander.sasquach.ast.expression.FunctionParameter;
import com.pentlander.sasquach.ast.expression.LocalFunctionCall;
import com.pentlander.sasquach.ast.expression.MemberFunctionCall;
import com.pentlander.sasquach.ast.expression.NamedFunction;
import com.pentlander.sasquach.ast.expression.Struct;
import com.pentlander.sasquach.ast.expression.Struct.Field;
import com.pentlander.sasquach.ast.expression.VarReference;
import com.pentlander.sasquach.ast.expression.VariableDeclaration;
import com.pentlander.sasquach.name.MemberScopedNameResolver.QualifiedFunction;
import com.pentlander.sasquach.name.NameResolutionResult;
import com.pentlander.sasquach.type.ModuleScopedTypes.FuncCallType;
import com.pentlander.sasquach.type.ModuleScopedTypes.VarRefType;
import com.pentlander.sasquach.type.StructType.RowModifier;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.SequencedMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MemberScopedTypeResolverTest {
  NameResolutionResult nameResolutionResult;
  MemberScopedTypeResolver memberScopedTypeResolver;
  ModuleScopedTypes moduleScopedTypes;
  boolean shouldAssertErrorsEmpty = true;

  public static StructType unnamed(SequencedMap<UnqualifiedName, Type> fieldTypes, RowModifier rowModifier) {
    return new StructType(null, List.of(), fieldTypes, rowModifier);
  }

  @BeforeEach
  void setUp() {
    moduleScopedTypes = mock(ModuleScopedTypes.class);
    nameResolutionResult = mock(NameResolutionResult.class);
    memberScopedTypeResolver = new MemberScopedTypeResolver(nameResolutionResult, moduleScopedTypes);
    shouldAssertErrorsEmpty = true;
  }

  @AfterEach
  void tearDown() {
    if (shouldAssertErrorsEmpty) {
      assertThat(memberScopedTypeResolver.errors().errors()).isEmpty();
    }
    shouldAssertErrorsEmpty = true;
  }

  @Test
  void value() {
    var type = resolveExpr(intValue("10"));

    assertThat(type).isEqualTo(BuiltinType.INT);
  }

  @Test
  void varReference() {
    var varId = id("foo");
    var varDecl = new VariableDeclaration(varId, stringValue("hi"), range());
    when(moduleScopedTypes.getVarReferenceType(any())).thenReturn(new VarRefType.LocalVar(varDecl));

    var declType = resolveExpr(varDecl);
    var refType = resolveExpr(new VarReference(id("foo")));

    assertThat(declType).isEqualTo(BuiltinType.VOID);
    assertThat(refType).isEqualTo(BuiltinType.STRING);
  }

  @Nested
  class BinaryExpression {

    @Test
    void compare() {
      var type = resolveExpr(new CompareExpression(CompareOperator.EQ,
          intValue("10"),
          intValue("11"),
          range()));

      assertThat(type).isEqualTo(BuiltinType.BOOLEAN);
    }

    @Test
    void math() {
      var type = resolveExpr(new MathExpression(MathOperator.PLUS,
          intValue("1"),
          intValue("2"),
          range()));

      assertThat(type).isEqualTo(BuiltinType.INT);
    }

    @Test
    void typeMismatch() {
      var range = range();
      var str = stringValue("11");
      resolveExpr(new CompareExpression(CompareOperator.EQ, intValue("10"), str, range));

      assertErrorRange(str.range());
    }
  }

  @Nested
  class MemberAccessExpr {
    @Test
    void accessStruct() {
      var struct = literalStruct(List.of(new Field(id("foo"), intValue("10"))), List.of());
      var type = resolveExpr(new MemberAccess(struct, id("foo")));

      assertThat(type).isEqualTo(BuiltinType.INT);
    }

    @Test
    void accessStructWithoutField() {
      var struct = literalStruct(List.of(new Field(id("baz"), intValue("10"))), List.of());
      var access = new MemberAccess(struct, id("foo"));

      resolveExpr(access);

      assertErrorRange(access);
    }

    @Test
    void accessNonStruct() {
      var access = new MemberAccess(stringValue("bar"), id("foo"));

      resolveExpr(access);

      assertErrorRange(access);
    }
  }

  @Nested
  class ForeignFuncCall {

    @Test
    void constructor() {
      when(nameResolutionResult.getForeignFunction(any())).thenReturn(foreignConstructors(File.class));
      var call = new ForeignFunctionCall(typeId("File"),
          id("new"),
          args(stringValue("foo.txt")),
          range());

      var type = resolveExpr(call);

      assertThat(type).isEqualTo(new ClassType(File.class));
    }

    @Test
    void staticFunc() {
      when(nameResolutionResult.getForeignFunction(any())).thenReturn(foreignMethods(Paths.class, "get"));
      var call = new ForeignFunctionCall(typeId("Paths"),
          id("get"),
          args(stringValue("foo.txt")),
          range());

      var type = resolveExpr(call);

      assertThat(type).isEqualTo(new ClassType(Path.class));
    }

    @Test
    void virtualFunc() {
      when(nameResolutionResult.getForeignFunction(any())).thenReturn(foreignMethods(String.class,
          m -> m.getName().equals("concat")));
      var call = new ForeignFunctionCall(typeId("String"),
          id("concat"),
          args(stringValue("foo"), stringValue("bar")),
          range());

      var type = resolveExpr(call);

      assertThat(type).isEqualTo(BuiltinType.STRING);
    }

    @Test
    void constructorNotFound() {
      when(nameResolutionResult.getForeignFunction(any())).thenReturn(foreignConstructors(File.class));
      var call = new ForeignFunctionCall(typeId("File"),
          id("new"),
          args(intValue("45"), intValue("10")),
          range());

      resolveExpr(call);

      assertErrorRange(call);
    }

    @Test
    void staticFuncNotFound() {
      when(nameResolutionResult.getForeignFunction(any())).thenReturn(foreignMethods(Paths.class,
          m -> m.getParameterCount() == 2));
      var call = new ForeignFunctionCall(typeId("Paths"),
          id("get"),
          args(intValue("10"), ArrayValue.ofElementType(BuiltinType.STRING, List.of(), range())),
          range());

      resolveExpr(call);

      assertErrorRange(call);
    }

  }

  @Nested
  class FunctionCallTest {
    private final Id funcId = id("foo");
    private NamedFunction func;

    @BeforeEach
    void setUp() {
      func = new NamedFunction(funcId,
          new Function(new FunctionSignature(List.of(param("arg1", BuiltinType.STRING),
              param("arg2", BuiltinType.INT)),
              typeNode(BuiltinType.BOOLEAN),
              range()), boolValue(true)));
    }

    private FunctionParameter param(String name, BuiltinType type) {
      return new FunctionParameter(id(name), null, typeNode(type), null);
    }

//    @Test
//    void row() {
//      var
//      new StructTypeNode(
//          Map.of("str", typeNode(BuiltinType.STRING)),
//          new StructTypeNode.RowModifier.NamedRow(new BasicTypeNode<>(new ResolvedLocalNamedType("A", List.of(), )))
//          range());
//      func = new NamedFunction(funcId,
//          new Function(new FunctionSignature(List.of(param("arg1", BuiltinType.STRING)),
//              typeNode(BuiltinType.BOOLEAN),
//              range()), boolValue(true)));
//    }
//
    @Nested
    class Local {
      final Id funcId = id("foo");

      @BeforeEach
      void setUp() {
        memberScopedTypeResolver.checkType(func, func.functionSignature().type());
        when(moduleScopedTypes.getFunctionCallType(any())).thenReturn(new FuncCallType.Module());
        when(moduleScopedTypes.getThisType()).thenReturn(new StructType(
            QUAL_MOD_NAME.toQualifiedTypeName(),
            seqMap(funcId.name(), func.functionSignature().type())));

        when(nameResolutionResult.getLocalFunctionCallTarget(any())).thenReturn(new QualifiedFunction());
      }

      @Test
      void call() {
        var call = new LocalFunctionCall(id("foo"),
            args(stringValue("test"), intValue(10)),
            range());

        var type = resolveExpr(call);

        assertThat(type).isEqualTo(BuiltinType.BOOLEAN);
      }

      @Test
      void callBadArgCount() {
        var callRange = range();
        var call = new LocalFunctionCall(id("foo"), args(stringValue("test")), callRange);

        resolveExpr(call);

        assertErrorRange(callRange);
      }

      @Test
      void callBadArgType() {
        var badArg = stringValue("other");
        var call = new LocalFunctionCall(id("foo"), args(stringValue("test"), badArg), range());

        resolveExpr(call);

        assertErrorRange(badArg);
      }
    }

    @Nested
    class StructCall {
      private final PackageName packageName = new PackageName("base");
      private final List<Argument> args = args(stringValue("test"), intValue(1));

      @Test
      void call() {
        var qualifiedName = new QualifiedModuleName(packageName, "MyMod");
        var struct = Struct.moduleStructBuilder(qualifiedName)
            .functions(List.of(func))
            .range(range())
            .build();
        var call = new MemberFunctionCall(struct, id("foo"), args, range());

        var type = resolveExpr(call);

        assertThat(type).isEqualTo(BuiltinType.BOOLEAN);
      }

      @Test
      void callExprNotStruct() {
        var expr = intValue(10);
        var call = new MemberFunctionCall(expr, id("foo"), args, range());

        resolveExpr(call);

        assertErrorRange(expr);
      }

      @Test
      void callFieldNotFunction() {
        var struct = Struct.literalStruct(List.of(new Field(id("foo"), intValue(10))),
            List.of(),
            List.of(),
            range());
        var callFuncId = id("foo");
        var call = new MemberFunctionCall(struct, callFuncId, args, range());

        resolveExpr(call);

        assertErrorRange(callFuncId);
      }

      @Test
      void callNoField() {
        var qualifiedName = new QualifiedModuleName(packageName, "MyMod");
        var struct = Struct.moduleStructBuilder(qualifiedName)
            .functions(List.of(func))
            .range(range())
            .build();
        var call = new MemberFunctionCall(struct, id("bar"), args, range());

        resolveExpr(call);

        assertErrorRange(call);
      }
    }
  }

  @Nested
  class StructTypeTest {
    @Test
    void structWithExtraFieldsIsNotAssignable() {
      var argFields = List.of(new Field(id("foo"), stringValue("str")),
          new Field(id("bar"), intValue("10")),
          new Field(id("baz"), boolValue("true")));
      var struct = literalStruct(argFields, List.of());
      var argType = resolveExpr(struct);

      var paramType = StructType.synthetic(seqMap(
          name("foo"),
          BuiltinType.STRING,
          name("bar"),
          BuiltinType.INT));

      assertThat(paramType.isAssignableFrom(argType)).isFalse();
    }

    @Test
    void rowStructWithExtraFieldsIsAssignable() {
      var argFields = List.of(new Field(id("foo"), stringValue("str")),
          new Field(id("bar"), intValue("10")),
          new Field(id("baz"), boolValue("true")));
      var struct = literalStruct(argFields, List.of());
      var argType = resolveExpr(struct);

      var paramType = unnamed(seqMap(name("foo"), BuiltinType.STRING, name("bar"), BuiltinType.INT),
          RowModifier.unnamedRow());

      assertThat(paramType.isAssignableFrom(argType)).isTrue();
    }
  }

  private Type resolveExpr(Expression expr) {
    return memberScopedTypeResolver.infer(expr).type();
  }

  private void assertErrorRange(Node node) {
    assertErrorRange(node.range());
  }

  private void assertErrorRange(Range range) {
    shouldAssertErrorsEmpty = false;
    var error = memberScopedTypeResolver.errors().errors().get(0);
    assertThat(error.range()).isEqualTo(range);
  }
}
