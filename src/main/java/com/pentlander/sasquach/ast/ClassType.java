package com.pentlander.sasquach.ast;

import java.lang.invoke.MethodHandles;

public record ClassType(String typeName) implements Type {
    @Override
    public Class<?> typeClass() {
        try {
            return MethodHandles.lookup().findClass(typeName.replace("/", "."));
        } catch (ClassNotFoundException | IllegalAccessException e) {
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
