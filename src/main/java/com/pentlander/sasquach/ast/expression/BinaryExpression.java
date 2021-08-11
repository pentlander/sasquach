package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.Range;

import java.util.NoSuchElementException;

public sealed interface BinaryExpression extends Expression {
  Expression left();

  Expression right();

  enum MathOperator {
    PLUS("+"), MINUS("-"), TIMES("*"), DIVIDE("/");

    private final String literal;

    MathOperator(String literal) {
      this.literal = literal;
    }

    public static MathOperator fromString(String value) {
      for (MathOperator mathOperator : values()) {
        if (mathOperator.literal.equals(value)) {
          return mathOperator;
        }
      }
      throw new NoSuchElementException(value);
    }
  }

  enum CompareOperator {
    GE(">="), LE("<="), GT(">"), LT("<"), EQ("=="), NE("!=");

    private final String literal;

    CompareOperator(String literal) {
      this.literal = literal;
    }

    public static CompareOperator fromString(String value) {
      for (var operator : values()) {
        if (operator.literal.equals(value)) {
          return operator;
        }
      }
      throw new NoSuchElementException(value);
    }
  }

  record MathExpression(MathOperator operator, Expression left, Expression right,
                        Range range) implements BinaryExpression {}

  record CompareExpression(CompareOperator compareOperator, Expression left, Expression right,
                           Range range) implements BinaryExpression {}
}
