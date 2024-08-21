package com.pentlander.sasquach.type;

import static java.util.Objects.requireNonNullElse;
import static java.util.stream.Collectors.joining;

import com.pentlander.sasquach.runtime.Func;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Type of a function, including the parameter types, the type parameters, and return type.
 */
public record FunctionType(List<Type> parameterTypes, List<TypeParameter> typeParameters,
                           Type returnType) implements ParameterizedType, TypeNester {
  public FunctionType {
    parameterTypes = requireNonNullElse(parameterTypes, List.of());
    typeParameters = requireNonNullElse(typeParameters, List.of());
  }

  @Override
  public String typeNameStr() {
    return parameterTypes.stream().map(Type::typeNameStr).collect(joining(", ", "(", "): "))
        + returnType.typeNameStr();
  }

  @Override
  public ClassDesc classDesc() {
    return Func.class.describeConstable().orElseThrow();
  }

  public MethodTypeDesc functionTypeDesc() {
    return MethodTypeDesc.of(
        returnType.classDesc(),
        parameterTypes.stream().map(Type::classDesc).toArray(ClassDesc[]::new));
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
        .map(TypeParameter::name)
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
