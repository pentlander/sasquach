package com.pentlander.sasquach.type;

public interface Type {
    String typeName();

    Class<?> typeClass();

    String descriptor();

    String internalName();
}
