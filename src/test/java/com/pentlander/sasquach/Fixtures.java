package com.pentlander.sasquach;

import com.pentlander.sasquach.ast.*;
import com.pentlander.sasquach.type.BuiltinType;
import com.pentlander.sasquach.type.Type;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Fixtures {
    public static final String MOD_NAME = "Test";
    private static final AtomicInteger RANGE_COUNTER = new AtomicInteger();

    public static Range.Single range() {
        return new Range.Single(new Position(RANGE_COUNTER.getAndIncrement(), 1), 1);
    }

    public static Identifier id(String name) {
        return new Identifier(name, range());
    }

    public static Function func(Scope scope, String name, List<FunctionParameter> functionParameters,
                                Type returnType, Expression expression) {
        var function = new Function(
                scope,
                id(name),
                new FunctionSignature(functionParameters, new TypeNode(returnType, range()), range()),
                expression);
        scope.addFunction(function);
        return function;
    }

    public static Value intValue(String value) {
        return new Value(BuiltinType.INT, value, range());
    }

    public static Value boolValue(String value) {
        return new Value(BuiltinType.BOOLEAN, value, range());
    }

    public static Value stringValue(String value) {
        return new Value(BuiltinType.STRING, value, range());
    }
}
