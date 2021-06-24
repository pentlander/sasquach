package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;

import java.util.List;

public record ForeignFunctionCall(Identifier id, List<Expression> arguments, String methodDescriptor,
                                  FunctionCallType functionCallType,
                                  Type returnType,
                                  String owner,
                                  Range range) implements Expression {
    public String name() {
        return id.name();
    }

    @Override
    public Type type() {
        return returnType;
    }

    public enum FunctionCallType {
        SPECIAL, STATIC, VIRTUAL
    }
}
