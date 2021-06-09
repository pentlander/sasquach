package com.pentlander.sasquach.ast;

public record Identifier(String name, Expression expression) implements Expression {
    @Override
    public Type type() {
        return expression.type();
    }
}
