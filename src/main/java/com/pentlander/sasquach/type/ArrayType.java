package com.pentlander.sasquach.type;

public record ArrayType(Type elementType) implements Type {
    @Override
    public String typeName() {
        return elementType.typeName() + "[]";
    }

    @Override
    public Class<?> typeClass() {
        return elementType.typeClass().arrayType();
    }

    @Override
    public String descriptor() {
        return "[" + elementType.descriptor();
    }

    @Override
    public String internalName() {
        return descriptor();
    }
}
