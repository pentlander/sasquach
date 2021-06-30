package com.pentlander.sasquach.type;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.*;
import com.pentlander.sasquach.ast.BinaryExpression.CompareExpression;
import com.pentlander.sasquach.ast.BinaryExpression.CompareOperator;
import com.pentlander.sasquach.ast.BinaryExpression.MathExpression;
import com.pentlander.sasquach.ast.BinaryExpression.MathOperator;
import com.pentlander.sasquach.ast.Struct.Field;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.pentlander.sasquach.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class TypeResolverTest {
  private static final String MOD_NAME = "Test";
  TypeResolver typeResolver;
  Scope scope;
  boolean shouldAssertErrorsEmpty = true;

  @BeforeEach
  void setUp() {
    typeResolver = new TypeResolver();
    scope = Scope.topLevel(new Metadata(MOD_NAME));
    shouldAssertErrorsEmpty = true;
  }

  @AfterEach
  void tearDown() {
    if (shouldAssertErrorsEmpty) {
      assertThat(typeResolver.getErrors()).isEmpty();
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

    var declType = resolveExpr(new VariableDeclaration(varId, stringValue("hi"), 0, range()));
    scope.addIdentifier(varId);
    var refType = resolveExpr(new VarReference(id("foo")));

    assertThat(declType).isEqualTo(BuiltinType.VOID);
    assertThat(refType).isEqualTo(BuiltinType.STRING);
  }

  @Nested
  class BinaryExpression {

    @Test
    void compare() {
      var type = resolveExpr(
          new CompareExpression(CompareOperator.EQ, intValue("10"), intValue("11"), range()));

      assertThat(type).isEqualTo(BuiltinType.BOOLEAN);
    }

    @Test
    void math() {
      var type = resolveExpr(
          new MathExpression(MathOperator.PLUS, intValue("1"), intValue("2"), range()));

      assertThat(type).isEqualTo(BuiltinType.INT);
    }

    @Test
    void typeMismatch() {
      var range = range();
      resolveExpr(
          new CompareExpression(CompareOperator.EQ, intValue("10"), stringValue("11"), range));

      assertErrorRange(range);
    }
  }

  @Nested
  class FieldAccessExpr {
    @Test
    void accessStruct() {
      var struct = Struct.literalStruct(scope, List.of(new Field(id("foo"), intValue("10"))),
          List.of(), range());
      var type = resolveExpr(new FieldAccess(struct, id("foo")));

      assertThat(type).isEqualTo(BuiltinType.INT);
    }

    @Test
    void accessStructWithoutField() {
      var struct = Struct.literalStruct(scope, List.of(new Field(id("baz"), intValue("10"))),
          List.of(), range());
      var access = new FieldAccess(struct, id("foo"));

      resolveExpr(access);

      assertErrorRange(access);
    }

    @Test
    void accessNonStruct() {
      var access = new FieldAccess(stringValue("bar"), id("foo"));

      resolveExpr(access);

      assertErrorRange(access);
    }
  }

  @Nested
  class ForeignFuncCall {
    @Test
    void constructor() {
      scope.addUse(new Use.Foreign("java.io.File", id("File"), range()));
      var call = new ForeignFunctionCall(id("File"), id("new"), List.of(stringValue("foo.txt")),
          range());

      var type = resolveExpr(call);

      assertThat(type).isEqualTo(new ClassType(File.class));
    }

    @Test
    void staticFunc() {
      scope.addUse(new Use.Foreign("java.nio.file.Paths", id("Paths"), range()));
      var call = new ForeignFunctionCall(id("Paths"), id("get"),
          List.of(stringValue("foo.txt"),
              ArrayValue.ofElementType(BuiltinType.STRING, List.of(), range())), range());

      var type = resolveExpr(call);

      assertThat(type).isEqualTo(new ClassType(Path.class));
    }

    @Test
    void virtualFunc() {
      scope.addUse(new Use.Foreign("java.lang.String", id("String"), range()));
      var call = new ForeignFunctionCall(id("String"), id("concat"),
          List.of(stringValue("foo"), stringValue("bar")), range());

      var type = resolveExpr(call);

      assertThat(type).isEqualTo(BuiltinType.STRING);
    }

    @Test
    void constructorNotFound() {
      scope.addUse(new Use.Foreign("java.io.File", id("File"), range()));
      var call = new ForeignFunctionCall(id("File"), id("new"),
          List.of(intValue("45"), intValue("10")), range());

      resolveExpr(call);

      assertErrorRange(call);
    }

    @Test
    void staticFuncNotFound() {
      scope.addUse(new Use.Foreign("java.nio.file.Paths", id("Paths"), range()));
      var call = new ForeignFunctionCall(id("Paths"), id("get"), List.of(intValue("10"),
          ArrayValue.ofElementType(BuiltinType.STRING, List.of(), range())), range());

      resolveExpr(call);

      assertErrorRange(call);
    }

  }

  @Nested
  class StructTypeTest {
    @Test
    void structWithExtraFieldsIsAssignable() {
      var argFields = List
          .of(new Field(id("foo"), stringValue("str")), new Field(id("bar"), intValue("10")),
              new Field(id("baz"), boolValue("true")));
      var struct = Struct.literalStruct(scope, argFields, List.of(), range());
      var argType = resolveExpr(struct);

      var paramType = new StructType(Map.of("foo", BuiltinType.STRING, "bar", BuiltinType.INT));

      assertThat(argType.isAssignableTo(paramType)).isTrue();
    }
  }

  private Type resolveExpr(Expression expr) {
    return typeResolver.resolveExprType(expr, scope);
  }

  private void assertErrorRange(Node node) {
    assertErrorRange(node.range());
  }

  private void assertErrorRange(Range range) {
    shouldAssertErrorsEmpty = false;
    assertThat(typeResolver.getErrors().get(0).range()).isEqualTo(range);
  }
}