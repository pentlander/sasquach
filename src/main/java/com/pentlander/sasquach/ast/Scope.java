package com.pentlander.sasquach.ast;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

public class Scope {
    public static final Scope NULL_SCOPE = new Scope(new Metadata("null"));
    private final List<Identifier> identifiers = new ArrayList<>();
    private final List<Function> functions = new ArrayList<>();
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
    }

    public Scope(Scope parentScope) {
        this(parentScope.metadata, parentScope);
    }

    public static Scope copyOf(Scope scope) {
        var newScope = new Scope(scope.metadata);
        newScope.identifiers.addAll(scope.identifiers);
        newScope.functions.addAll(scope.functions);
        return newScope;
    }

    public void addFunction(Function function) {
        functions.add(function);
    }

    public void addIdentifier(Identifier identifier) {
        identifiers.add(identifier);
    }

    public Identifier findIdentifier(String identifierName) {
        return identifiers.stream()
                .filter(id -> id.name().equals(identifierName))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException(identifierName));
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

    public List<Identifier> getIdentifiers() {
        return List.copyOf(identifiers);
    }
}
