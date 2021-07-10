package com.pentlander.sasquach.type;

public interface Type {
    String typeName();

    Class<?> typeClass();

    String descriptor();

    String internalName();

    default boolean isAssignableFrom(Type other) {
        return this.equals(other);
    }

    default String toPrettyString() {
        return typeName();
    }
}
