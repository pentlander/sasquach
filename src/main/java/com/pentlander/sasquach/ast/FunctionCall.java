package com.pentlander.sasquach.ast;

import org.antlr.v4.runtime.misc.Nullable;

import java.util.List;

public record FunctionCall(FunctionSignature signature, List<Expression> arguments,
                           @Nullable Type owner) implements Expression {

    public String functionName() {
        return signature().name();
    }

    @Override
    public Type type() {
        return signature().returnType();
    }
}
