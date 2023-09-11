package com.pentlander.sasquach.type;

import static java.util.Objects.requireNonNullElse;
import static java.util.stream.Collectors.joining;

import com.pentlander.sasquach.runtime.StructBase;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Type of a function, including the parameter types, the type parameters, and return type.
 */
public record FunctionType(List<Type> parameterTypes, List<TypeParameter> typeParameters,
                           Type returnType) implements ParameterizedType {
  public FunctionType {
    parameterTypes = requireNonNullElse(parameterTypes, List.of());
    typeParameters = requireNonNullElse(typeParameters, List.of());
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
    return StructBase.class.descriptorString();
  }

  public String funcDescriptor() {
    String paramDescriptor = parameterTypes.stream()
        .map(Type::descriptor)
        .collect(joining("", "(", ")"));
    return paramDescriptor + returnType.descriptor();
  }

  public String funcDescriptorWith(int index, Type type) {
    var paramTypes = new ArrayList<>(parameterTypes());
    paramTypes.add(index, type);
    String paramDescriptor = paramTypes.stream()
        .map(Type::descriptor)
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

  @Override
  public String toPrettyString() {
    var typeParams = !typeParameters.isEmpty() ? typeParameters.stream()
        .map(TypeParameter::typeName)
        .collect(joining(", ", "[", "]")) : "";
    return typeParams + parameterTypes().stream()
        .map(Type::toPrettyString)
        .collect(joining(", ", "(", ")")) + " -> " + returnType.toPrettyString();
  }

  public Optional<Map<LocalNamedType, Type>> reifyTypes(FunctionType other) {
    var paramTypes = new HashMap<LocalNamedType, Type>();
    var paramCount = parameterTypes().size();
    if (paramCount != other.parameterTypes().size()) {
      return Optional.empty();
    }

    for (int i = 0; i < paramCount; i++) {
      var paramType = parameterTypes().get(i);
      var otherParamType = other.parameterTypes().get(i);
      if (paramType instanceof LocalNamedType localNamedType) {
        paramType = paramTypes.computeIfAbsent(localNamedType, _k -> otherParamType);
      }
      if (!paramType.isAssignableFrom(otherParamType)) {
        return Optional.empty();
      }
    }
    return Optional.of(paramTypes);
  }
}
