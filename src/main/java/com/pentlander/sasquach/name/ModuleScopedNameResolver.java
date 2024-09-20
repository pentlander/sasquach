package com.pentlander.sasquach.name;

import com.pentlander.sasquach.RangedErrorList;
import com.pentlander.sasquach.ast.ModuleDeclaration;
import com.pentlander.sasquach.ast.NamedTypeDefinition;
import com.pentlander.sasquach.ast.NamedTypeNode;
import com.pentlander.sasquach.ast.StructTypeNode;
import com.pentlander.sasquach.ast.SumTypeNode;
import com.pentlander.sasquach.ast.SumTypeNode.VariantTypeNode;
import com.pentlander.sasquach.ast.TupleTypeNode;
import com.pentlander.sasquach.ast.TypeStatement;
import com.pentlander.sasquach.ast.UnqualifiedName;
import com.pentlander.sasquach.ast.UnqualifiedTypeName;
import com.pentlander.sasquach.ast.Use;
import com.pentlander.sasquach.ast.expression.Function;
import com.pentlander.sasquach.ast.expression.LiteralStruct;
import com.pentlander.sasquach.ast.expression.ModuleStruct;
import com.pentlander.sasquach.ast.expression.NamedFunction;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class ModuleScopedNameResolver {
  private final Map<UnqualifiedName, ModuleScopedNameResolver> moduleImports = new HashMap<>();
  private final Map<UnqualifiedTypeName, Class<?>> foreignClasses = new HashMap<>();
  private final Map<UnqualifiedTypeName, TypeStatement> typeStatementNames = new HashMap<>();
  private final Map<NamedTypeNode, NamedTypeDefinition> namedTypeDefs = new HashMap<>();
  private final Map<UnqualifiedTypeName, VariantTypeNode> variantTypes = new HashMap<>();
  private final Map<UnqualifiedName, LiteralStruct.Field> fields = new HashMap<>();
  private final Map<LiteralStruct.Field, NameResolutionResult> fieldResults = new HashMap<>();
  private final Map<UnqualifiedName, NamedFunction> functions = new HashMap<>();
  private final Map<Function, NameResolutionResult> functionResults = new HashMap<>();
  private final RangedErrorList.Builder errors = RangedErrorList.builder();

  private final ModuleDeclaration module;
  private final ModuleResolver moduleResolver;
  private NameResolutionResult nameResolutionResult = NameResolutionResult.empty();
  private boolean typeDefsResolved = false;

  public ModuleScopedNameResolver(ModuleDeclaration module, ModuleResolver moduleResolver) {
    this.module = module;
    this.moduleResolver = moduleResolver;
  }

  public ModuleDeclaration moduleDeclaration() {
    return module;
  }

  public NameResolutionResult resolveBody() {
    resolve(module.struct());
    return nameResolutionResult.withNamedTypeDefs(namedTypeDefs).withErrors(errors.build());
  }

  public void resolveTypeDefs() {
    var struct = module.struct();

    for (var use : struct.useList()) {
      switch (use) {
        case Use.Module useModule -> resolve(useModule);
        case Use.Foreign useForeign -> resolve(useForeign);
      }
    }

    // The loops need to be separated in case there are aliases that refer to other aliases
    for (var typeStatement : struct.typeStatements()) {
      var prevStatement = typeStatementNames.put(typeStatement.id().name(), typeStatement);
      if (prevStatement != null) {
        errors.add(new DuplicateNameError(typeStatement.id(), prevStatement.id()));
      }
    }

    typeDefsResolved = true;
  }

  NameResolutionResult getResolver(LiteralStruct.Field field) {
    return Objects.requireNonNull(fieldResults.get(field));
  }

  NameResolutionResult getResolver(Function function) {
    return Objects.requireNonNull(functionResults.get(function));
  }

  private void resolve(Use.Module use) {
    var moduleScopedResolver = moduleResolver.resolveModule(use.id().name());
    if (moduleScopedResolver == null) {
      errors.add(new NameNotFoundError(use.id(), "module"));
      return;
    }
    var existingImport = moduleImports.put(use.alias().name(), moduleScopedResolver);
    if (existingImport != null) {
      errors.add(new DuplicateNameError(use.id(), existingImport.moduleDeclaration().id()));
    }
  }

  private void resolve(Use.Foreign use) {
    try {
      var qualifiedName = use.id().name().javaName();
      var clazz = MethodHandles.lookup().findClass(qualifiedName);
      foreignClasses.put(use.alias().name().toTypeName(), clazz);
    } catch (ClassNotFoundException | IllegalAccessException e) {
      errors.add(new NameNotFoundError(use.alias(), "foreign class"));
    }
  }

  public void resolve(ModuleStruct struct) {
    if (!typeDefsResolved) {
      throw new IllegalStateException("Must resolve type defs before resolving rest of module");
    }

    for (var typeStatement : struct.typeStatements()) {
      var resolver = new TypeNodeNameResolver(this);
      var result = resolver.resolveTypeNode(typeStatement);
      namedTypeDefs.putAll(result.namedTypes());
      if (!typeStatement.isAlias()) {
        switch (typeStatement.typeNode()) {
          case StructTypeNode node -> {}
          case TupleTypeNode node -> {}
          case SumTypeNode node -> node.variantTypeNodes().forEach(variantTypeNode -> {
            variantTypes.put(variantTypeNode.id().name(), variantTypeNode);
          });
          default -> throw new IllegalStateException("Must have checked in AstValidator");
        }
      }
      errors.addAll(result.errors());
    }

    for (var field : struct.fields()) {
      fields.put(field.name(), field);
    }
    for (var function : struct.functions()) {
      functions.put(function.name(), function);
    }

    for (var field : struct.fields()) {
      var resolver = new MemberScopedNameResolver(this);
      var result = resolver.resolve(field);
      nameResolutionResult = nameResolutionResult.merge(result);
      fieldResults.put(field, result);
    }
    for (var function : struct.functions()) {
      var resolver = new MemberScopedNameResolver(this);
      var result = resolver.resolve(function);
      nameResolutionResult = nameResolutionResult.merge(result);
      functionResults.put(function.function(), result);
    }
  }

  Optional<Class<?>> resolveForeignClass(UnqualifiedTypeName classAlias) {
    return Optional.ofNullable(foreignClasses.get(classAlias));
  }

  Optional<ModuleDeclaration> resolveModule(UnqualifiedName moduleAlias) {
    return Optional.ofNullable(moduleImports.get(moduleAlias))
        .map(ModuleScopedNameResolver::moduleDeclaration);
  }

  Optional<ModuleScopedNameResolver> resolveModuleResolver(UnqualifiedName moduleAlias) {
    return Optional.ofNullable(moduleImports.get(moduleAlias));
  }

  Optional<NamedFunction> resolveFunction(UnqualifiedName functionName) {
    return Optional.ofNullable(functions.get(functionName));
  }

  Optional<LiteralStruct.Field> resolveField(UnqualifiedName fieldName) {
    return Optional.ofNullable(fields.get(fieldName));
  }

  Optional<TypeStatement> resolveTypeAlias(UnqualifiedTypeName typeAlias) {
    return Optional.ofNullable(typeStatementNames.get(typeAlias));
  }

  Optional<VariantTypeNode> resolveVariantTypeNode(UnqualifiedTypeName variantName) {
    return Optional.ofNullable(variantTypes.get(variantName));
  }
}
