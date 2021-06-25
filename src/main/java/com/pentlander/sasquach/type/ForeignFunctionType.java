package com.pentlander.sasquach.type;

import com.pentlander.sasquach.ast.ForeignFunctionCall;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.stream.Collectors;

public record ForeignFunctionType(MethodType methodType, ForeignFunctionCall.FunctionCallType callType) implements Type {
    @Override
    public String typeName() {
        return methodType.toString();
    }

    @Override
    public Class<?> typeClass() {
        throw new IllegalStateException();
    }

    @Override
    public String descriptor() {
        return methodType.descriptorString();
    }

    @Override
    public String internalName() {
        throw new IllegalStateException();
    }
}
