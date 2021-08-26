package com.pentlander.sasquach;

import com.pentlander.sasquach.ast.*;
import com.pentlander.sasquach.ast.expression.Expression;
import com.pentlander.sasquach.ast.expression.Function;
import com.pentlander.sasquach.ast.expression.FunctionParameter;
import com.pentlander.sasquach.ast.expression.Struct;
import com.pentlander.sasquach.ast.expression.Struct.Field;
import com.pentlander.sasquach.ast.expression.Value;
import com.pentlander.sasquach.name.ForeignFunctionHandle;
import com.pentlander.sasquach.name.ForeignFunctions;
import com.pentlander.sasquach.type.BuiltinType;
import com.pentlander.sasquach.type.Type;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

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
        return new QualifiedIdentifier(name, range());
    }

    public static Function func(String name, List<FunctionParameter> functionParameters, Type returnType, Expression expression) {
        return func(name, functionParameters, List.of(), returnType, expression);
    }

    public static Function func(String name, List<FunctionParameter> functionParameters, List<Type> typeParameters, Type returnType,
        Expression expression) {
        return new Function(
            id(name),
            new FunctionSignature(functionParameters,
            typeParameters.stream().map(t -> new TypeNode(t, range())).toList(),
            new TypeNode(returnType, range()),
            range()), expression);
    }

    public static Function voidFunc(String name, Expression expression) {
        return func(name, List.of(), BuiltinType.VOID, expression);
    }

    public static Struct literalStruct(List<Field> fields, List<Function> functions) {
        return Struct
            .literalStruct(fields, functions, range());
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

  public static ForeignFunctions foreignMethods(Class<?> clazz, Predicate<Method> methodPredicate) {
    return new ForeignFunctions(clazz, Arrays.stream(clazz.getMethods()).filter(methodPredicate).map(m -> {
      try {
        boolean isStatic = Modifier.isStatic(m.getModifiers());
        return new ForeignFunctionHandle(
            MethodHandles.lookup().unreflect(m),
            isStatic ? InvocationKind.STATIC : InvocationKind.VIRTUAL);
      } catch (IllegalAccessException e) {
        throw new IllegalStateException(e);
      }
    }).toList());
  }

  public static ForeignFunctions foreignMethods(Class<?> clazz) {
    return foreignMethods(clazz, m -> true);
  }

  public static ForeignFunctions foreignConstructors(Class<?> clazz) {
    return new ForeignFunctions(clazz, Arrays.stream(clazz.getConstructors()).map(c -> {
      try {
        return new ForeignFunctionHandle(
            MethodHandles.lookup().unreflectConstructor(c),
            InvocationKind.SPECIAL);
      } catch (IllegalAccessException e) {
        throw new IllegalStateException(e);
      }
    }).toList());
  }
}
