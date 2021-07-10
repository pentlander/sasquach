package com.pentlander.sasquach.ast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

public class Scope implements ScopeReader, ScopeWriter {
    public static final Scope NULL_SCOPE = topLevel(new Metadata("null"));
    private final Map<String, Identifier> localIdentifiers = new LinkedHashMap<>();
    private final List<Function> functions = new ArrayList<>();
    private final Map<String, Use> useAliases = new LinkedHashMap<>();
    private final Map<String, TypeNode> namedTypes = new LinkedHashMap<>();
    private Metadata metadata;
    private final Scope parentScope;

    private Scope(Metadata metadata, Scope parentScope) {
        this.metadata = metadata;
        this.parentScope = parentScope;
    }

    public static Scope forBlock(Scope parentScope) {
        var scope =  new Scope(parentScope.metadata, parentScope);
        scope.localIdentifiers.putAll(parentScope.localIdentifiers);
        scope.useAliases.putAll(parentScope.useAliases);
        scope.namedTypes.putAll(parentScope.namedTypes);
        return scope;
    }

    public static Scope forStructLiteral(Scope parentScope) {
        var scope =  new Scope(null, parentScope);
        scope.localIdentifiers.putAll(parentScope.localIdentifiers);
        scope.useAliases.putAll(parentScope.useAliases);
        scope.namedTypes.putAll(parentScope.namedTypes);
        return scope;
    }

    public static Scope forStructType(Scope parentScope) {
        var scope =  new Scope(null, null);
        scope.useAliases.putAll(parentScope.useAliases);
        scope.namedTypes.putAll(parentScope.namedTypes);
        return scope;
    }

    public static Scope topLevel(Metadata metadata) {
        return new Scope(metadata, null);
    }

    public void setMetadata(Metadata metadata) {
        if (this.metadata != null) {
            throw new IllegalStateException("Failed to set metadata: " + metadata);
        }
        this.metadata = metadata;
    }

    public void addFunction(Function function) {
        functions.add(function);
    }

    public void addLocalIdentifier(Identifier identifier) {
        localIdentifiers.put(identifier.name(), identifier);
    }

    public void addUse(Use use) {
        useAliases.put(use.alias().name(), use);
    }

    public void addNamedType(String name, TypeNode typeNode) {
        namedTypes.put(name, typeNode);
    }

    public Optional<Identifier> getLocalIdentifier(String identifierName) {
        return Optional.ofNullable(localIdentifiers.get(identifierName));
    }

    public Optional<Use> findUse(String useName) {
        return Optional.ofNullable(useAliases.get(useName));
    }

    public Optional<TypeNode> getNamedType(String typeName) {
        return Optional.ofNullable(namedTypes.get(typeName));
    }

    public OptionalInt findLocalIdentifierIdx(String identifierName) {
        int i = 0;
        for (var name : localIdentifiers.keySet()) {
            if (name.equals(identifierName)) {
                return OptionalInt.of(i);
            }
            i++;
        }

        return OptionalInt.empty();
    }

    public Function findFunction(String functionName) {
        var function = functions.stream().filter(func -> func.name().equals(functionName)).findFirst();
        if (function.isPresent()) {
            return function.get();
        } else if (parentScope != null) {
            return parentScope.findFunction(functionName);
        }
        throw new NoSuchElementException(functionName + " " + functions);
    }

    public String getClassName() {
      return Objects.requireNonNull(metadata.className());
//        return metadata!= null ? metadata.className() : null;
    }
}
