package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.type.Type;

import java.util.*;

public class Scope {
    public static final Scope NULL_SCOPE = new Scope(new Metadata("null"));
    private final Map<String, Identifier> identifiers = new HashMap<>();
    private final Map<String, Type> identifierTypes = new LinkedHashMap<>();
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
        this.identifiers.putAll(parentScope.identifiers);
        this.identifierTypes.putAll(parentScope.identifierTypes);
        this.useAliases.putAll(parentScope.useAliases);
    }

    public Scope(Scope parentScope) {
        this(parentScope.metadata, parentScope);
    }

    public void addFunction(Function function) {
        functions.add(function);
    }

    public void addIdentifier(Identifier identifier, Type type) {
        identifiers.put(identifier.name(), identifier);
        identifierTypes.put(identifier.name(), type);
    }

    public void addUse(Use use) {
        useAliases.put(use.alias().name(), use);
    }

    public Optional<Type> findIdentifierType(String identifierName) {
        return Optional.ofNullable(identifierTypes.get(identifierName));
    }

    public Optional<Identifier> getIdentifier(String identifierName) {
        return Optional.ofNullable(identifiers.get(identifierName));
    }

    public Optional<Use> findUse(String useName) {
        System.out.println(useAliases);
        return Optional.ofNullable(useAliases.get(useName));
    }

    public int findIdentifierIdx(String identifierName) {
        int i = 0;
        for (var name : identifierTypes.keySet()) {
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
