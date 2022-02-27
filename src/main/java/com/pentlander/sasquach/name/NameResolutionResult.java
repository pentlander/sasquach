package com.pentlander.sasquach.name;

import static java.util.Objects.requireNonNull;

import com.pentlander.sasquach.RangedErrorList;
import com.pentlander.sasquach.ast.NamedTypeDefintion;
import com.pentlander.sasquach.ast.TypeNode;
import com.pentlander.sasquach.ast.expression.ForeignFieldAccess;
import com.pentlander.sasquach.ast.expression.ForeignFunctionCall;
import com.pentlander.sasquach.ast.expression.LocalFunctionCall;
import com.pentlander.sasquach.ast.expression.LocalVariable;
import com.pentlander.sasquach.ast.expression.VarReference;
import com.pentlander.sasquach.name.MemberScopedNameResolver.QualifiedFunction;
import com.pentlander.sasquach.name.MemberScopedNameResolver.ReferenceDeclaration;
import com.pentlander.sasquach.type.Type;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;

public class NameResolutionResult {
  private static final NameResolutionResult EMPTY = new NameResolutionResult(Map.of(),
      Map.of(),
      Map.of(),
      Map.of(),
      Map.of(),
      Map.of(),
      new RangedErrorList(List.of()));

  private final Map<TypeNode<Type>, NamedTypeDefintion> typeAliases;
  private final Map<Type, NamedTypeDefintion> typeNameAliases;
  private final Map<ForeignFieldAccess, Field> foreignFieldAccesses;
  private final Map<ForeignFunctionCall, ForeignFunctions> foreignFunctions;
  private final Map<LocalFunctionCall, QualifiedFunction> localFunctionCalls;
  private final Map<VarReference, ReferenceDeclaration> varReferences;
  private final Map<LocalVariable, Integer> varIndexes;
  private final RangedErrorList errors;

  public NameResolutionResult(Map<TypeNode<Type>, NamedTypeDefintion> typeAliases,
      Map<ForeignFieldAccess, Field> foreignFieldAccesses,
      Map<ForeignFunctionCall, ForeignFunctions> foreignFunctions,
      Map<LocalFunctionCall, QualifiedFunction> localFunctionCalls,
      Map<VarReference, ReferenceDeclaration> varReferences, Map<LocalVariable, Integer> varIndexes,
      RangedErrorList errors) {
    this.typeAliases = typeAliases;
    this.typeNameAliases = typeNameAliases(typeAliases);
    this.foreignFieldAccesses = foreignFieldAccesses;
    this.foreignFunctions = foreignFunctions;
    this.localFunctionCalls = localFunctionCalls;
    this.varReferences = varReferences;
    this.varIndexes = varIndexes;
    this.errors = errors;
  }

  private static Map<Type, NamedTypeDefintion> typeNameAliases(
      Map<TypeNode<Type>, NamedTypeDefintion> typeAliases) {
    return typeAliases.entrySet().stream()
        .collect(Collectors.toMap(entry -> entry.getKey().type(), Entry::getValue, (a, b) -> a));
  }

  public NameResolutionResult withErrors(RangedErrorList errors) {
    return new NameResolutionResult(typeAliases, foreignFieldAccesses,
        foreignFunctions,
        localFunctionCalls,
        varReferences,
        varIndexes, this.errors.concat(errors));
  }

  public NameResolutionResult withNamedTypes(Map<TypeNode<Type>, NamedTypeDefintion> namedTypes) {
    var mergedNamedTypes = new HashMap<>(this.typeAliases);
    mergedNamedTypes.putAll(namedTypes);
    return new NameResolutionResult(mergedNamedTypes,
        foreignFieldAccesses,
        foreignFunctions,
        localFunctionCalls,
        varReferences,
        varIndexes,
        errors);
  }

  public static NameResolutionResult empty() {
    return EMPTY;
  }

  public Optional<NamedTypeDefintion> getNamedType(Type type) {
    return Optional.ofNullable(typeNameAliases.get(type));
  }

  public Field getForeignField(ForeignFieldAccess foreignFieldAccess) {
    return requireNonNull(foreignFieldAccesses.get(foreignFieldAccess));
  }

  public ForeignFunctions getForeignFunction(ForeignFunctionCall foreignFunctionCall) {
    return requireNonNull(foreignFunctions.get(foreignFunctionCall));
  }

  public QualifiedFunction getLocalFunction(LocalFunctionCall localFunctionCall) {
    return requireNonNull(localFunctionCalls.get(localFunctionCall));
  }

  public ReferenceDeclaration getVarReference(VarReference varReference) {
    return requireNonNull(varReferences.get(varReference));
  }

  public Integer getVarIndex(LocalVariable localVariable) {
    return requireNonNull(varIndexes.get(localVariable));
  }

  public RangedErrorList errors() {
    return errors;
  }

  public NameResolutionResult merge(NameResolutionResult other) {
    return new NameResolutionResult(merged(typeAliases, other.typeAliases),
        merged(foreignFieldAccesses, other.foreignFieldAccesses),
        merged(foreignFunctions, other.foreignFunctions),
        merged(localFunctionCalls, other.localFunctionCalls),
        merged(varReferences, other.varReferences),
        merged(varIndexes, other.varIndexes),
        errors.concat(other.errors));
  }

  private <K, V> Map<K, V> merged(Map<K, V> mapA, Map<K, V> mapB) {
    var map = new HashMap<>(mapA);
    map.putAll(mapB);
    return map;
  }
}
