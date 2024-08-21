package com.pentlander.sasquach.type;

import static com.pentlander.sasquach.type.MemberScopedTypeResolver.typeParams;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.RangedErrorList;
import com.pentlander.sasquach.ast.FunctionSignature;
import com.pentlander.sasquach.ast.Id;
import com.pentlander.sasquach.ast.ModuleDeclaration;
import com.pentlander.sasquach.ast.SumTypeNode;
import com.pentlander.sasquach.ast.SumTypeNode.VariantTypeNode;
import com.pentlander.sasquach.ast.TypeId;
import com.pentlander.sasquach.ast.UnqualifiedName;
import com.pentlander.sasquach.ast.expression.Function;
import com.pentlander.sasquach.ast.expression.LocalFunctionCall;
import com.pentlander.sasquach.ast.expression.LocalVariable;
import com.pentlander.sasquach.ast.expression.ModuleStruct;
import com.pentlander.sasquach.ast.expression.NamedFunction;
import com.pentlander.sasquach.ast.expression.NamedStruct;
import com.pentlander.sasquach.ast.expression.VarReference;
import com.pentlander.sasquach.name.MemberScopedNameResolver.QualifiedFunction;
import com.pentlander.sasquach.name.MemberScopedNameResolver.ReferenceDeclaration.Local;
import com.pentlander.sasquach.name.MemberScopedNameResolver.ReferenceDeclaration.Module;
import com.pentlander.sasquach.name.MemberScopedNameResolver.ReferenceDeclaration.Singleton;
import com.pentlander.sasquach.name.MemberScopedNameResolver.VariantStructConstructor;
import com.pentlander.sasquach.name.NameResolutionData.NamedStructId;
import com.pentlander.sasquach.name.NameResolutionResult;
import com.pentlander.sasquach.tast.TModuleDeclaration;
import com.pentlander.sasquach.tast.TNamedFunction;
import com.pentlander.sasquach.tast.expression.TModuleStructBuilder;
import com.pentlander.sasquach.tast.expression.TStruct.TField;
import com.pentlander.sasquach.tast.expression.TVarReference;
import com.pentlander.sasquach.tast.expression.TVarReference.RefDeclaration;
import com.pentlander.sasquach.type.ModuleScopedTypes.FuncCallType.LocalVar;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public class ModuleScopedTypeResolver {
  private final NameResolutionResult nameResolutionResult;
  private final NamedTypeResolver namedTypeResolver;
  private final ModuleDeclaration moduleDecl;
  private final ModuleTypeProvider moduleTypeProvider;

  private final List<NamedFunction> nameResolvedFunctions = new ArrayList<>();
  private final TModuleStructBuilder typedStructBuilder = TModuleStructBuilder.builder();
  private final Map<TypeId, FunctionType> variantConstructorTypes = new HashMap<>();

  private final Map<TypeId, Type> namedTypeIdToType = new HashMap<>();
  private final Map<Id, FunctionType> funcIdToType = new HashMap<>();
  private final RangedErrorList.Builder errors = RangedErrorList.builder();


  public ModuleScopedTypeResolver(NameResolutionResult nameResolutionResult,
      ModuleDeclaration moduleDeclaration, ModuleTypeProvider moduleTypeProvider) {
    this.nameResolutionResult = nameResolutionResult;
    this.namedTypeResolver = new NamedTypeResolver(nameResolutionResult);
    this.moduleDecl = moduleDeclaration;
    this.moduleTypeProvider = moduleTypeProvider;
  }

  public StructType resolveModuleType() {
    var struct = (ModuleStruct) moduleDecl.struct();
    var fieldTypes = new LinkedHashMap<UnqualifiedName, Type>();
    var typedFields = new ArrayList<TField>();

    struct.typeAliases().forEach(typeAlias -> {
      // Add the types of all the sum type nodes.
      var resolvedAlias = namedTypeResolver.resolveTypeNode(typeAlias, Map.of());
      if (resolvedAlias.typeNode() instanceof SumTypeNode sumTypeNode) {
        var sumType = sumTypeNode.type();
        namedTypeIdToType.put(sumTypeNode.id(), sumType);
        for (int i = 0; i < sumTypeNode.variantTypeNodes().size(); i++) {
          var variantTypeNode = sumTypeNode.variantTypeNodes().get(i);
          namedTypeIdToType.put(variantTypeNode.id(), variantTypeNode.type());

          switch (variantTypeNode) {
            case VariantTypeNode.Tuple tuple -> {
              var paramTypes = tuple.type().sortedFieldTypes();
              var variantConstructorType = new FunctionType(paramTypes,
                  sumType.typeParameters(),
                  sumType);
              var typeId = tuple.id();
              variantConstructorTypes.put(typeId, variantConstructorType);
//              fieldTypes.put(typeId.toId().name(), variantConstructorType);
            }
            case VariantTypeNode.Singleton singleton -> {
              var id = singleton.id().toId();
              typedFields.add(new TField(id,
                  new TVarReference(id, new RefDeclaration.Singleton(singleton.type()), sumType)));
//              fieldTypes.put(id.name(), sumType);
            }
            case VariantTypeNode.Struct strct -> {
              // TODO need to somehow replace the SumType in NameResolutionResult's namedStructTypes
              // with the named resolved one here
            }
          }
        }
      }
    });
    struct.functions().forEach(func -> {
      var funcSig = resolveFuncSignatureType(func.functionSignature());
      var type = funcSig.type();
      fieldTypes.put(func.name(), type);
      funcIdToType.put(func.id(), type);
      nameResolvedFunctions.add(new NamedFunction(func.id(),
          new Function(funcSig, func.expression())));
    });

    var modScopedTypes = new ResolverModuleScopedTypes();
    struct.fields().forEach(field -> {
      var resolver = new MemberScopedTypeResolver(nameResolutionResult, modScopedTypes);
      var result = resolver.checkField(field);
      typedFields.add((TField) result.getTypedMember());
      errors.addAll(result.errors());
    });

    var typeAliases = namedTypeResolver.mapResolveTypeNode(struct.typeAliases());

    typedStructBuilder.name(struct.name())
        .useList(struct.useList())
        .typeAliases(typeAliases)
        .fields(typedFields)
        .range(struct.range());

    var namedStructTypes = typeAliases.stream()
        .flatMap(alias -> alias.type() instanceof SumType sumType ? Stream.of(sumType)
            : Stream.empty())
        .collect(toMap(sumType -> sumType.qualifiedTypeName().name(), identity()));

    return new StructType(struct.name(), fieldTypes, namedStructTypes);
  }

  public TypeResolutionResult resolveFunctions() {
    var modScopedTypes = new ResolverModuleScopedTypes();
    var typedFunctions = new ArrayList<TNamedFunction>();
    var mergedResult = nameResolvedFunctions.stream().map(func -> {
      var result = new MemberScopedTypeResolver(nameResolutionResult, modScopedTypes).checkType(func);
      typedFunctions.add((TNamedFunction) result.getTypedMember());
      return result;
    }).reduce(TypeResolutionResult.EMPTY, TypeResolutionResult::merge);

    var typedModuleDecl = new TModuleDeclaration(moduleDecl.id(),
        typedStructBuilder.functions(typedFunctions).build(),
        moduleDecl.range());
    var typedModules = Map.of(moduleDecl.id(), typedModuleDecl);
    // Need to include te map of typevars in this result
    return TypeResolutionResult.ofTypedModules(typedModules, errors.build()).merge(mergedResult);
  }

  FunctionSignature resolveFuncSignatureType(FunctionSignature funcSignature) {
    var typeParams = typeParams(funcSignature.typeParameters(),
        param -> param.toUniversal(0));
    return namedTypeResolver.resolveTypeNode(funcSignature, typeParams);
  }

  private SumType convertUniversals(SumType type, Id id, Range range) {
    var params = typeParamToVar(type.typeParameters(), id.hashCode());
    return namedTypeResolver.resolveNames(type, params, range);
  }

  private static Map<String, Type> typeParamToVar(List<TypeParameter> typeParams, int level) {
    return typeParams(typeParams, param -> param.toTypeVariable(level));
  }

  public class ResolverModuleScopedTypes implements ModuleScopedTypes {

    @Override
    public FuncCallType getFunctionCallType(LocalFunctionCall funcCall) {
      var callTarget = nameResolutionResult.getLocalFunctionCallTarget(funcCall);
      return switch (callTarget) {
        case QualifiedFunction func ->
            new FuncCallType.Module(funcIdToType.get(func.id()));
        case VariantStructConstructor constructor ->
            new FuncCallType.Module(variantConstructorTypes.get(constructor.id()));
        case LocalVariable localVar -> new LocalVar(localVar);
      };
    }

    @Override
    public VarRefType getVarReferenceType(VarReference varRef) {
      return switch (nameResolutionResult.getVarReference(varRef)) {
        case Local local -> new VarRefType.LocalVar(local.localVariable());
        case Module(var modDecl) ->
            new VarRefType.Module(modDecl.id(), moduleTypeProvider.getModuleType(modDecl.id()));
        case Singleton(var singletonNode) -> {
          var type = (SumType) namedTypeIdToType.get(singletonNode.aliasId());
          var resolvedType = convertUniversals(type, varRef.id(), singletonNode.range());
          yield new VarRefType.Singleton(resolvedType, singletonNode.type());
        }
      };
    }

    @Override
    public SumWithVariantIdx getVariantType(NamedStruct namedStruct) {
      var namedStructType = nameResolutionResult.getNamedStructType(namedStruct);

      switch (namedStructType) {
        case NamedStructId.Variant(var sumTypeId, var variantId) -> {
          var sumType = (SumType) requireNonNull(namedTypeIdToType.get(sumTypeId));

          Integer variantIdx = null;
          for (int i = 0; i < sumType.types().size(); i++) {
            var type = sumType.types().get(i);
            if (type.typeNameStr().endsWith(variantId.name().toString())) {
              variantIdx = i;
              break;
            }
          }

          return new SumWithVariantIdx(sumType, requireNonNull(variantIdx));
        }
      }
    }
  }
}
