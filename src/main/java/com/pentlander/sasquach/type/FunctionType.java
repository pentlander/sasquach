package com.pentlander.sasquach.type;

import static com.pentlander.sasquach.Util.mapNonNull;
import static java.util.Objects.requireNonNullElse;
import static java.util.stream.Collectors.joining;

import com.pentlander.sasquach.ast.Id;
import com.pentlander.sasquach.ast.Labeled;
import com.pentlander.sasquach.ast.UnqualifiedName;
import com.pentlander.sasquach.ast.expression.FunctionParameter;
import com.pentlander.sasquach.runtime.bootstrap.Func;
import com.pentlander.sasquach.tast.TFunctionParameter;
import com.pentlander.sasquach.tast.TFunctionParameter.Label.None;
import com.pentlander.sasquach.tast.TFunctionParameter.Label.Some;
import com.pentlander.sasquach.tast.TFunctionParameter.Label.WithDefault;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Type of a function, including the parameter types, the type parameters, and return type.
 */
public record FunctionType(List<Param> parameters,
                           List<TypeParameter> typeParameters, Type returnType) implements
    ParameterizedType, TypeNester {
  public FunctionType {
    parameters = requireNonNullElse(parameters, List.of());
    typeParameters = requireNonNullElse(typeParameters, List.of());
  }

  @Override
  public String typeNameStr() {
    return parameterTypes().stream().map(Type::typeNameStr).collect(joining(", ", "(", "): "))
        + returnType.typeNameStr();
  }

  @Override
  public ClassDesc classDesc() {
    return Func.class.describeConstable().orElseThrow();
  }

  public List<Type> parameterTypes() {
    return parameters().stream().map(Param::type).toList();
  }

  public MethodTypeDesc functionTypeDesc() {
    return MethodTypeDesc.of(
        returnType.classDesc(),
        parameterTypes().stream().map(Type::classDesc).toArray(ClassDesc[]::new));
  }

  @Override
  public String internalName() {
    return Func.class.getName().replace('.', '/');
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

  private Optional<Map<LocalNamedType, Type>> reifyTypes(FunctionType other) {
    var paramTypes = new HashMap<LocalNamedType, Type>();
    var paramCount = parameterTypes().size();
    if (paramCount != other.parameterTypes().size()) {
      return Optional.empty();
    }

    for (int i = 0; i < paramCount; i++) {
      var paramType = parameterTypes().get(i);
      var otherParamType = other.parameterTypes().get(i);
      if (paramType instanceof LocalNamedType localNamedType) {
        paramType = paramTypes.computeIfAbsent(localNamedType, _ -> otherParamType);
      }
      if (!paramType.isAssignableFrom(otherParamType)) {
        return Optional.empty();
      }
    }
    return Optional.of(paramTypes);
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof FunctionType that && parameters.equals(that.parameters)
        && returnType.equals(that.returnType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(parameterTypes(), returnType);
  }

  public record Param(Type type, @Nullable UnqualifiedName label, boolean hasDefault) implements
      Labeled {
    public Param(Type type) {
      this(type, null, false);
    }

    public static Param from(FunctionParameter funcParam) {
      var label = mapNonNull(funcParam.label(), Id::name);
      return new Param(funcParam.type(), label, funcParam.defaultExpr() != null);
    }

    public static Param from(TFunctionParameter funcParam) {
      var type = funcParam.type();
      return switch (funcParam.label()) {
        case None _ -> new FunctionType.Param(type);
        case Some(var label) -> new FunctionType.Param(type, label.name(), false);
        case WithDefault(var label, _) -> new FunctionType.Param(type, label.name(), true);
      };
    }

    public Param mapType(Function<Type, Type> mapper) {
      return new Param(mapper.apply(type), label, hasDefault);
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof Param param && type.equals(param.type);
    }

    @Override
    public int hashCode() {
      return Objects.hash(type);
    }
  }
}
