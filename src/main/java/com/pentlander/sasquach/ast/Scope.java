package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.type.Type;

import java.util.*;

public class Scope {
    public static final Scope NULL_SCOPE = topLevel(new Metadata("null"));
    private final Map<String, Identifier> identifiers = new HashMap<>();
    private final Map<String, Type> identifierTypes = new LinkedHashMap<>();
    private final List<Function> functions = new ArrayList<>();
    private final Map<String, Use> useAliases = new LinkedHashMap<>();
    private final Metadata metadata;
    private final Scope parentScope;

    private Scope(Metadata metadata, Scope parentScope) {
        this.metadata = metadata;
        this.parentScope = parentScope;
    }

    public static Scope forBlock(Scope parentScope) {
        var scope =  new Scope(parentScope.metadata, parentScope);
        scope.identifiers.putAll(parentScope.identifiers);
        scope.identifierTypes.putAll(parentScope.identifierTypes);
        scope.useAliases.putAll(parentScope.useAliases);
        return scope;
    }

    public static Scope topLevel(Metadata metadata) {
        return new Scope(metadata, null);
    }

    public void addFunction(Function function) {
        functions.add(function);
    }

    public void addIdentifier(Identifier identifier) {
        identifiers.put(identifier.name(), identifier);
    }

    public void addUse(Use use) {
        useAliases.put(use.alias().name(), use);
    }

    public Optional<Identifier> getIdentifier(String identifierName) {
        return Optional.ofNullable(identifiers.get(identifierName));
    }

    public Optional<Use> findUse(String useName) {
        return Optional.ofNullable(useAliases.get(useName));
    }

    public int findIdentifierIdx(String identifierName) {
        int i = 0;
        for (var name : identifiers.keySet()) {
            if (name.equals(identifierName)) {
                return i;
            }
            i++;
        }

        throw new NoSuchElementException(identifierName);
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
        return metadata.className();
    }
}
