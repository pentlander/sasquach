package com.pentlander.sasquach.name;

import com.pentlander.sasquach.RangedErrorList;
import com.pentlander.sasquach.Util;
import com.pentlander.sasquach.ast.BasicTypeNode;
import com.pentlander.sasquach.ast.FunctionSignature;
import com.pentlander.sasquach.ast.NamedTypeDefinition;
import com.pentlander.sasquach.ast.NamedTypeDefinition.ForeignClass;
import com.pentlander.sasquach.ast.StructTypeNode;
import com.pentlander.sasquach.ast.SumTypeNode;
import com.pentlander.sasquach.ast.SumTypeNode.VariantTypeNode;
import com.pentlander.sasquach.ast.TupleTypeNode;
import com.pentlander.sasquach.ast.TypeAlias;
import com.pentlander.sasquach.ast.TypeNode;
import com.pentlander.sasquach.type.LocalNamedType;
import com.pentlander.sasquach.type.ModuleNamedType;
import com.pentlander.sasquach.type.TypeParameter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Resolves named types to their corresponding aliases or type parameters. */
public class TypeNameResolver {
  private final List<TypeParameter> contextTypeParams;
  private final ModuleScopedNameResolver moduleScopedNameResolver;
  // Map of named types to the name declaration, e.g. type alias or type parameter
  private final Map<TypeNode, NamedTypeDefinition> namedTypes = new HashMap<>();
  private final Map<String, VariantTypeNode> variantNodes = new HashMap<>();
  private final RangedErrorList.Builder errors = RangedErrorList.builder();

  public TypeNameResolver(ModuleScopedNameResolver moduleScopedNameResolver) {
    this(List.of(), moduleScopedNameResolver);
  }
  private TypeNameResolver(List<TypeParameter> contextTypeParams,
      ModuleScopedNameResolver moduleScopedNameResolver) {
    this.contextTypeParams = contextTypeParams;
    this.moduleScopedNameResolver = moduleScopedNameResolver;
  }

  private void putNamedType(TypeNode typeNode, NamedTypeDefinition namedTypeDef) {
    var existing = namedTypes.put(typeNode,  namedTypeDef);
    if (existing != null) {
      throw new IllegalStateException();
    }
  }

  public Result resolveTypeNode(TypeNode typeNode) {
    switch (typeNode) {
      case BasicTypeNode<?> basicTypeNode -> resolveNamedType(basicTypeNode);
      case StructTypeNode structTypeNode -> structTypeNode.fieldTypeNodes().values()
          .forEach(this::resolveTypeNode);
      case FunctionSignature functionSignature -> {
        var typeParams = Util.concat(contextTypeParams, functionSignature.typeParameters());
        var resolver = new TypeNameResolver(typeParams, moduleScopedNameResolver);
        functionSignature.parameters().forEach(param -> {
          var result = resolver.resolveTypeNode(param.typeNode());
          mergeResult(result);
        });
        var result = resolver.resolveTypeNode(functionSignature.returnTypeNode());
        mergeResult(result);
      }
      case TypeAlias typeAlias -> {
        var typeParams = Util.concat(contextTypeParams, typeAlias.typeParameters());
        var resolver = new TypeNameResolver(typeParams, moduleScopedNameResolver);
        var result = resolver.resolveTypeNode(typeAlias.typeNode());
        mergeResult(result);
      }
      case TupleTypeNode tupleTypeNode -> tupleTypeNode.fields().forEach(this::resolveTypeNode);
      case TypeParameter ignored -> {}
      case SumTypeNode sumTypeNode -> sumTypeNode.variantTypeNodes().forEach(this::resolveTypeNode);
      case VariantTypeNode variantTypeNode -> {
        variantNodes.put(variantTypeNode.id().name(), variantTypeNode);
        switch (variantTypeNode) {
          case VariantTypeNode.Singleton singleton -> {
            var typeAlias = moduleScopedNameResolver.resolveTypeAlias(singleton.aliasId().name()).get();
            putNamedType(singleton, typeAlias);
          }
          case VariantTypeNode.Tuple tuple -> resolveTypeNode(tuple.typeNode());
          case VariantTypeNode.Struct struct -> resolveTypeNode(struct.typeNode());
        }
      }
    }
    return new Result(namedTypes, variantNodes, errors.build());
  }

  private void mergeResult(Result result) {
    namedTypes.putAll(result.namedTypes);
    variantNodes.putAll(result.variantTypes);
    errors.addAll(result.errors);
  }

  private void resolveNamedType(TypeNode typeNode) {
    var type = typeNode.type();
    if (type instanceof LocalNamedType localNamedType) {
      localNamedType.typeArgumentNodes().forEach(this::resolveNamedType);
      // Check if the named type matches a type parameter
      var name = type.typeName();
      var typeParam = contextTypeParams.stream().filter(param -> param.typeName().equals(name))
          .findFirst();
      // Check if the named type matches a local type alias
      var typeAlias = moduleScopedNameResolver.resolveTypeAlias(localNamedType.typeName());
      var foreignClass = moduleScopedNameResolver.resolveForeignClass(localNamedType.typeName());
      // Ensure that a named type is only resolved from one source. If it isn't, create an error.
      if (typeParam.isPresent() && typeAlias.isPresent()) {
        var aliasId =  typeAlias.get().id();
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
        errors.add(new NameNotFoundError(localNamedType.id(), "named type"));
      }
    } else if (type instanceof ModuleNamedType moduleNamedType) {
      moduleNamedType.typeArgumentNodes().forEach(this::resolveNamedType);
      var moduleScopedResolver = moduleScopedNameResolver.resolveModuleResolver(moduleNamedType.moduleName());
      moduleScopedResolver.flatMap(m -> m.resolveTypeAlias(moduleNamedType.name()))
          .ifPresentOrElse(alias -> putNamedType(typeNode, alias),
              () -> errors.add(new NameNotFoundError(moduleNamedType.id(), "module type")));
    }
  }

  public record Result(Map<TypeNode, NamedTypeDefinition> namedTypes,
                       Map<String, VariantTypeNode> variantTypes, RangedErrorList errors) {}
}
