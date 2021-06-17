package com.pentlander.sasquach.ast;

import org.antlr.v4.runtime.misc.Nullable;

import java.util.List;

public record Block(Scope scope, List<Expression> expressions, @Nullable Expression returnExpression) implements Expression {
    @Override
    public Type type() {
        if (returnExpression != null) {
            return returnExpression.type();
        }
        return BuiltinType.VOID;
    }
}