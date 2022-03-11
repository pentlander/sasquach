package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.Range;

import java.util.NoSuchElementException;

public sealed interface BinaryExpression extends Expression {
  Operator operator();
  Expression left();
  Expression right();

  @Override
  default String toPrettyString() {
    return left().toPrettyString() + " " + operator().literal() + " " + right().toPrettyString();
  }

  enum MathOperator implements Operator {
    PLUS("+"), MINUS("-"), TIMES("*"), DIVIDE("/");

    private final String literal;

    MathOperator(String literal) {
      this.literal = literal;
    }

    @Override
    public String literal() {
      return literal;
    }

    public static MathOperator fromString(String value) {
      return BinaryExpression.fromString(value, values());
    }
  }

  enum CompareOperator implements Operator {
    GE(">="), LE("<="), GT(">"), LT("<"), EQ("=="), NE("!=");

    private final String literal;

    CompareOperator(String literal) {
      this.literal = literal;
    }

    @Override
    public String literal() {
      return literal;
    }

    public static CompareOperator fromString(String value) {
      return BinaryExpression.fromString(value, values());
    }
  }

  enum BooleanOperator implements Operator {
    AND("&&"), OR("||");

    private final String literal;

    BooleanOperator(String literal) {
      this.literal = literal;
    }

    @Override
    public String literal() {
      return literal;
    }

    public static BooleanOperator fromString(String value) {
      return BinaryExpression.fromString(value, values());
    }
  }

  interface Operator {
    String literal();
  }

  private static <T extends Operator> T fromString(String value, T[] values) {
    for (var operator : values) {
      if (operator.literal().equals(value)) {
        return operator;
      }
    }
    throw new NoSuchElementException(value);
  }

  record MathExpression(MathOperator operator, Expression left, Expression right,
                        Range range) implements BinaryExpression {}

  record CompareExpression(CompareOperator operator, Expression left, Expression right,
                           Range range) implements BinaryExpression {}

  record BooleanExpression(BooleanOperator operator, Expression left, Expression right,
                           Range range) implements BinaryExpression {}
}
