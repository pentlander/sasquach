package com.pentlander.sasquach.type;

import java.util.List;

import static java.util.stream.Collectors.*;

public record FunctionType(String ownerName, List<Type> parameterTypes, Type returnType) implements Type {
    @Override
    public String typeName() {
        return parameterTypes.stream().map(Type::typeName).collect(joining(", ", "(" ,"): ")) + returnType.typeName();
    }

    @Override
    public Class<?> typeClass() {
        // TODO: Needed to implement higher order functions
        throw new IllegalStateException();
    }

    @Override
    public String descriptor() {
        String paramDescriptor = parameterTypes.stream()
                .map(Type::descriptor)
                .collect(joining("", "(", ")"));
        return paramDescriptor + returnType.descriptor();
    }

    @Override
    public String internalName() {
        throw new IllegalStateException();
    }

    public String internalOwnerName() {
        return ownerName().replace('.', '/');
    }
}
