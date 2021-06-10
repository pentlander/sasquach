package com.pentlander.sasquach.ast;

import java.util.Arrays;
import java.util.NoSuchElementException;

public interface BinaryExpression extends Expression {
    Expression left();
    Expression right();

    @Override
    default Type type() {
        System.out.println(this);
        return left().type();
    }

    enum Operator {
        PLUS("+"), MINUS("-"), ASTERISK("*"), DIVIDE("/");

        private final String literal;

        Operator(String literal) {
            this.literal = literal;
        }

        public static Operator fromString(String value) {
            for (Operator operator : values()) {
                if (operator.literal.equals(value)) {
                    return operator;
                }
            }
            throw new NoSuchElementException(value);
        }
    }

    record MathExpression(Operator operator, Expression left, Expression right) implements BinaryExpression {}

    record SumExpression(Expression left, Expression right) implements BinaryExpression {
    }

    record SubtractionExpression(Expression left, Expression right) implements BinaryExpression {
    }

    record MultiplicationExpression(Expression left, Expression right) implements BinaryExpression {
    }

    record DivisionExpression(Expression left, Expression right) implements BinaryExpression {
    }
}
