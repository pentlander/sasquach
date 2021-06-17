package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;
import org.antlr.v4.runtime.misc.Nullable;

import java.util.List;

public record FunctionCall(String name, FunctionSignature signature, List<Expression> arguments,
                           @Nullable Type owner, Range range) implements Expression {

    public String functionName() {
        return name;
    }

    @Override
    public Type type() {
        return signature().returnType();
    }
}
