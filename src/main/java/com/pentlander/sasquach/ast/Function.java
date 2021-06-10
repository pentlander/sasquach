package com.pentlander.sasquach.ast;

import org.antlr.v4.runtime.misc.Nullable;

import java.util.List;

public record Function(Scope scope, String name, Type returnType, List<FunctionParameter> arguments,
                       List<Expression> expressions, @Nullable Expression returnExpression) implements Expression {
    @Override
    public Type type() {
        return returnType;
    }
}
