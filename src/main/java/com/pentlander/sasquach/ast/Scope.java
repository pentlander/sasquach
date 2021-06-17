package com.pentlander.sasquach.ast;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

public class Scope {
    private final List<Identifier> identifiers = new ArrayList<>();
    private final List<FunctionSignature> functionSignatures = new ArrayList<>();
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
        newScope.functionSignatures.addAll(scope.functionSignatures);
        return newScope;
    }

    public void addSignature(FunctionSignature signature) {
        functionSignatures.add(signature);
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

    public FunctionSignature findFunctionSignature(String functionName) {
        return Stream.concat(functionSignatures.stream(), parentScope.functionSignatures.stream()).filter(signature -> signature.name().equals(functionName)).findFirst().orElseThrow();
    }

    public String getClassName() {
        return metadata.className();
    }

    public List<Identifier> getIdentifiers() {
        return List.copyOf(identifiers);
    }
}
