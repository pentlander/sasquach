package com.pentlander.sasquach.name;

import static java.util.Objects.requireNonNull;

import com.pentlander.sasquach.RangedError;
import com.pentlander.sasquach.ast.expression.ForeignFieldAccess;
import com.pentlander.sasquach.ast.expression.ForeignFunctionCall;
import com.pentlander.sasquach.ast.expression.Function;
import com.pentlander.sasquach.ast.expression.LocalFunctionCall;
import com.pentlander.sasquach.ast.expression.LocalVariable;
import com.pentlander.sasquach.ast.expression.VarReference;
import com.pentlander.sasquach.name.MemberScopedNameResolver.QualifiedFunction;
import com.pentlander.sasquach.name.MemberScopedNameResolver.ReferenceDeclaration;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("ClassCanBeRecord")
public class ResolutionResult {
  private static final ResolutionResult EMPTY = new ResolutionResult(Map.of(),
      Map.of(),
      Map.of(),
      Map.of(),
      Map.of(),
      List.of());
  private final Map<ForeignFieldAccess, Field> foreignFieldAccesses;
  private final Map<ForeignFunctionCall, List<Executable>> foreignFunctions;
  private final Map<LocalFunctionCall, QualifiedFunction> localFunctionCalls;
  private final Map<VarReference, ReferenceDeclaration> varReferences;
  private final Map<LocalVariable, Integer> varIndexes;
  private final List<RangedError> errors;

  public ResolutionResult(Map<ForeignFieldAccess, Field> foreignFieldAccesses,
      Map<ForeignFunctionCall, List<Executable>> foreignFunctions,
      Map<LocalFunctionCall, QualifiedFunction> localFunctionCalls,
      Map<VarReference, ReferenceDeclaration> varReferences, Map<LocalVariable, Integer> varIndexes,
      List<RangedError> errors) {
    this.foreignFieldAccesses = foreignFieldAccesses;
    this.foreignFunctions = foreignFunctions;
    this.localFunctionCalls = localFunctionCalls;
    this.varReferences = varReferences;
    this.varIndexes = varIndexes;
    this.errors = errors;
  }

  public ResolutionResult withErrors(List<RangedError> errors) {
    var mergedErrors = new ArrayList<>(this.errors);
    mergedErrors.addAll(errors);
    return new ResolutionResult(foreignFieldAccesses,
        foreignFunctions,
        localFunctionCalls,
        varReferences,
        varIndexes,
        mergedErrors);
  }

  public static ResolutionResult empty() {
    return EMPTY;
  }

  public Field getForeignField(ForeignFieldAccess foreignFieldAccess) {
    return requireNonNull(foreignFieldAccesses.get(foreignFieldAccess));
  }

  public List<Executable> getForeignFunction(ForeignFunctionCall foreignFunctionCall) {
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

  public List<RangedError> errors() {
    return errors;
  }

  public ResolutionResult merge(ResolutionResult other) {
    var mergedErrors = new ArrayList<>(errors);
    mergedErrors.addAll(other.errors);
    return new ResolutionResult(merged(foreignFieldAccesses, other.foreignFieldAccesses),
        merged(foreignFunctions, other.foreignFunctions),
        merged(localFunctionCalls, other.localFunctionCalls),
        merged(varReferences, other.varReferences),
        merged(varIndexes, other.varIndexes),
        mergedErrors);
  }

  private <K, V> Map<K, V> merged(Map<K, V> mapA, Map<K, V> mapB) {
    var map = new HashMap<>(mapA);
    map.putAll(mapB);
    return map;
  }
}
