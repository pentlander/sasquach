package com.pentlander.sasquach.ast;

import java.util.List;

public record Function(Scope scope, String name, Type returnType, List<FunctionParameter> arguments,
                       List<Expression> expressions) implements Expression {
    @Override
    public Type type() {
        return returnType;
    }
}
