package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;

import java.util.List;

public record ForeignFunctionCall(String name, List<Expression> arguments, String methodDescriptor, FunctionCallType functionCallType,
                                  Type returnType,
                                  String owner,
                                  Range range) implements Expression {
    @Override
    public Type type() {
        return returnType;
    }

    public enum FunctionCallType {
        SPECIAL, STATIC, VIRTUAL
    }
}
