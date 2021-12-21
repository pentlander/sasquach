package com.pentlander.sasquach.name;

import static java.util.Objects.requireNonNull;

import com.pentlander.sasquach.RangedError;
import com.pentlander.sasquach.RangedErrorList;
import com.pentlander.sasquach.ast.Use;
import com.pentlander.sasquach.ast.Use.Foreign;
import com.pentlander.sasquach.ast.expression.ForeignFieldAccess;
import com.pentlander.sasquach.ast.expression.ForeignFunctionCall;
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
public class NameResolutionResult {
  private static final NameResolutionResult EMPTY = new NameResolutionResult(Map.of(),
      Map.of(),
      Map.of(),
      Map.of(),
      Map.of(),
      Map.of(),
      new RangedErrorList(List.of()));
  private final Map<ForeignFieldAccess, Field> foreignFieldAccesses;
  private final Map<ForeignFunctionCall, ForeignFunctions> foreignFunctions;
  private final Map<LocalFunctionCall, QualifiedFunction> localFunctionCalls;
  private final Map<VarReference, ReferenceDeclaration> varReferences;
  private final Map<LocalVariable, Integer> varIndexes;
  private final Map<Use.Foreign, Class<?>> foreignUseClasses;
  private final RangedErrorList errors;

  public NameResolutionResult(Map<ForeignFieldAccess, Field> foreignFieldAccesses,
      Map<ForeignFunctionCall, ForeignFunctions> foreignFunctions,
      Map<LocalFunctionCall, QualifiedFunction> localFunctionCalls,
      Map<VarReference, ReferenceDeclaration> varReferences, Map<LocalVariable, Integer> varIndexes,
      Map<Foreign, Class<?>> foreignUseClasses, RangedErrorList errors) {
    this.foreignFieldAccesses = foreignFieldAccesses;
    this.foreignFunctions = foreignFunctions;
    this.localFunctionCalls = localFunctionCalls;
    this.varReferences = varReferences;
    this.varIndexes = varIndexes;
    this.foreignUseClasses = foreignUseClasses;
    this.errors = errors;
  }

  public NameResolutionResult withErrors(RangedErrorList errors) {
    return new NameResolutionResult(foreignFieldAccesses,
        foreignFunctions,
        localFunctionCalls,
        varReferences,
        varIndexes, foreignUseClasses, this.errors.concat(errors));
  }

  public NameResolutionResult withForeignUseClasses(Map<Use.Foreign, Class<?>> foreignUseClasses) {
    return new NameResolutionResult(foreignFieldAccesses,
        foreignFunctions,
        localFunctionCalls,
        varReferences, varIndexes, foreignUseClasses, errors);
  }

  public static NameResolutionResult empty() {
    return EMPTY;
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

  public Class<?> getForeignUseClass(Foreign foreignUse) {
    return requireNonNull(foreignUseClasses.get(foreignUse));
  }

  public RangedErrorList errors() {
    return errors;
  }

  public NameResolutionResult merge(NameResolutionResult other) {
    return new NameResolutionResult(merged(foreignFieldAccesses, other.foreignFieldAccesses),
        merged(foreignFunctions, other.foreignFunctions),
        merged(localFunctionCalls, other.localFunctionCalls),
        merged(varReferences, other.varReferences),
        merged(varIndexes, other.varIndexes),
        foreignUseClasses,
        errors.concat(other.errors));
  }

  private <K, V> Map<K, V> merged(Map<K, V> mapA, Map<K, V> mapB) {
    var map = new HashMap<>(mapA);
    map.putAll(mapB);
    return map;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private final Map<ForeignFieldAccess, Field> foreignFieldAccesses = new HashMap<>();
    private final Map<ForeignFunctionCall, List<Executable>> foreignFunctions = new HashMap<>();
    private final Map<LocalFunctionCall, QualifiedFunction> localFunctionCalls = new HashMap<>();
    private final Map<VarReference, ReferenceDeclaration> varReferences = new HashMap<>();
    private final Map<LocalVariable, Integer> varIndexes = new HashMap<>();
    private final Map<Use.Foreign, Class<?>> foreignUseClasses = new HashMap<>();
    private final List<RangedError> errors = new ArrayList<>();

    private Builder() {}

    public Builder mergeFrom(NameResolutionResult nameResolutionResult) {
      return this;
    }

    public Builder putForeignFieldAccess(ForeignFieldAccess foreignFieldAccess, Field field) {
      foreignFieldAccesses.put(foreignFieldAccess, field);
      return this;
    }

    public Builder putForeignFunction(ForeignFunctionCall foreignFunctionCall,
        List<Executable> executables) {
      foreignFunctions.put(foreignFunctionCall, executables);
      return this;
    }

    public Builder putLocalFunctionCall(LocalFunctionCall localFunctionCall, QualifiedFunction qualifiedFunction) {
      localFunctionCalls.put(localFunctionCall, qualifiedFunction);
      return this;
    }

    public Builder putVarReference(VarReference varReference, ReferenceDeclaration referenceDeclaration) {
      varReferences.put(varReference, referenceDeclaration);
      return this;
    }

    public Builder putVarIndex(LocalVariable localVariable, int index) {
      varIndexes.put(localVariable, index);
      return this;
    }

    public Builder addError(RangedError error) {
      errors.add(error);
      return this;
    }

//    public ResolutionResult build() {
//      return new ResolutionResult(foreignFieldAccesses,
//          foreignFunctions,
//          localFunctionCalls,
//          varReferences,
//          varIndexes, foreignUseClasses, new RangedErrorList(errors));
//    }
  }
}
