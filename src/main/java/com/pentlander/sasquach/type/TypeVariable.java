package com.pentlander.sasquach.type;

import static java.util.Objects.requireNonNull;

import java.lang.constant.ClassDesc;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Represents a type parameter that hasn't been resolved yet. It is essentially a placeholder that
 * gets replaced by the {@link TypeUnifier}.
 */
public final class TypeVariable implements Type, TypeNester {
  private final String name;
  private final int level;
  private final @Nullable Object context;
  private final UUID uuid;
  private InnerType inner = new InnerType();

  private StackTraceElement[] stackTrace;

  /**
   * @param name captureName of the type variable.
   */
  public TypeVariable(String name, int level, @Nullable Object context) {
    this.name = name;
    this.level = level;
    this.context = context;
    stackTrace = Thread.currentThread().getStackTrace();
    uuid = UUID.nameUUIDFromBytes((name + level + context).getBytes());
  }

  public TypeVariable(String name, int level) {
    this(name, level, null);
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

      // If only one or neither is resolved, they need to have the same inner. The order matters
      // here, if the other (which appears later in the code) already has a concrete type and this
      // does not, then update the inner of this. Otherwise, set the other's inner to the inner of
      // this regardless of whether it has a value since it was already unified with earlier type
      // variables.
      if (typeVar.inner.type != null) {
        inner = typeVar.inner;
        stackTrace = Thread.currentThread().getStackTrace();
      } else {
        typeVar.inner = inner;
      }
      return true;
    } else if (inner.type != null && !inner.type.isAssignableFrom(type)) {
      return false;
      // If the other type is also a type variable, the inner of the other needs to be used so that
      // when the other type variable is resolved to a concrete type, this type variable is also
      // resolved.
    } else {
      inner.type = type;
      stackTrace = Thread.currentThread().getStackTrace();
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

  @Override
  public boolean equals(Object obj) {
    return obj == this || obj instanceof TypeVariable other
        && name.equals(other.name)
        && level == other.level
        && inner.equals(other.inner);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, level, inner);
  }

  @Override
  public String toString() {
    return "TypeVariable[" + "name=" + name + level + ", inner=" + inner + ']';
  }

  @Override
  public String toPrettyString() {
    var innerStr = inner.type != null ? inner.type.toPrettyString() : "null";
    return name + "[" + innerStr + "]";
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
