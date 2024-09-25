package com.pentlander.sasquach.name;

import static java.util.Objects.requireNonNull;

import com.pentlander.sasquach.RangedErrorList;
import com.pentlander.sasquach.ast.NamedTypeDefinition;
import com.pentlander.sasquach.ast.RecurPoint;
import com.pentlander.sasquach.ast.TypeNode;
import com.pentlander.sasquach.ast.expression.ForeignFieldAccess;
import com.pentlander.sasquach.ast.expression.ForeignFunctionCall;
import com.pentlander.sasquach.ast.expression.Function;
import com.pentlander.sasquach.ast.expression.LocalFunctionCall;
import com.pentlander.sasquach.ast.expression.LocalVariable;
import com.pentlander.sasquach.ast.expression.Match;
import com.pentlander.sasquach.ast.expression.Recur;
import com.pentlander.sasquach.ast.expression.VarReference;
import com.pentlander.sasquach.name.MemberScopedNameResolver.FunctionCallTarget;
import com.pentlander.sasquach.name.MemberScopedNameResolver.ReferenceDeclaration;
import com.pentlander.sasquach.type.NamedType;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class NameResolutionResult {
  private static final NameResolutionResult EMPTY = new NameResolutionResult(
      NameResolutionDataBuilder.builder().build(),
      new RangedErrorList(List.of()));

  private final Map<NamedType, NamedTypeDefinition> namedTypeDefs;
  private final NameResolutionData nameData;
  private final RangedErrorList errors;

  public NameResolutionResult(NameResolutionData nameData, RangedErrorList errors) {
    this.namedTypeDefs = nameData.namedTypeDefs();
    this.nameData = nameData;
    this.errors = errors;
  }

  public NameResolutionResult withErrors(RangedErrorList errors) {
    return new NameResolutionResult(nameData, this.errors.concat(errors));
  }

  public NameResolutionResult withNamedTypeDefs(Map<NamedType, NamedTypeDefinition> namedTypes) {
    var mergedNameData = NameResolutionDataBuilder.builder(nameData)
        .addNamedTypeDefs(namedTypes.entrySet())
        .build();
    return new NameResolutionResult(mergedNameData, errors);
  }

  public static NameResolutionResult empty() {
    return EMPTY;
  }

  public Optional<NamedTypeDefinition> getNamedTypeDef(NamedType type) {
    return Optional.ofNullable(namedTypeDefs.get(type));
  }

  public Field getForeignField(ForeignFieldAccess foreignFieldAccess) {
    return requireNonNull(nameData.foreignFieldAccesses().get(foreignFieldAccess));
  }

  public ForeignFunctions getForeignFunction(ForeignFunctionCall foreignFunctionCall) {
    return requireNonNull(nameData.foreignFunctions().get(foreignFunctionCall.functionId()));
  }

  public FunctionCallTarget getLocalFunctionCallTarget(LocalFunctionCall localFunctionCall) {
    return requireNonNull(nameData.localFunctionCalls().get(localFunctionCall.functionId()),
        localFunctionCall.toString());
  }

  public ReferenceDeclaration getVarReference(VarReference varReference) {
    return requireNonNull(nameData.varReferences().get(varReference), varReference.toString());
  }

  public RecurPoint getRecurPoint(Recur recur) {
    return requireNonNull(nameData.recurPoints().get(recur));
  }

  /**
   * Returns a list of type nodes that correspond to the match branches in order.
   **/
  public List<TypeNode> getMatchTypeNodes(Match match) {
    return requireNonNull(nameData.matchTypeNodes().get(match));
  }

  public List<LocalVariable> getFunctionCaptures(Function func) {
    var captures = nameData.funcCaptures().get(func);
    return captures != null ? List.copyOf(captures) : List.of();
  }

  public RangedErrorList errors() {
    return errors;
  }

  public NameResolutionResult merge(NameResolutionResult other) {
    var nd = nameData;
    var ond = other.nameData;
    return new NameResolutionResult(
        NameResolutionDataBuilder.builder()
            .namedTypeDefs(merged(nd.namedTypeDefs(), ond.namedTypeDefs()))
            .foreignFieldAccesses(merged(nd.foreignFieldAccesses(), ond.foreignFieldAccesses()))
            .foreignFunctions(merged(nd.foreignFunctions(), ond.foreignFunctions()))
            .localFunctionCalls(merged(nd.localFunctionCalls(), ond.localFunctionCalls()))
            .varReferences(merged(nd.varReferences(), ond.varReferences()))
            .recurPoints(merged(nd.recurPoints(), ond.recurPoints()))
            .matchTypeNodes(merged(nd.matchTypeNodes(), ond.matchTypeNodes()))
            .funcCaptures(merged(nd.funcCaptures(), ond.funcCaptures()))
            .build(),
        errors.concat(other.errors)
     );
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
