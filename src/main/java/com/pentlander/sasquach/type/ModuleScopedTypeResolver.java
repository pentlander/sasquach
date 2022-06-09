package com.pentlander.sasquach.type;

import com.pentlander.sasquach.RangedErrorList;
import com.pentlander.sasquach.ast.FunctionSignature;
import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.ModuleDeclaration;
import com.pentlander.sasquach.ast.QualifiedIdentifier;
import com.pentlander.sasquach.ast.SumTypeNode;
import com.pentlander.sasquach.name.NameResolutionResult;
import java.util.HashMap;
import java.util.Map;

public class ModuleScopedTypeResolver {
  private final NameResolutionResult nameResolutionResult;
  private final NamedTypeResolver namedTypeResolver;
  private final ModuleDeclaration moduleDecl;
  private final ModuleTypeProvider moduleTypeProvider;

  private final Map<Identifier, Type> idTypes = new HashMap<>();
  private final RangedErrorList.Builder errors = RangedErrorList.builder();

  public ModuleScopedTypeResolver(NameResolutionResult nameResolutionResult,
      ModuleDeclaration moduleDeclaration, ModuleTypeProvider moduleTypeProvider) {
    this.nameResolutionResult = nameResolutionResult;
    this.namedTypeResolver = new NamedTypeResolver(nameResolutionResult);
    this.moduleDecl = moduleDeclaration;
    this.moduleTypeProvider = moduleTypeProvider;
  }

  public StructType resolveModuleType() {
    var struct = moduleDecl.struct();
    var fieldTypes = new HashMap<String, Type>();
    struct.typeAliases().forEach(typeAlias -> {
      // Add the types of all the sum type nodes.
      if (typeAlias.typeNode() instanceof SumTypeNode sumTypeNode) {
        var typeParams = MemberScopedTypeResolver.typeParams(typeAlias.typeParameters(),
            param -> new TypeVariable(param.typeName()));
        var sumType = (SumType) namedTypeResolver.resolveNamedType(sumTypeNode.type(),
            typeParams,
            sumTypeNode.range());
        idTypes.put(sumTypeNode.id(), sumType);
        for (int i = 0; i < sumTypeNode.variantTypeNodes().size(); i++) {
          var variantTypeNode = sumTypeNode.variantTypeNodes().get(i);
          var type = namedTypeResolver.resolveNamedType(variantTypeNode.type(),
              typeParams,
              variantTypeNode.range());
          idTypes.put(variantTypeNode.id(), type);
        }
      }
    });
    struct.functions().forEach(func -> {
      var type = resolveFuncSignatureType(func.functionSignature());
      fieldTypes.put(func.name(), type);
      idTypes.put(func.id(), type);
    });
    struct.fields()
        .forEach(field -> {
          var resolver = new MemberScopedTypeResolver(idTypes, nameResolutionResult,
              moduleTypeProvider);
          var result = resolver.inferType(field);
          errors.addAll(result.errors());
        });
    return struct.name().map(name -> new StructType(name, fieldTypes))
        .orElseGet(() -> new StructType(fieldTypes));
  }

  interface ModuleTypeProvider {
    StructType getModuleType(QualifiedIdentifier qualifiedIdentifier);
  }

  public TypeResolutionResult resolveFunctions() {
    var mergedResult = moduleDecl.struct().functions().stream()
        .map(func -> new MemberScopedTypeResolver(idTypes, nameResolutionResult,
            moduleTypeProvider).checkType(func))
        .reduce(TypeResolutionResult.EMPTY, TypeResolutionResult::merge);
    return new TypeResolutionResult(
        idTypes,
        Map.of(),
        Map.of(),
        errors.build()).merge(mergedResult);
  }

  FunctionType resolveFuncSignatureType(FunctionSignature funcSignature) {
    var typeParams = MemberScopedTypeResolver.typeParams(funcSignature.typeParameters(),
        param -> new UniversalType(param.typeName(), 0));
    return TypeUtils.asFunctionType(namedTypeResolver.resolveNamedType(funcSignature.type(),
        typeParams, funcSignature.range())).orElseThrow();
  }
}
