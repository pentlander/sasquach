package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;

public record PrintStatement(Expression expression, Range range) implements Expression {

    @Override
    public Type type() {
        return BuiltinType.VOID;
    }
}
