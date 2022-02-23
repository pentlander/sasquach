package com.pentlander.sasquach.name;

import com.pentlander.sasquach.RangedErrorList;
import com.pentlander.sasquach.ast.BasicTypeNode;
import com.pentlander.sasquach.ast.FunctionSignature;
import com.pentlander.sasquach.ast.StructTypeNode;
import com.pentlander.sasquach.ast.TypeAlias;
import com.pentlander.sasquach.ast.TypeNode;
import com.pentlander.sasquach.type.LocalNamedType;
import com.pentlander.sasquach.type.ModuleNamedType;
import com.pentlander.sasquach.type.Type;
import com.pentlander.sasquach.type.TypeParameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Resolves named types to their corresponding aliases or type parameters. */
public class NamedTypeResolver {
  private final List<TypeNode<TypeParameter>> contextTypeParams;
  private final ModuleScopedNameResolver moduleScopedNameResolver;
  // Map of named types to the name declaration, e.g. type alias or type parameter
  private final Map<TypeNode<Type>, TypeNode<Type>> namedTypes = new HashMap<>();
  private final RangedErrorList.Builder errors = RangedErrorList.builder();

  public NamedTypeResolver(List<TypeNode<TypeParameter>> contextTypeParams,
      ModuleScopedNameResolver moduleScopedNameResolver) {
    this.contextTypeParams = contextTypeParams;
    this.moduleScopedNameResolver = moduleScopedNameResolver;
  }

  @SuppressWarnings("unchecked")
  private void putNamedType(TypeNode<? extends Type> typeNode, TypeNode<? extends Type> type) {
    var existing = namedTypes.put((TypeNode<Type>) typeNode, (TypeNode<Type>) type);
    if (existing != null) {
      throw new IllegalStateException();
    }
  }

  public Result resolveTypeNode(TypeNode<? extends Type> typeNode) {
    switch (typeNode) {
      case BasicTypeNode ignored -> resolveType(typeNode);
      case StructTypeNode structTypeNode -> structTypeNode.fieldTypeNodes().values()
          .forEach(this::resolveTypeNode);
      case FunctionSignature functionSignature -> {
        var typeParams = new ArrayList<>(contextTypeParams);
        typeParams.addAll(functionSignature.typeParameters());
        var resolver = new NamedTypeResolver(typeParams, moduleScopedNameResolver);
        functionSignature.parameters().forEach(param -> {
          var result = resolver.resolveTypeNode(param.typeNode());
          namedTypes.putAll(result.namedTypes);
          errors.addAll(result.errors);
        });
        var result = resolver.resolveTypeNode(functionSignature.returnTypeNode());
        namedTypes.putAll(result.namedTypes);
        errors.addAll(result.errors);
      }
    }
    return new Result(namedTypes, errors.build());
  }

  private void resolveType(TypeNode<? extends Type> typeNode) {
    var type = typeNode.type();
    if (type instanceof LocalNamedType localNamedType) {
      // Check if the named type matches a type parameter
      var name = type.typeName();
      var typeParam = contextTypeParams.stream().filter(param -> param.typeName().equals(name))
          .findFirst();
      // Check if the named type matches a local type alias
      var typeAlias = moduleScopedNameResolver.resolveTypeAlias(localNamedType.typeName());
      // Ensure that a named type is only resolved from one source. If it isn't, create an error.
      if (typeParam.isPresent() && typeAlias.isPresent()) {
        var aliasId =  typeAlias.get().id();
        var paramId = typeParam.get().type().id();
        errors.add(new DuplicateNameError(aliasId, paramId));
      } else if (typeAlias.isPresent()) {
        putNamedType(typeNode, typeAlias.get().typeNode());
      } else if (typeParam.isPresent()) {
        // Exit if the named type matches a type parameter
        putNamedType(typeNode, typeParam.get());
      } else {
        errors.add(new NameNotFoundError(localNamedType.id(), "named type"));
      }
    } else if (type instanceof ModuleNamedType moduleNamedType) {
      var moduleScopedResolver = moduleScopedNameResolver.resolveModuleResolver(moduleNamedType.moduleName());
      moduleScopedResolver.flatMap(m -> m.resolveTypeAlias(moduleNamedType.name()).map(TypeAlias::typeNode))
          .ifPresentOrElse(modNode -> putNamedType(typeNode, modNode),
              () -> errors.add(new NameNotFoundError(moduleNamedType.id(), "module type")));
    }
  }

  public record Result(Map<TypeNode<Type>, TypeNode<Type>> namedTypes, RangedErrorList errors) {}
}
