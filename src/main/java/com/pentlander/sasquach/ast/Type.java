package com.pentlander.sasquach.ast;

public interface Type {
    String typeName();

    Class<?> typeClass();

    String descriptor();

    String internalName();
}
