package com.pentlander.sasquach.ast;

public record VariableDeclaration(String name, Expression expression, int index) implements Expression {
    @Override
    public Type type() {
        return expression.type();
    }
}
