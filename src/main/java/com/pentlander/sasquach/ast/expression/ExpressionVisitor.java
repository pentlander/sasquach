package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.ast.Node;
import com.pentlander.sasquach.ast.expression.Struct.Field;

public interface ExpressionVisitor<T> {
  T defaultValue();

  default T visit(Expression expr) {
    return switch (expr) {
      case ArrayValue arrayValue -> visit(arrayValue);
      case BinaryExpression binExpr -> visit(binExpr);
      case Block block -> visit(block);
      case FieldAccess fieldAccess -> visit(fieldAccess);
      case Field field -> visit(field);
      case ForeignFieldAccess fieldAccess -> visit(fieldAccess);
      case FunctionCall funcCall -> visit(funcCall);
      case Function func -> visit(func);
      case IfExpression ifExpr -> visit(ifExpr);
      case PrintStatement printStatement -> visit(printStatement);
      case Struct struct -> visit(struct);
      case Value value -> visit(value);
      case VariableDeclaration variableDeclaration -> visit(variableDeclaration);
      case VarReference varReference -> visit(varReference);
    };
  }

  default T visit(ArrayValue arrayValue) {
    arrayValue.expressions().forEach(this::visit);
    return defaultValue();
  }

  default T visit(BinaryExpression binaryExpression) {
    var left = visit(binaryExpression.left());
    visit(binaryExpression.right());
    return left;
  }

  default T visit(Block block) {
    for (int i = 0; i < block.expressions().size(); i++) {
      var value = visit(block.expressions().get(i));
      if (i == block.expressions().size() - 1) {
        return value;
      }
    }
    return defaultValue();
  }

  default T visit(Field field) {
    return visit(field.value());
  }

  default T visit(FieldAccess fieldAccess) {
    return visit(fieldAccess.expr());
  }

  T visit(ForeignFieldAccess fieldAccess);

  default T visit(ForeignFunctionCall funcCall) {
    funcCall.arguments().forEach(this::visit);
    return defaultValue();
  }

  default T visit(Function function) {
    visit(function.functionSignature());
    function.parameters().forEach(this::visit);
    return visit(function.expression());
  }

  default T visit(FunctionCall functionCall) {
    functionCall.arguments().forEach(this::visit);
    return switch (functionCall) {
      case LocalFunctionCall localFuncCall -> visit(localFuncCall);
      case MemberFunctionCall memberFuncCall -> visit(memberFuncCall);
      case ForeignFunctionCall foreignFunctionCall -> visit(foreignFunctionCall);
    };
  }

  default T visit(IfExpression ifExpression) {
    visit(ifExpression.condition());
    visit(ifExpression.trueExpression());
    visit(ifExpression.falseExpression());
    return defaultValue();
  }

  T visit(LocalFunctionCall localFunctionCall);

  default T visit(MemberFunctionCall memberFunctionCall) {
    visit(memberFunctionCall.structExpression());
    return defaultValue();
  }

  default T visit(PrintStatement printStatement) {
    return visit(printStatement.expression());
  }

  default T visit(Struct struct) {
    struct.useList().forEach(this::visit);
    struct.functions().forEach(this::visit);
    struct.fields().forEach(this::visit);
    return defaultValue();
  }

  default T visit(Value value) {
    return defaultValue();
  }

  default T visit(VariableDeclaration variableDeclaration) {
    return visit(variableDeclaration.expression());
  }

  default T visit(VarReference varReference) {
    return defaultValue();
  }

  T visit(Node node);
}
