package com.pentlander.sasquach.name;

import com.pentlander.sasquach.RangedErrorList;
import com.pentlander.sasquach.Util;
import com.pentlander.sasquach.ast.BasicTypeNode;
import com.pentlander.sasquach.ast.FunctionSignature;
import com.pentlander.sasquach.ast.ModuleScopedTypeId;
import com.pentlander.sasquach.ast.NamedTypeDefinition;
import com.pentlander.sasquach.ast.NamedTypeDefinition.ForeignClass;
import com.pentlander.sasquach.ast.NamedTypeNode;
import com.pentlander.sasquach.ast.StructTypeNode;
import com.pentlander.sasquach.ast.StructTypeNode.RowModifier.NamedRow;
import com.pentlander.sasquach.ast.SumTypeNode;
import com.pentlander.sasquach.ast.SumTypeNode.VariantTypeNode;
import com.pentlander.sasquach.ast.TupleTypeNode;
import com.pentlander.sasquach.ast.TypeId;
import com.pentlander.sasquach.ast.TypeStatement;
import com.pentlander.sasquach.ast.TypeNode;
import com.pentlander.sasquach.ast.UnqualifiedTypeName;
import com.pentlander.sasquach.type.TypeParameter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Resolves named types to their corresponding aliases or type parameters. */
public class TypeNodeNameResolver {
  private final List<TypeParameter> contextTypeParams;
  private final ModuleScopedNameResolver moduleScopedNameResolver;
  // Map of named type nodes to its type definition, e.g. type alias or type parameter
  private final Map<NamedTypeNode, NamedTypeDefinition> namedTypes = new HashMap<>();
  private final RangedErrorList.Builder errors = RangedErrorList.builder();

  public TypeNodeNameResolver(ModuleScopedNameResolver moduleScopedNameResolver) {
    this(List.of(), moduleScopedNameResolver);
  }
  public TypeNodeNameResolver(List<TypeParameter> contextTypeParams,
      ModuleScopedNameResolver moduleScopedNameResolver) {
    this.contextTypeParams = contextTypeParams;
    this.moduleScopedNameResolver = moduleScopedNameResolver;
  }

  private void putNamedType(NamedTypeNode typeNode, NamedTypeDefinition namedTypeDef) {
    var existing = namedTypes.put(typeNode,  namedTypeDef);
    if (existing != null) {
      throw new IllegalStateException();
    }
  }

  public Result resolveTypeNode(TypeNode typeNode) {
    switch (typeNode) {
      case BasicTypeNode _ ->  {}
      case StructTypeNode structTypeNode -> {
        structTypeNode.fieldTypeNodes().values().forEach(this::resolveTypeNode);
        if (structTypeNode.rowModifier() instanceof NamedRow namedRow) {
          resolveTypeNode(namedRow.typeNode());
        }
      }
      case FunctionSignature functionSignature -> {
        var typeParams = Util.concat(contextTypeParams, functionSignature.typeParameters());
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
        var typeParams = Util.concat(contextTypeParams, typeStatement.typeParameters());
        var resolver = new TypeNodeNameResolver(typeParams, moduleScopedNameResolver);
        var result = resolver.resolveTypeNode(typeStatement.typeNode());
        mergeResult(result);
      }
      case TupleTypeNode tupleTypeNode -> tupleTypeNode.fields().forEach(this::resolveTypeNode);
      case SumTypeNode sumTypeNode -> sumTypeNode.variantTypeNodes().forEach(this::resolveTypeNode);
      case VariantTypeNode variantTypeNode -> {
        switch (variantTypeNode) {
          case VariantTypeNode.Singleton _ -> {}
          case VariantTypeNode.Tuple tuple -> resolveTypeNode(tuple.typeNode());
          case VariantTypeNode.Struct struct -> resolveTypeNode(struct.typeNode());
        }
      }
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
          // Exit if the named type matches a type parameter
          putNamedType(typeNode, typeParam.get());
        } else if (foreignClass.isPresent()) {
          putNamedType(typeNode, new ForeignClass(foreignClass.get()));
        } else {
          errors.add(new NameNotFoundError(typeId, "named type"));
        }
      }
      case ModuleScopedTypeId(var moduleId, var id) -> {
        var moduleName = moduleId.name();
        var moduleScopedResolver = moduleScopedNameResolver.resolveModuleResolver(moduleName.toName());
        moduleScopedResolver.flatMap(m -> m.resolveTypeAlias(id.name())).ifPresentOrElse(
            alias -> putNamedType(typeNode, alias),
            () -> errors.add(new NameNotFoundError(typeNode.id(), "module type")));
      }
    }
  }

  private void resolveNamedType(TypeNode typeNode) {
    if (typeNode instanceof NamedTypeNode namedTypeNode) {
      resolveNamedType(namedTypeNode);
    }
  }

  public record Result(Map<NamedTypeNode, NamedTypeDefinition> namedTypes, RangedErrorList errors) {}
}
