package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.type.Type;

import java.util.List;

public record ForeignFunctionCall(Identifier classAlias, Identifier functionName, List<Expression> arguments,
                                  String methodDescriptor,
                                  FunctionCallType functionCallType,
                                  Type returnType,
                                  String owner,
                                  Range range) implements Expression {
    public String name() {
        return functionName.name();
    }

    @Override
    public Type type() {
        return returnType;
    }

    public enum FunctionCallType {
        SPECIAL, STATIC, VIRTUAL
    }
}
