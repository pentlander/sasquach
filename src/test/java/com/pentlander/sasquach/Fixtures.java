package com.pentlander.sasquach;

import com.pentlander.sasquach.ast.*;
import com.pentlander.sasquach.ast.expression.Expression;
import com.pentlander.sasquach.ast.expression.Function;
import com.pentlander.sasquach.ast.expression.Struct;
import com.pentlander.sasquach.ast.expression.Struct.Field;
import com.pentlander.sasquach.ast.expression.Value;
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

    public static QualifiedIdentifier qualId(String name) {
        if (name.contains(".")) {
            throw new IllegalStateException("Qualified name separated by '/' not '.': " + name);
        }
        return new QualifiedIdentifier(name, range());
    }

    public static Function func(Scope scope, String name,
        List<FunctionParameter> functionParameters, Type returnType, Expression expression) {
        return func(scope, name, functionParameters, List.of(), returnType, expression);
    }

    public static Function func(Scope scope, String name,
        List<FunctionParameter> functionParameters, List<Type> typeParameters, Type returnType,
        Expression expression) {
        var function = new Function(scope, id(name),
            new FunctionSignature(functionParameters,
            typeParameters.stream().map(t -> new TypeNode(t, range())).toList(),
            new TypeNode(returnType, range()),
            range()), expression);
        scope.addFunction(function);
        return function;
    }

    public static Struct literalStruct(Scope parentScope, List<Field> fields,
        List<Function> functions) {
        return Struct
            .literalStruct(Scope.forStructLiteral(parentScope), fields, functions, range());
    }

    public static Value intValue(String value) {
        return new Value(BuiltinType.INT, value, range());
    }

    public static Value intValue(int value) {
        return new Value(BuiltinType.INT, String.valueOf(value), range());
    }

    public static Value boolValue(String value) {
        return new Value(BuiltinType.BOOLEAN, value, range());
    }

    public static Value boolValue(boolean value) {
        return new Value(BuiltinType.BOOLEAN, String.valueOf(value), range());
    }

    public static Value stringValue(String value) {
        return new Value(BuiltinType.STRING, value, range());
    }

    public static TypeNode typeNode(Type type) {
        return new TypeNode(type, range());
    }
}
