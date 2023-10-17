package com.pentlander.sasquach.type;

import static com.pentlander.sasquach.type.MemberScopedTypeResolver.typeParams;

import com.pentlander.sasquach.RangedErrorList;
import com.pentlander.sasquach.ast.FunctionSignature;
import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.ModuleDeclaration;
import com.pentlander.sasquach.ast.QualifiedModuleId;
import com.pentlander.sasquach.ast.SumTypeNode;
import com.pentlander.sasquach.ast.SumTypeNode.VariantTypeNode;
import com.pentlander.sasquach.ast.expression.Function;
import com.pentlander.sasquach.ast.expression.LocalFunctionCall;
import com.pentlander.sasquach.ast.expression.LocalVariable;
import com.pentlander.sasquach.ast.expression.NamedFunction;
import com.pentlander.sasquach.ast.expression.VarReference;
import com.pentlander.sasquach.name.MemberScopedNameResolver.QualifiedFunction;
import com.pentlander.sasquach.name.MemberScopedNameResolver.ReferenceDeclaration.Local;
import com.pentlander.sasquach.name.MemberScopedNameResolver.ReferenceDeclaration.Module;
import com.pentlander.sasquach.name.MemberScopedNameResolver.ReferenceDeclaration.Singleton;
import com.pentlander.sasquach.name.MemberScopedNameResolver.VariantStructConstructor;
import com.pentlander.sasquach.name.NameResolutionResult;
import com.pentlander.sasquach.tast.TModuleDeclaration;
import com.pentlander.sasquach.tast.TNamedFunction;
import com.pentlander.sasquach.tast.expression.TStruct.TField;
import com.pentlander.sasquach.tast.expression.TStructBuilder;
import com.pentlander.sasquach.tast.expression.TVarReference;
import com.pentlander.sasquach.tast.expression.TVarReference.RefDeclaration;
import com.pentlander.sasquach.type.ModuleScopedTypes.FuncCallType.LocalVar;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ModuleScopedTypeResolver {
  private final NameResolutionResult nameResolutionResult;
  private final NamedTypeResolver namedTypeResolver;
  private final ModuleDeclaration moduleDecl;
  private final ModuleTypeProvider moduleTypeProvider;

  private final List<NamedFunction> nameResolvedFunctions = new ArrayList<>();
  private final TStructBuilder typedStructBuilder = TStructBuilder.builder();
  private final Map<Identifier, FunctionType> variantConstructorTypes = new HashMap<>();

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
    var fieldTypes = new LinkedHashMap<String, Type>();
    var typedFields = new ArrayList<TField>();

    struct.typeAliases().forEach(typeAlias -> {
      // Add the types of all the sum type nodes.
      var resolvedAlias = namedTypeResolver.resolveTypeNode(typeAlias, Map.of());
      if (resolvedAlias.typeNode() instanceof SumTypeNode sumTypeNode) {
        var sumType = sumTypeNode.type();
        idTypes.put(sumTypeNode.id(), sumType);
        for (int i = 0; i < sumTypeNode.variantTypeNodes().size(); i++) {
          var variantTypeNode = sumTypeNode.variantTypeNodes().get(i);
          idTypes.put(variantTypeNode.id(), variantTypeNode.type());

          if (variantTypeNode instanceof VariantTypeNode.Tuple tuple) {
            var tupleFieldTypes = tuple.type().fieldTypes();
            var paramTypes = tupleFieldTypes.values()
                .stream()
                .sorted(Comparator.comparing(Type::typeName))
                .toList();
            var variantConstructorType = new FunctionType(paramTypes,
                sumType.typeParameters(),
                sumType);
            variantConstructorTypes.put(tuple.id(), variantConstructorType);
            fieldTypes.put(tuple.id().name(), variantConstructorType);
          } else if (variantTypeNode instanceof VariantTypeNode.Singleton singleton) {
            typedFields.add(new TField(singleton.id(),
                new TVarReference(singleton.id(),
                    new RefDeclaration.Singleton(singleton.type()),
                    sumType)));
            fieldTypes.put(singleton.id().name(), sumType);
          }
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

    var modScopedTypes = new ResolverModuleScopedTypes();
    struct.fields().forEach(field -> {
      var resolver = new MemberScopedTypeResolver(nameResolutionResult,
          moduleTypeProvider,
          modScopedTypes);
      var result = resolver.checkField(field);
      typedFields.add((TField) result.getTypedMember());
      errors.addAll(result.errors());
    });

    typedStructBuilder.name(struct.name())
        .useList(struct.useList())
        .typeAliases(namedTypeResolver.mapResolveTypeNode(struct.typeAliases()))
        .fields(typedFields)
        .structKind(struct.structKind())
        .range(struct.range());
    return struct.name()
        .map(name -> new StructType(name, fieldTypes))
        .orElseGet(() -> new StructType(fieldTypes));
  }

  interface ModuleTypeProvider {
    StructType getModuleType(QualifiedModuleId qualifiedModuleId);
  }

  public TypeResolutionResult resolveFunctions() {
    var modScopedTypes = new ResolverModuleScopedTypes();
    var typedFunctions = new ArrayList<TNamedFunction>();
    var mergedResult = nameResolvedFunctions.stream().map(func -> {
      var result = new MemberScopedTypeResolver(nameResolutionResult,
          moduleTypeProvider,
          modScopedTypes).checkType(func);
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
        param -> new UniversalType(param.typeName(), 0));
    return namedTypeResolver.resolveTypeNode(funcSignature, typeParams);
  }

  public class ResolverModuleScopedTypes implements ModuleScopedTypes {

    public FuncCallType getFunctionCallType(LocalFunctionCall funcCall) {
      var callTarget = nameResolutionResult.getLocalFunctionCallTarget(funcCall);
      return switch (callTarget) {
        case QualifiedFunction func ->
            new FuncCallType.Module((FunctionType) idTypes.get(func.id()));
        case VariantStructConstructor constructor ->
            new FuncCallType.Module(variantConstructorTypes.get(constructor.id()));
        case LocalVariable localVar -> new LocalVar(localVar);
      };
    }

    public VarRefType getVarReferenceType(VarReference varRef) {
      return switch (nameResolutionResult.getVarReference(varRef)) {
        case Local local -> new VarRefType.LocalVar(local.localVariable());
        case Module(var modDecl) ->
            new VarRefType.Module(modDecl.id(), moduleTypeProvider.getModuleType(modDecl.id()));
        case Singleton(var singletonNode) -> {
          var type = (SumType) idTypes.get(singletonNode.aliasId());
          var params = typeParams(type.typeParameters(),
              param -> new TypeVariable(param.typeName() + "_" + varRef.id().hashCode()));
          var resolvedType = namedTypeResolver.resolveNames(type, params, singletonNode.range());
          yield new VarRefType.Singleton((SumType) resolvedType, singletonNode.type());
        }
      };
    }
  }
}
