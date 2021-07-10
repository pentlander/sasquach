package com.pentlander.sasquach.type;

import static java.util.stream.Collectors.joining;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Type of a function, including the parameter types, the type parameters, and return type.
 */
public record FunctionType(List<Type> parameterTypes, List<NamedType> typeParameters,
                           Type returnType) implements ParameterizedType {
  public FunctionType(List<Type> parameterTypes, Type returnType) {
    this(parameterTypes, List.of(), returnType);
  }

  @Override
  public String typeName() {
    return parameterTypes.stream().map(Type::typeName).collect(joining(", ", "(", "): "))
        + returnType.typeName();
  }

  @Override
  public Class<?> typeClass() {
    // TODO: Needed to implement higher order functions
    throw new IllegalStateException();
  }

  @Override
  public String descriptor() {
    String paramDescriptor = parameterTypes.stream().map(Type::descriptor)
        .collect(joining("", "(", ")"));
    return paramDescriptor + returnType.descriptor();
  }

  public String descriptorWith(int index, Type type) {
    var paramTypes = new ArrayList<>(parameterTypes());
    paramTypes.add(index, type);
    String paramDescriptor = paramTypes.stream().map(Type::descriptor)
        .collect(joining("", "(", ")"));
    return paramDescriptor + returnType.descriptor();
  }

  @Override
  public String internalName() {
    throw new IllegalStateException();
  }

  @Override
  public boolean isAssignableFrom(Type other) {
    if (other instanceof FunctionType funcType) {
      return reifyTypes(funcType).isPresent();
    }
    return false;
  }

  public Optional<Map<NamedType, Type>> reifyTypes(FunctionType other) {
    var paramTypes = new HashMap<NamedType, Type>();
    var paramCount = parameterTypes().size();
      if (paramCount != other.parameterTypes().size()) {
          return Optional.empty();
      }

    for (int i = 0; i < paramCount; i++) {
      var paramType = parameterTypes().get(i);
      var otherParamType = other.parameterTypes().get(i);
      if (paramType instanceof NamedType namedType) {
        paramType = paramTypes.computeIfAbsent(namedType, _k -> otherParamType);
      }
      if (!paramType.isAssignableFrom(otherParamType)) {
        return Optional.empty();
      }
    }
    return Optional.of(paramTypes);
  }
}
