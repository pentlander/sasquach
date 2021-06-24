package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.type.Type;

public record VariableDeclaration(Identifier id, Expression expression, int index, Range range) implements Expression {
    public String name() {
        return id.name();
    }

    public Range.Single nameRange() {
        return id.range();
    }

    @Override
    public Type type() {
        return expression.type();
    }
}
