package com.pentlander.sasquach.type;

import static java.util.Objects.requireNonNull;

import java.lang.constant.ClassDesc;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Represents a type parameter that hasn't been resolved yet. It is essentially a placeholder that
 * gets replaced by the {@link TypeUnifier}.
 */
public final class TypeVariable implements Type, ParameterizedType {
  private final String name;
  private InnerType inner = new InnerType();

  /**
   * @param name captureName of the type variable.
   */
  public TypeVariable(String name) {
    this.name = name;
  }

  public Optional<Type> resolvedType() {
    return Optional.ofNullable(inner.type);
  }

  public boolean resolveType(Type type) {
    // If both are type variables, they need to be unified
    if (type instanceof TypeVariable typeVar) {
      // If both are resolved, check that they're the same type
      if (inner.type != null && typeVar.inner.type != null) {
        return inner.type.equals(typeVar.inner.type);
      }

      // If only one or neither is resolved, they need to have the same inner
      if (inner.type != null) {
        typeVar.inner = inner;
      } else {
        inner = typeVar.inner;
      }
      return true;
    } else if (inner.type != null && !inner.type.isAssignableFrom(type)) {
      return false;
      // If the other type is also a type variable, the inner of the other needs to be used so that
      // when the other type variable is resolved to a concrete type, this type variable is also
      // resolved.
    } else {
      inner.type = type;
      return true;
    }
  }

  @Override
  public String typeNameStr() {
    return name;
  }

  @Override
  public ClassDesc classDesc() {
    return requireNonNull(inner.type).classDesc();
  }

  @Override
  public String internalName() {
    return requireNonNull(inner.type).internalName();
  }

  @Override
  public boolean isAssignableFrom(Type other) {
    return resolvedType().map(type -> type.isAssignableFrom(other)).orElse(true);
  }

  public String name() {
    return name;
  }

  @Override
  public boolean equals(Object obj) {
    return obj == this || obj instanceof TypeVariable other && Objects.equals(name, other.name)
        && Objects.equals(inner, other.inner);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, inner);
  }

  @Override
  public String toString() {
    return "TypeVariable[" + "captureName=" + name + ", inner=" + inner + ']';
  }

  private static class InnerType {
    @Nullable private Type type = null;

    @Override
    public boolean equals(Object o) {
      return this == o || o instanceof InnerType other && Objects.equals(type, other.type);
    }

    @Override
    public int hashCode() {
      return Objects.hash(type);
    }

    @Override
    public String toString() {
      return "Inner" + Integer.toHexString(System.identityHashCode(this)) + "[" + "type=" + type
          + "]";
    }
  }
}
