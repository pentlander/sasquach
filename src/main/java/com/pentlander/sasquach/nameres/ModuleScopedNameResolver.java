package com.pentlander.sasquach.nameres;

import com.pentlander.sasquach.RangedErrorList;
import com.pentlander.sasquach.ast.ModuleDeclaration;
import com.pentlander.sasquach.ast.NamedTypeDefinition;
import com.pentlander.sasquach.ast.Use;
import com.pentlander.sasquach.ast.expression.ModuleStruct;
import com.pentlander.sasquach.ast.expression.NamedFunction;
import com.pentlander.sasquach.ast.typenode.ConstructableNamedTypeNode;
import com.pentlander.sasquach.ast.typenode.StructTypeNode;
import com.pentlander.sasquach.ast.typenode.SumTypeNode;
import com.pentlander.sasquach.ast.typenode.TupleTypeNode;
import com.pentlander.sasquach.ast.typenode.TypeStatement;
import com.pentlander.sasquach.name.QualifiedTypeName;
import com.pentlander.sasquach.name.StructName;
import com.pentlander.sasquach.name.StructName.SyntheticName;
import com.pentlander.sasquach.name.UnqualifiedName;
import com.pentlander.sasquach.name.UnqualifiedTypeName;
import com.pentlander.sasquach.type.NamedType;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ModuleScopedNameResolver {
  private static Lookup LOOKUP = MethodHandles.lookup();
  private final Map<UnqualifiedName, ModuleScopedNameResolver> moduleImports = new HashMap<>();
  private final Map<QualifiedTypeName, Class<?>> foreignClasses = new HashMap<>();
  private final Map<QualifiedTypeName, TypeStatement> typeStatementNames = new HashMap<>();
  private final Map<NamedType, NamedTypeDefinition> namedTypeDefs = new HashMap<>();
  private final Map<UnqualifiedTypeName, ConstructableNamedTypeNode> constructableTypes = new HashMap<>();
  private final Map<UnqualifiedName, NamedFunction> functions = new HashMap<>();
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

  void resolveTypeStatements() {
    var struct = module.struct();
    for (var typeStatement : struct.typeStatements()) {
      var resolver = new TypeNodeNameResolver(this);
      var result = resolver.resolveTypeNode(typeStatement);
      namedTypeDefs.putAll(result.namedTypes());
      if (!typeStatement.isAlias()) {
        switch (typeStatement.typeNode()) {
          case StructTypeNode node -> constructableTypes.put(node.typeName().simpleName(), node);
          case TupleTypeNode node -> constructableTypes.put(node.typeName().simpleName(), node);
          case SumTypeNode node -> node.variantTypeNodes()
              .forEach(variantTypeNode -> constructableTypes.put(
                  variantTypeNode.typeName().simpleName(),
                  variantTypeNode));
          default -> throw new IllegalStateException("Must have checked in AstValidator");
        }
      }
      errors.addAll(result.errors());
    }
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
      var qualifiedName = use.id().name();
      var clazz = LOOKUP.findClass(qualifiedName.javaName());
      foreignClasses.put(qualifiedName.toQualifiedTypeName(), clazz);
    } catch (ClassNotFoundException | IllegalAccessException e) {
      errors.add(new NameNotFoundError(use.alias(), "foreign class"));
    }
  }

  public void resolve(ModuleStruct struct) {
    if (!typeDefsResolved) {
      throw new IllegalStateException("Must resolve type defs before resolving rest of module");
    }

//    for (var field : struct.fields()) {
//      fields.put(field.name(), field);
//    }
    for (var function : struct.functions()) {
      functions.put(function.name(), function);
    }

    for (var field : struct.fields()) {
      var resolver = new MemberScopedNameResolver(this);
      var result = resolver.resolve(field);
      nameResolutionResult = nameResolutionResult.merge(result);
    }
    for (var function : struct.functions()) {
      var resolver = new MemberScopedNameResolver(this);
      var result = resolver.resolve(function);
      nameResolutionResult = nameResolutionResult.merge(result);
    }
  }


  Optional<Class<?>> resolveForeignClass(QualifiedTypeName classAlias) {
    return Optional.ofNullable(foreignClasses.get(classAlias));
  }

  Optional<ModuleDeclaration> resolveModule(UnqualifiedName moduleAlias) {
    return getModuleNameResolver(moduleAlias)
        .map(ModuleScopedNameResolver::moduleDeclaration);
  }

  private Optional<ModuleScopedNameResolver> getModuleNameResolver(UnqualifiedName moduleAlias) {
    return Optional.ofNullable(moduleImports.get(moduleAlias));
  }

  Optional<NamedFunction> resolveFunction(UnqualifiedName functionName) {
    return Optional.ofNullable(functions.get(functionName));
  }

  Optional<TypeStatement> resolveTypeAlias(QualifiedTypeName typeAlias) {
    var moduleName = typeAlias.qualifiedModuleName();
    if (module.name().equals(moduleName)) {
      return Optional.ofNullable(typeStatementNames.get(typeAlias));
    }

    return getModuleNameResolver(moduleName.simpleName())
        .flatMap(resolver -> resolver.resolveTypeAlias(typeAlias));
  }

  Optional<ConstructableNamedTypeNode> resolveConstructableTypeNode(StructName structTypeName) {
    return switch (structTypeName) {
      case QualifiedTypeName qualName -> {
        var moduleName = qualName.qualifiedModuleName();
        if (module.name().equals(moduleName)) {
          yield Optional.ofNullable(constructableTypes.get(qualName.simpleName()));
        }
        yield getModuleNameResolver(moduleName.simpleName()).flatMap(resolver -> resolver.resolveConstructableTypeNode(
            structTypeName));
      }
      case UnqualifiedTypeName name -> Optional.ofNullable(constructableTypes.get(name));
      case SyntheticName _ -> throw new IllegalStateException();
    };
  }
}
