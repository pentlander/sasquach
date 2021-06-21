package com.pentlander.sasquach.ast;

import java.util.*;

public class Scope {
    public static final Scope NULL_SCOPE = new Scope(new Metadata("null"));
    private final List<Identifier> identifiers = new ArrayList<>();
    private final List<Function> functions = new ArrayList<>();
    private final Map<String, Use> useAliases = new LinkedHashMap<>();
    private final Metadata metadata;
    private final Scope parentScope;

    public Scope(Metadata metadata) {
        this.metadata = metadata;
        this.parentScope = null;
    }

    public Scope(Metadata metadata, Scope parentScope) {
        this.metadata = metadata;
        this.parentScope = parentScope;
        this.identifiers.addAll(parentScope.identifiers);
        this.useAliases.putAll(parentScope.useAliases);
    }

    public Scope(Scope parentScope) {
        this(parentScope.metadata, parentScope);
    }

    public void addFunction(Function function) {
        functions.add(function);
    }

    public void addIdentifier(Identifier identifier) {
        identifiers.add(identifier);
    }

    public void addUse(Use use) {
        useAliases.put(use.alias(), use);
    }

    public Optional<Identifier> findIdentifier(String identifierName) {
        return identifiers.stream()
                .filter(id -> id.name().equals(identifierName))
                .findFirst();
    }

    public Optional<Use> findUse(String useName) {
        System.out.println(useAliases);
        return Optional.ofNullable(useAliases.get(useName));
    }

    public int findIdentifierIdx(String identifierName) {
        for (int i = 0; i < identifiers.size(); i++) {
            if (identifiers.get(i).name().equals(identifierName)) {
                return i;
            }
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
