package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;
import org.antlr.v4.runtime.misc.Nullable;

import java.util.List;

public record FunctionCall(Identifier id, FunctionSignature signature, List<Expression> arguments,
                           @Nullable Type owner, Range range) implements Expression {
    public String name() {
        return id.name();
    }

    @Override
    public Type type() {
        return signature().returnType();
    }
}
