package com.pentlander.sasquach.nameres;

import com.pentlander.sasquach.ast.id.Id;
import com.pentlander.sasquach.ast.NamedTypeDefinition;
import com.pentlander.sasquach.ast.RecurPoint;
import com.pentlander.sasquach.ast.typenode.TypeNode;
import com.pentlander.sasquach.ast.expression.ForeignFieldAccess;
import com.pentlander.sasquach.ast.expression.Function;
import com.pentlander.sasquach.ast.expression.LocalVariable;
import com.pentlander.sasquach.ast.expression.Match;
import com.pentlander.sasquach.ast.expression.Recur;
import com.pentlander.sasquach.ast.expression.VarReference;
import com.pentlander.sasquach.nameres.MemberScopedNameResolver.FunctionCallTarget;
import com.pentlander.sasquach.nameres.MemberScopedNameResolver.ReferenceDeclaration;
import com.pentlander.sasquach.type.NamedType;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.SequencedSet;

@RecordBuilder
@RecordBuilder.Options(addSingleItemCollectionBuilders = true, useImmutableCollections = true)
public record NameResolutionData(
    Map<NamedType, NamedTypeDefinition> namedTypeDefs,
    Map<ForeignFieldAccess, Field> foreignFieldAccesses,
    Map<Id, ForeignFunctions> foreignFunctions,
    Map<Id, FunctionCallTarget> localFunctionCalls,
    Map<VarReference, ReferenceDeclaration> varReferences,
    Map<Recur, RecurPoint> recurPoints,
    Map<Match, List<TypeNode>> matchTypeNodes,
    Map<Function, SequencedSet<LocalVariable>> funcCaptures
) {}
