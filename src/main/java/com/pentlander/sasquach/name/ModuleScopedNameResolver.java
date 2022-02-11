package com.pentlander.sasquach.name;

import com.pentlander.sasquach.RangedErrorList;
import com.pentlander.sasquach.ast.ModuleDeclaration;
import com.pentlander.sasquach.ast.NodeVisitor;
import com.pentlander.sasquach.ast.TypeAlias;
import com.pentlander.sasquach.ast.TypeNode;
import com.pentlander.sasquach.ast.Use;
import com.pentlander.sasquach.ast.expression.Expression;
import com.pentlander.sasquach.ast.expression.Function;
import com.pentlander.sasquach.ast.expression.Struct;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class ModuleScopedNameResolver {
  private final Map<String, ModuleScopedNameResolver> moduleImports = new HashMap<>();
  private final Map<String, Class<?>> foreignClasses = new HashMap<>();
  private final Map<String, TypeAlias> typeAliasNames = new HashMap<>();
  private final Map<String, Struct.Field> fields = new HashMap<>();
  private final Map<Struct.Field, NameResolutionResult> fieldResults = new HashMap<>();
  private final Map<String, Function> functions = new HashMap<>();
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
    return nameResolutionResult.withErrors(errors.build());
  }

  NameResolutionResult getResolver(Struct.Field field) {
    return Objects.requireNonNull(fieldResults.get(field));
  }

  NameResolutionResult getResolver(Function function) {
    return Objects.requireNonNull(functionResults.get(function));
  }

  private class Visitor implements NodeVisitor<Void> {
    @Override
    public Void visit(TypeNode typeNode) {
      return null;
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

    @Override
    public Void visit(Expression expression) {
      if (expression instanceof Struct struct) {
        for (var use : struct.useList()) {
          visit(use);
        }

        for (var typeAlias : struct.typeAliases()) {
          visit(typeAlias);
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
          functionResults.put(function, result);
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

  Optional<Function> resolveFunction(String functionName) {
    return Optional.ofNullable(functions.get(functionName));
  }

  Optional<Struct.Field> resolveField(String fieldName) {
    return Optional.ofNullable(fields.get(fieldName));
  }

  Optional<TypeNode> resolveTypeAlias(String typeAlias) {
    return Optional.ofNullable(typeAliasNames.get(typeAlias)).map(TypeAlias::typeNode);
  }
}
