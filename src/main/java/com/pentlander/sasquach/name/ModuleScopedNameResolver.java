package com.pentlander.sasquach.name;

import com.pentlander.sasquach.RangedErrorList;
import com.pentlander.sasquach.ast.ModuleDeclaration;
import com.pentlander.sasquach.ast.NamedTypeDefinition;
import com.pentlander.sasquach.ast.NodeVisitor;
import com.pentlander.sasquach.ast.SumTypeNode.VariantTypeNode;
import com.pentlander.sasquach.ast.TypeAlias;
import com.pentlander.sasquach.ast.TypeNode;
import com.pentlander.sasquach.ast.Use;
import com.pentlander.sasquach.ast.expression.LiteralStruct;
import com.pentlander.sasquach.ast.expression.Expression;
import com.pentlander.sasquach.ast.expression.Function;
import com.pentlander.sasquach.ast.expression.ModuleStruct;
import com.pentlander.sasquach.ast.expression.NamedFunction;
import com.pentlander.sasquach.ast.expression.Struct;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class ModuleScopedNameResolver {
  private final Map<String, ModuleScopedNameResolver> moduleImports = new HashMap<>();
  private final Map<String, Class<?>> foreignClasses = new HashMap<>();
  private final Map<String, TypeAlias> typeAliasNames = new HashMap<>();
  private final Map<TypeNode, NamedTypeDefinition> namedTypes = new HashMap<>();
  private final Map<String, VariantTypeNode> variantTypes = new HashMap<>();
  private final Map<String, LiteralStruct.Field> fields = new HashMap<>();
  private final Map<LiteralStruct.Field, NameResolutionResult> fieldResults = new HashMap<>();
  private final Map<String, NamedFunction> functions = new HashMap<>();
  private final Map<Function, NameResolutionResult> functionResults = new HashMap<>();
  private final RangedErrorList.Builder errors = RangedErrorList.builder();

  private final ModuleDeclaration module;
  private final ModuleResolver moduleResolver;
  private NameResolutionResult nameResolutionResult = NameResolutionResult.empty();

  public ModuleScopedNameResolver(ModuleDeclaration module, ModuleResolver moduleResolver) {
    this.module = module;
    this.moduleResolver = moduleResolver;
  }

  public ModuleDeclaration moduleDeclaration() {
    return module;
  }

  public NameResolutionResult resolve() {
    new Visitor().visit(module);
    return nameResolutionResult.withNamedTypes(namedTypes).withErrors(errors.build());
  }

  NameResolutionResult getResolver(LiteralStruct.Field field) {
    return Objects.requireNonNull(fieldResults.get(field));
  }

  NameResolutionResult getResolver(Function function) {
    return Objects.requireNonNull(functionResults.get(function));
  }

  private class Visitor implements NodeVisitor<Void> {
    @Override
    public Void visit(TypeNode typeNode) {
      throw new IllegalStateException();
    }

    @Override
    public Void visit(TypeAlias typeAlias) {
      var prevAlias = typeAliasNames.put(typeAlias.id().name(), typeAlias);
      if (prevAlias != null) {
        errors.add(new DuplicateNameError(typeAlias.id(), prevAlias.id()));
      }
      return null;
    }

    @Override
    public Void visit(Use.Module use) {
      var moduleScopedResolver = moduleResolver.resolveModule(use.id().name());
      if (moduleScopedResolver == null) {
        errors.add(new NameNotFoundError(use.id(), "module"));
        return null;
      }
      var existingImport = moduleImports.put(
          use.alias().name(),
          moduleScopedResolver);
      if (existingImport != null) {
        errors.add(new DuplicateNameError(use.id(), existingImport.moduleDeclaration().id()));
      }
      return null;
    }

    @Override
    public Void visit(Use.Foreign use) {
      try {
        var qualifiedName = use.id().name().replace('/', '.');
        var clazz = MethodHandles.lookup().findClass(qualifiedName);
        foreignClasses.put(use.alias().name(), clazz);
      } catch (ClassNotFoundException | IllegalAccessException e) {
        errors.add(new NameNotFoundError(use.alias(), "foreign class"));
      }
      return null;
    }

    private void checkAliases(Collection<TypeAlias> typeAliases) {
      for (var typeAlias : typeAliases) {
        var resolver = new TypeNameResolver(ModuleScopedNameResolver.this);
        var result = resolver.resolveTypeNode(typeAlias);
        namedTypes.putAll(result.namedTypes());
        variantTypes.putAll(result.variantTypes());
        errors.addAll(result.errors());
      }
    }

    @Override
    public Void visit(Expression expression) {
      if (expression instanceof Struct struct) {
        if (struct instanceof ModuleStruct moduleStruct) {
          for (var use : moduleStruct.useList()) {
            visit(use);
          }

          for (var typeAlias : moduleStruct.typeAliases()) {
            visit(typeAlias);
          }
          checkAliases(typeAliasNames.values());
        }

        for (var field : struct.fields()) {
          fields.put(field.name(), field);
        }
        for (var function : struct.functions()) {
          functions.put(function.name(), function);
        }

        for (var field : struct.fields()) {
          var resolver = new MemberScopedNameResolver(ModuleScopedNameResolver.this);
          var result = resolver.resolve(field);
          nameResolutionResult = nameResolutionResult.merge(result);
          fieldResults.put(field, result);
        }
        for (var function : struct.functions()) {
          var resolver = new MemberScopedNameResolver(ModuleScopedNameResolver.this);
          var result = resolver.resolve(function);
          nameResolutionResult = nameResolutionResult.merge(result);
          functionResults.put(function.function(), result);
        }
      }
      return null;
    }
  }

  Optional<Class<?>> resolveForeignClass(String classAlias) {
    return Optional.ofNullable(foreignClasses.get(classAlias));
  }

  Optional<ModuleDeclaration> resolveModule(String moduleAlias) {
    return Optional.ofNullable(moduleImports.get(moduleAlias)).map(ModuleScopedNameResolver::moduleDeclaration);
  }

  Optional<ModuleScopedNameResolver> resolveModuleResolver(String moduleAlias) {
    return Optional.ofNullable(moduleImports.get(moduleAlias));
  }

  Optional<NamedFunction> resolveFunction(String functionName) {
    return Optional.ofNullable(functions.get(functionName));
  }

  Optional<LiteralStruct.Field> resolveField(String fieldName) {
    return Optional.ofNullable(fields.get(fieldName));
  }

  Optional<TypeAlias> resolveTypeAlias(String typeAlias) {
    return Optional.ofNullable(typeAliasNames.get(typeAlias));
  }

  Optional<VariantTypeNode> resolveVariantTypeNode(String variantName) {
    return Optional.ofNullable(variantTypes.get(variantName));
  }
}
