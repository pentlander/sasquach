package com.pentlander.sasquach.name;

import com.pentlander.sasquach.ast.Id;
import com.pentlander.sasquach.ast.NamedTypeDefinition;
import com.pentlander.sasquach.ast.RecurPoint;
import com.pentlander.sasquach.ast.TypeNode;
import com.pentlander.sasquach.ast.expression.ForeignFieldAccess;
import com.pentlander.sasquach.ast.expression.Function;
import com.pentlander.sasquach.ast.expression.LocalVariable;
import com.pentlander.sasquach.ast.expression.Match;
import com.pentlander.sasquach.ast.expression.NamedStruct;
import com.pentlander.sasquach.ast.expression.Recur;
import com.pentlander.sasquach.ast.expression.VarReference;
import com.pentlander.sasquach.name.MemberScopedNameResolver.FunctionCallTarget;
import com.pentlander.sasquach.name.MemberScopedNameResolver.ReferenceDeclaration;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedSet;

@RecordBuilder
@RecordBuilder.Options(addSingleItemCollectionBuilders = true, useImmutableCollections = true)
public record NameResolutionData(
    Map<TypeNode, NamedTypeDefinition> typeAliases,
    Map<ForeignFieldAccess, Field> foreignFieldAccesses,
    Map<Id, ForeignFunctions> foreignFunctions,
    Map<Id, FunctionCallTarget> localFunctionCalls,
    Map<VarReference, ReferenceDeclaration> varReferences,
    Map<NamedStruct, NamedStructId> namedStructTypes,
    Map<Recur, RecurPoint> recurPoints,
    Map<Match, List<TypeNode>> matchTypeNodes,
    Map<Function, SequencedSet<LocalVariable>> funcCaptures
) {
  NameResolutionData merge(NameResolutionData ond) {
    return NameResolutionDataBuilder.builder()
        .typeAliases(merged(typeAliases(), ond.typeAliases()))
        .foreignFieldAccesses(merged(foreignFieldAccesses(), ond.foreignFieldAccesses()))
        .foreignFunctions(merged(foreignFunctions(), ond.foreignFunctions()))
        .localFunctionCalls(merged(localFunctionCalls(), ond.localFunctionCalls()))
        .varReferences(merged(varReferences(), ond.varReferences()))
        .namedStructTypes(merged(namedStructTypes(), ond.namedStructTypes()))
        .recurPoints(merged(recurPoints(), ond.recurPoints()))
        .matchTypeNodes(merged(matchTypeNodes(), ond.matchTypeNodes()))
        .funcCaptures(merged(funcCaptures(), ond.funcCaptures()))
        .build();
  }

  private static <K, V> Map<K, V> merged(Map<K, V> mapA, Map<K, V> mapB) {
    var map = new HashMap<>(mapA);
    map.putAll(mapB);
    return map;
  }

  public sealed interface NamedStructId {
    record Variant(Id sumTypeId, Id variantStructId) implements NamedStructId {}
  }
}
