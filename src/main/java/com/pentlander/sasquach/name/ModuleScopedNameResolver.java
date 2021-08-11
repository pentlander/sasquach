package com.pentlander.sasquach.name;

import com.pentlander.sasquach.InternalCompilerException;
import com.pentlander.sasquach.RangedError;
import com.pentlander.sasquach.ast.ModuleDeclaration;
import com.pentlander.sasquach.ast.NodeVisitor;
import com.pentlander.sasquach.ast.TypeNode;
import com.pentlander.sasquach.ast.Use.Foreign;
import com.pentlander.sasquach.ast.Use.Module;
import com.pentlander.sasquach.ast.expression.Expression;
import com.pentlander.sasquach.ast.expression.Function;
import com.pentlander.sasquach.ast.expression.Struct;
import com.pentlander.sasquach.name.MemberScopedNameResolver.ResolutionResult;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class ModuleScopedNameResolver  {
  private final Resolver<String, ModuleDeclaration> moduleResolver = k -> null;
  private final Map<String, ModuleDeclaration> moduleImports = new HashMap<>();
  private final Map<String, Class<?>> foreignClasses = new HashMap<>();
  private final Map<String, Struct.Field> fields = new HashMap<>();
  private final Map<Struct.Field, ResolutionResult> fieldResults = new HashMap<>();
  private final Map<String, Function> functions = new HashMap<>();
  private final Map<Function, ResolutionResult> functionResults = new HashMap<>();
  private final ModuleDeclaration module;
  private final List<RangedError> errors = new ArrayList<>();

  public ModuleScopedNameResolver(ModuleDeclaration module) {
    this.module = module;
  }

  public List<RangedError> resolve() {
    new Visitor().visit(module);
    return errors;
  }

  public ResolutionResult getResolver(Struct.Field field) {
    return Objects.requireNonNull(fieldResults.get(field));
  }

  public ResolutionResult getResolver(Function function) {
    return Objects.requireNonNull(functionResults.get(function));
  }

  class Visitor implements NodeVisitor<Void> {
    @Override
    public Void visit(TypeNode typeNode) {
      return null;
    }

    @Override
    public Void visit(Module use) {
      var module = moduleResolver.resolve(use.id().name());
      var existingImport = moduleImports.put(use.alias().name(), module.orElseThrow());
      if (existingImport != null) {
        errors.add(new DuplicateNameError(use.id(), existingImport.id()));
      }
      return null;
    }

    @Override
    public Void visit(Foreign use) {
      try {
        var qualifiedName = use.id().name().replace('/', '.');
        var clazz = MethodHandles.lookup().findClass(qualifiedName);
        foreignClasses.put(use.alias().name(), clazz);
      } catch (ClassNotFoundException | IllegalAccessException e) {
        errors.add(new NameNotFoundError(use.alias(), "foreign class"));
        throw new InternalCompilerException(e);
      }
      return null;
    }

    @Override
    public Void visit(Expression expression) {
      if (expression instanceof Struct struct) {
        for (var use : struct.useList()) {
          visit(use);
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
          errors.addAll(result.errors());
          fieldResults.put(field, result);
        }
        for (var function : struct.functions()) {
          var resolver = new MemberScopedNameResolver(ModuleScopedNameResolver.this);
          var result = resolver.resolve(function);
          errors.addAll(result.errors());
          functionResults.put(function, result);
        }
      }
      return null;
    }
  }

  public Optional<Class<?>> resolveForeignClass(String classAlias) {
    return Optional.ofNullable(foreignClasses.get(classAlias));
  }

  public Optional<ModuleDeclaration> resolveModule(String moduleAlias) {
    return Optional.ofNullable(moduleImports.get(moduleAlias));
  }

  public Optional<Function> resolveFunction(String functionName) {
    return Optional.ofNullable(functions.get(functionName));
  }

  public Optional<Struct.Field> resolveField(String fieldName) {
    return Optional.ofNullable(fields.get(fieldName));
  }

  interface Resolver<K, V> {
    Optional<V> resolve(K key);
  }
}
