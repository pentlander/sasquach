package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;

public record VariableDeclaration(String name, Expression expression, int index, Range.Single nameRange) implements Expression {
    @Override
    public Type type() {
        return expression.type();
    }

    public Identifier toIdentifier() {
        return new Identifier(name, type());
    }
}
