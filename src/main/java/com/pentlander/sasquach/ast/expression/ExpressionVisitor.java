package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.ast.Node;
import com.pentlander.sasquach.ast.expression.Struct.Field;

public interface ExpressionVisitor<T> {
  T defaultValue();

  default T visit(Expression expr) {
    if (expr instanceof ArrayValue arrayValue) {
      return visit(arrayValue);
    } else if (expr instanceof BinaryExpression binExpr) {
      return visit(binExpr);
    } else if (expr instanceof Block block) {
      return visit(block);
    } else if (expr instanceof FieldAccess fieldAccess) {
      return visit(fieldAccess);
    } else if (expr instanceof Field field) {
      return visit(field);
    } else if (expr instanceof ForeignFieldAccess fieldAccess) {
      return visit(fieldAccess);
    } else if (expr instanceof FunctionCall funcCall) {
      return visit(funcCall);
    } else if (expr instanceof Function func) {
      return visit(func);
    } else if (expr instanceof IfExpression ifExpr) {
      return visit(ifExpr);
    } else if (expr instanceof PrintStatement printStatement) {
      return visit(printStatement);
    } else if (expr instanceof Struct struct) {
      return visit(struct);
    } else if (expr instanceof Value value) {
      return visit(value);
    } else if (expr instanceof VariableDeclaration variableDeclaration) {
      return visit(variableDeclaration);
    } else if (expr instanceof VarReference varReference) {
      return visit(varReference);
    } else {
      throw new IllegalStateException();
    }
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
    if (functionCall instanceof LocalFunctionCall localFuncCall) {
      return visit(localFuncCall);
    } else if (functionCall instanceof MemberFunctionCall memberFuncCall) {
      return visit(memberFuncCall);
    } else if (functionCall instanceof ForeignFunctionCall foreignFunctionCall) {
      return visit(foreignFunctionCall);
    } else {
      throw new IllegalStateException();
    }
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
