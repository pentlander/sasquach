package com.pentlander.sasquach.name;

import static java.util.Objects.requireNonNull;

import com.pentlander.sasquach.RangedErrorList;
import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.NamedTypeDefinition;
import com.pentlander.sasquach.ast.RecurPoint;
import com.pentlander.sasquach.ast.TypeNode;
import com.pentlander.sasquach.ast.expression.ForeignFieldAccess;
import com.pentlander.sasquach.ast.expression.ForeignFunctionCall;
import com.pentlander.sasquach.ast.expression.LocalFunctionCall;
import com.pentlander.sasquach.ast.expression.LocalVariable;
import com.pentlander.sasquach.ast.expression.Recur;
import com.pentlander.sasquach.ast.expression.VarReference;
import com.pentlander.sasquach.name.MemberScopedNameResolver.FunctionCallTarget;
import com.pentlander.sasquach.name.MemberScopedNameResolver.ReferenceDeclaration;
import com.pentlander.sasquach.type.Type;
import java.lang.reflect.Field;
import java.util.Collection;
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
      Map.of(),
      new RangedErrorList(List.of()));

  private final Map<TypeNode, NamedTypeDefinition> typeAliases;
  private final Map<Type, NamedTypeDefinition> typeNameAliases;
  private final Map<ForeignFieldAccess, Field> foreignFieldAccesses;
  private final Map<Identifier, ForeignFunctions> foreignFunctions;
  private final Map<Identifier, FunctionCallTarget> localFunctionCalls;
  private final Map<VarReference, ReferenceDeclaration> varReferences;
  private final Map<LocalVariable, Integer> varIndexes;
  private final Map<Recur, RecurPoint> recurPoints;
  private final RangedErrorList errors;

  public NameResolutionResult(Map<TypeNode, NamedTypeDefinition> typeAliases,
      Map<ForeignFieldAccess, Field> foreignFieldAccesses,
      Map<Identifier, ForeignFunctions> foreignFunctions,
      Map<Identifier, FunctionCallTarget> localFunctionCalls,
      Map<VarReference, ReferenceDeclaration> varReferences,
      Map<LocalVariable, Integer> varIndexes, Map<Recur, RecurPoint> recurPoints,
      RangedErrorList errors) {
    this.typeAliases = typeAliases;
    this.typeNameAliases = typeNameAliases(typeAliases);
    this.foreignFieldAccesses = foreignFieldAccesses;
    this.foreignFunctions = foreignFunctions;
    this.localFunctionCalls = localFunctionCalls;
    this.varReferences = varReferences;
    this.varIndexes = varIndexes;
    this.recurPoints = recurPoints;
    this.errors = errors;
  }

  private static Map<Type, NamedTypeDefinition> typeNameAliases(
      Map<TypeNode, NamedTypeDefinition> typeAliases) {
    return typeAliases.entrySet().stream()
        .collect(Collectors.toMap(entry -> entry.getKey().type(), Entry::getValue, (a, b) -> a));
  }

  public NameResolutionResult withErrors(RangedErrorList errors) {
    return new NameResolutionResult(typeAliases, foreignFieldAccesses,
        foreignFunctions,
        localFunctionCalls,
        varReferences,
        varIndexes, recurPoints, this.errors.concat(errors));
  }

  public NameResolutionResult withNamedTypes(Map<TypeNode, NamedTypeDefinition> namedTypes) {
    var mergedNamedTypes = new HashMap<>(this.typeAliases);
    mergedNamedTypes.putAll(namedTypes);
    return new NameResolutionResult(mergedNamedTypes,
        foreignFieldAccesses,
        foreignFunctions,
        localFunctionCalls,
        varReferences,
        varIndexes,
        recurPoints,
        errors);
  }

  public NameResolutionResult withf(Map<TypeNode, NamedTypeDefinition> namedTypes) {
    var mergedNamedTypes = new HashMap<>(this.typeAliases);
    mergedNamedTypes.putAll(namedTypes);
    return new NameResolutionResult(mergedNamedTypes,
        foreignFieldAccesses,
        foreignFunctions,
        localFunctionCalls,
        varReferences,
        varIndexes,
        recurPoints,
        errors);
  }

  public static NameResolutionResult empty() {
    return EMPTY;
  }

  public Optional<NamedTypeDefinition> getNamedType(Type type) {
    return Optional.ofNullable(typeNameAliases.get(type));
  }

  public Field getForeignField(ForeignFieldAccess foreignFieldAccess) {
    return requireNonNull(foreignFieldAccesses.get(foreignFieldAccess));
  }

  public ForeignFunctions getForeignFunction(ForeignFunctionCall foreignFunctionCall) {
    return requireNonNull(foreignFunctions.get(foreignFunctionCall.functionId()));
  }

  public FunctionCallTarget getLocalFunction(LocalFunctionCall localFunctionCall) {
    return requireNonNull(localFunctionCalls.get(localFunctionCall.functionId()), localFunctionCall.toString());
  }

  public ReferenceDeclaration getVarReference(VarReference varReference) {
    return requireNonNull(varReferences.get(varReference), varReference.toString());
  }

  public Integer getVarIndex(LocalVariable localVariable) {
    return requireNonNull(varIndexes.get(localVariable));
  }

  public RecurPoint getRecurPoint(Recur recur) {
    return requireNonNull(recurPoints.get(recur));
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
        merged(recurPoints, other.recurPoints),
        errors.concat(other.errors));
  }

  public NameResolutionResult merge(Collection<NameResolutionResult> results) {
    return results.stream().reduce(this, NameResolutionResult::merge);
  }

  private <K, V> Map<K, V> merged(Map<K, V> mapA, Map<K, V> mapB) {
    var map = new HashMap<>(mapA);
    map.putAll(mapB);
    return map;
  }
}
