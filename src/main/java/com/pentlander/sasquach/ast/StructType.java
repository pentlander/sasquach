package com.pentlander.sasquach.ast;

public record StructType(String typeName) implements Type {
    @Override
    public Class<?> typeClass() {
        try {
            return Class.forName(typeName);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String descriptor() {
        return "L%s;".formatted(internalName());
    }

    @Override
    public String internalName() {
        return typeName.replace(".", "/");
    }
}
