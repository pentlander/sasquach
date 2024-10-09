package com.pentlander.sasquach.nameres;

import com.pentlander.sasquach.RangedErrorList;
import com.pentlander.sasquach.Util;
import com.pentlander.sasquach.ast.id.TypeParameterId;
import com.pentlander.sasquach.ast.typenode.ArrayTypeNode;
import com.pentlander.sasquach.ast.typenode.BasicTypeNode;
import com.pentlander.sasquach.ast.typenode.FunctionSignature;
import com.pentlander.sasquach.ast.NamedTypeDefinition;
import com.pentlander.sasquach.ast.NamedTypeDefinition.ForeignClass;
import com.pentlander.sasquach.ast.typenode.NamedTypeNode;
import com.pentlander.sasquach.ast.typenode.StructTypeNode;
import com.pentlander.sasquach.ast.typenode.StructTypeNode.RowModifier.NamedRow;
import com.pentlander.sasquach.ast.typenode.SumTypeNode;
import com.pentlander.sasquach.ast.typenode.SumTypeNode.VariantTypeNode.SingletonTypeNode;
import com.pentlander.sasquach.ast.typenode.TupleTypeNode;
import com.pentlander.sasquach.ast.id.TypeId;
import com.pentlander.sasquach.ast.typenode.TypeNode;
import com.pentlander.sasquach.ast.typenode.TypeStatement;
import com.pentlander.sasquach.type.NamedType;
import com.pentlander.sasquach.type.TypeParameterNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Resolves named types to their corresponding aliases or type parameters. */
public class TypeNodeNameResolver {
  private final List<TypeParameterNode> contextTypeParams;
  private final ModuleScopedNameResolver moduleScopedNameResolver;
  // Map of named type nodes to its type definition, e.g. type alias or type parameter
  private final Map<NamedType, NamedTypeDefinition> namedTypes = new HashMap<>();
  private final RangedErrorList.Builder errors = RangedErrorList.builder();

  public TypeNodeNameResolver(ModuleScopedNameResolver moduleScopedNameResolver) {
    this(List.of(), moduleScopedNameResolver);
  }
  public TypeNodeNameResolver(List<TypeParameterNode> contextTypeParams,
      ModuleScopedNameResolver moduleScopedNameResolver) {
    this.contextTypeParams = contextTypeParams;
    this.moduleScopedNameResolver = moduleScopedNameResolver;
  }

  private void putNamedType(NamedTypeNode typeNode, NamedTypeDefinition namedTypeDef) {
    var existing = namedTypes.put(typeNode.type(),  namedTypeDef);
    if (existing != null) {
      throw new IllegalStateException();
    }
  }

  public Result resolveTypeNode(TypeNode typeNode) {
    switch (typeNode) {
      case BasicTypeNode _ ->  {}
      case ArrayTypeNode arrayTypeNode -> {
        resolveTypeNode(arrayTypeNode.typeArgumentNode());
      }
      case StructTypeNode structTypeNode -> {
        structTypeNode.fieldTypeNodes().values().forEach(this::resolveTypeNode);
        if (structTypeNode.rowModifier() instanceof NamedRow namedRow) {
          resolveTypeNode(namedRow.typeNode());
        }
      }
      case FunctionSignature functionSignature -> {
        var typeParams = Util.concat(contextTypeParams, functionSignature.typeParameterNodes());
        var resolver = new TypeNodeNameResolver(typeParams, moduleScopedNameResolver);
        functionSignature.parameters().forEach(param -> {
          if (param.typeNode() != null) {
            var result = resolver.resolveTypeNode(param.typeNode());
            mergeResult(result);
          }
        });

        if (functionSignature.returnTypeNode() != null) {
          var result = resolver.resolveTypeNode(functionSignature.returnTypeNode());
          mergeResult(result);
        }
      }
      case TypeStatement typeStatement -> {
        var typeParams = Util.concat(contextTypeParams, typeStatement.typeParameterNodes());
        var resolver = new TypeNodeNameResolver(typeParams, moduleScopedNameResolver);
        var result = resolver.resolveTypeNode(typeStatement.typeNode());
        mergeResult(result);
      }
      case TupleTypeNode tupleTypeNode -> tupleTypeNode.fields().forEach(this::resolveTypeNode);
      case SumTypeNode sumTypeNode -> sumTypeNode.variantTypeNodes().forEach(this::resolveTypeNode);
      case SingletonTypeNode _ -> {}
      case NamedTypeNode namedTypeNode -> resolveNamedType(namedTypeNode);
    }
    return new Result(namedTypes, errors.build());
  }

  private void mergeResult(Result result) {
    namedTypes.putAll(result.namedTypes);
    errors.addAll(result.errors);
  }

  private void resolveNamedType(NamedTypeNode typeNode) {
    typeNode.typeArgumentNodes().forEach(this::resolveNamedType);

    switch (typeNode.id()) {
      case TypeId typeId -> {
        // Check if the named type matches a type parameter
        var name = typeId.name();
        var typeParam = contextTypeParams.stream()
            .filter(param -> param.name().equals(name.toString()))
            .findFirst();
        // Check if the named type matches a local type alias
        var typeAlias = moduleScopedNameResolver.resolveTypeAlias(name);
        var foreignClass = moduleScopedNameResolver.resolveForeignClass(name);
        // Ensure that a named type is only resolved from one source. If it isn't, create an error.
        if (typeParam.isPresent() && typeAlias.isPresent()) {
          var aliasId = typeAlias.get().id();
          var paramId = typeParam.get().id();
          errors.add(new DuplicateNameError(aliasId, paramId));
        } else if (typeAlias.isPresent()) {
          putNamedType(typeNode, typeAlias.get());
        } else if (typeParam.isPresent()) {
          // TODO remove branch
          throw new IllegalStateException();
        } else if (foreignClass.isPresent()) {
          putNamedType(typeNode, new ForeignClass(foreignClass.get()));
        } else {
          errors.add(new NameNotFoundError(typeId, "named type"));
        }
      }
      case TypeParameterId typeId -> {
        var name = typeId.name();
        contextTypeParams.stream()
            .filter(param -> param.id().name().equals(name))
            .findFirst()
            .ifPresentOrElse(
                typeParam -> putNamedType(typeNode, typeParam),
                () -> errors.add(new NameNotFoundError(typeId, "named type")));
      }
    }
  }

  private void resolveNamedType(TypeNode typeNode) {
    if (typeNode instanceof NamedTypeNode namedTypeNode) {
      resolveNamedType(namedTypeNode);
    }
  }

  public record Result(Map<NamedType, NamedTypeDefinition> namedTypes, RangedErrorList errors) {}
}
