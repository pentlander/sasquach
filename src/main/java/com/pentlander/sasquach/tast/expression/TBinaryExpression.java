package com.pentlander.sasquach.tast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.expression.BinaryExpression.BooleanOperator;
import com.pentlander.sasquach.ast.expression.BinaryExpression.CompareOperator;
import com.pentlander.sasquach.ast.expression.BinaryExpression.MathOperator;
import com.pentlander.sasquach.ast.expression.BinaryExpression.Operator;
import com.pentlander.sasquach.type.BuiltinType;
import com.pentlander.sasquach.type.Type;

public sealed interface TBinaryExpression extends TypedExpression {
  Operator operator();

  TypedExpression left();

  TypedExpression right();

  @Override
  default String toPrettyString() {
    return left().toPrettyString() + " " + operator().literal() + " " + right().toPrettyString();
  }

  record TMathExpression(MathOperator operator, TypedExpression left, TypedExpression right,
                        Range range) implements TBinaryExpression {
    @Override
    public Type type() {
      return left.type();
    }
  }

  record TCompareExpression(CompareOperator operator, TypedExpression left, TypedExpression right,
                           Range range) implements TBinaryExpression {
    @Override
    public Type type() {
      return BuiltinType.BOOLEAN;
    }
  }

  record TBooleanExpression(BooleanOperator operator, TypedExpression left, TypedExpression right,
                           Range range) implements TBinaryExpression {
    @Override
    public Type type() {
      return BuiltinType.BOOLEAN;
    }
  }
}
