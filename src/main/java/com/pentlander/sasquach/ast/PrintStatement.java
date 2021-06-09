package com.pentlander.sasquach.ast;

public record PrintStatement(Expression expression) implements Expression {

    @Override
    public Type type() {
        return BuiltinType.VOID;
    }
}
