package com.pentlander.sasquach.type;

import com.pentlander.sasquach.RangedErrorList;
import com.pentlander.sasquach.ast.FunctionSignature;
import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.ModuleDeclaration;
import com.pentlander.sasquach.ast.QualifiedIdentifier;
import com.pentlander.sasquach.ast.SumTypeNode;
import com.pentlander.sasquach.ast.expression.Function;
import com.pentlander.sasquach.ast.expression.NamedFunction;
import com.pentlander.sasquach.name.NameResolutionResult;
import com.pentlander.sasquach.tast.expression.TypedExprWrapper;
import com.pentlander.sasquach.tast.expression.TypedStruct.TypedField;
import com.pentlander.sasquach.tast.expression.TypedStructBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModuleScopedTypeResolver {
  private final NameResolutionResult nameResolutionResult;
  private final NamedTypeResolver namedTypeResolver;
  private final ModuleDeclaration moduleDecl;
  private final ModuleTypeProvider moduleTypeProvider;

  private final List<NamedFunction> nameResolvedFunctions = new ArrayList<>();

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

    // Change all of the for loops to use `resolveTypeNode and construct a TypedStruct

    struct.typeAliases().forEach(typeAlias -> {
      // Add the types of all the sum type nodes.
      if (typeAlias.typeNode() instanceof SumTypeNode sumTypeNode) {
        var typeParams = MemberScopedTypeResolver.typeParams(typeAlias.typeParameters(),
            param -> new UniversalType(param.typeName(), 0));
        var sumType = (SumType) namedTypeResolver.resolveNames(sumTypeNode.type(),
            typeParams,
            sumTypeNode.range());
        idTypes.put(sumTypeNode.id(), sumType);
        for (int i = 0; i < sumTypeNode.variantTypeNodes().size(); i++) {
          var variantTypeNode = sumTypeNode.variantTypeNodes().get(i);
          var type = namedTypeResolver.resolveNames(variantTypeNode.type(),
              typeParams,
              variantTypeNode.range());
          idTypes.put(variantTypeNode.id(), type);
        }
      }
    });
    struct.functions().forEach(func -> {
      var funcSig = resolveFuncSignatureType(func.functionSignature());
      var type = funcSig.type();
      fieldTypes.put(func.name(), type);
      idTypes.put(func.id(), type);
      nameResolvedFunctions.add(new NamedFunction(func.id(),
          new Function(funcSig, func.expression())));
    });

    var typedFields = new ArrayList<TypedField>();
    struct.fields().forEach(field -> {
      var resolver = new MemberScopedTypeResolver(idTypes,
          nameResolutionResult,
          moduleTypeProvider);
      var result = resolver.inferType(field);
      var typedField = new TypedField(field.id(),
          new TypedExprWrapper(field.value(), result.getType(field)));
      typedFields.add(typedField);
      errors.addAll(result.errors());
    });
    var typedStruct = TypedStructBuilder.builder()
        .name(struct.name())
        .useList(struct.useList())
        .typeAliases(namedTypeResolver.mapResolveTypeNode(struct.typeAliases()))
        .fields(typedFields)
        .functions(nameResolvedFunctions)
        .build();
    var structType = struct.name()
        .map(name -> new StructType(name, fieldTypes))
        .orElseGet(() -> new StructType(fieldTypes));
    if (!typedStruct.type().equals(structType)) {
      throw new IllegalStateException("Type %s not assignable from %s".formatted(typedStruct.type(),
          structType));
    }
    return structType;
  }

  interface ModuleTypeProvider {
    StructType getModuleType(QualifiedIdentifier qualifiedIdentifier);
  }

  public TypeResolutionResult resolveFunctions() {
    var mergedResult = nameResolvedFunctions.stream()
        .map(func -> new MemberScopedTypeResolver(idTypes,
            nameResolutionResult,
            moduleTypeProvider).checkType(func))
        .reduce(TypeResolutionResult.EMPTY, TypeResolutionResult::merge);
    return new TypeResolutionResult(idTypes,
        Map.of(),
        Map.of(),
        errors.build()).merge(mergedResult);
  }

  FunctionSignature resolveFuncSignatureType(FunctionSignature funcSignature) {
    var typeParams = MemberScopedTypeResolver.typeParams(funcSignature.typeParameters(),
        param -> new UniversalType(param.typeName(), 0));
    return namedTypeResolver.resolveTypeNode(funcSignature, typeParams);
  }
}
