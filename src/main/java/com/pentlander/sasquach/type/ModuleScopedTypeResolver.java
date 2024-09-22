package com.pentlander.sasquach.type;

import static com.pentlander.sasquach.type.MemberScopedTypeResolver.typeParams;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import com.pentlander.sasquach.AbstractRangedError;
import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.RangedErrorList;
import com.pentlander.sasquach.RangedErrorList.Builder;
import com.pentlander.sasquach.Source;
import com.pentlander.sasquach.ast.FunctionSignature;
import com.pentlander.sasquach.ast.ModuleDeclaration;
import com.pentlander.sasquach.ast.StructName;
import com.pentlander.sasquach.ast.StructTypeNode;
import com.pentlander.sasquach.ast.SumTypeNode;
import com.pentlander.sasquach.ast.TupleTypeNode;
import com.pentlander.sasquach.ast.UnqualifiedName;
import com.pentlander.sasquach.ast.UnqualifiedTypeName;
import com.pentlander.sasquach.ast.expression.Function;
import com.pentlander.sasquach.ast.expression.LocalFunctionCall;
import com.pentlander.sasquach.ast.expression.LocalVariable;
import com.pentlander.sasquach.ast.expression.ModuleStruct;
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
import com.pentlander.sasquach.tast.expression.TModuleStructBuilder;
import com.pentlander.sasquach.tast.expression.TStruct.TField;
import com.pentlander.sasquach.type.ModuleScopedTypes.FuncCallType.LocalVar;
import java.lang.constant.ClassDesc;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;

public class ModuleScopedTypeResolver {
  private final NameResolutionResult nameResolutionResult;
  private final NamedTypeResolver namedTypeResolver;
  private final ModuleDeclaration moduleDecl;
  private final ModuleTypeProvider moduleTypeProvider;

  private final List<NamedFunction> nameResolvedFunctions = new ArrayList<>();
  private final TModuleStructBuilder typedStructBuilder = TModuleStructBuilder.builder();
  private final Map<StructTypeKey, StructName> structTypeNames = new LinkedHashMap<>();
  private final Builder errors = RangedErrorList.builder();

  private StructType thisStructType;


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

    struct.typeStatements().forEach(typeAlias -> {
      // Add the types of all the sum type nodes.
      var resolvedAlias = namedTypeResolver.resolveTypeNode(typeAlias, Map.of());
      switch (resolvedAlias.typeNode()) {
        case SumTypeNode sumTypeNode -> {
          var sumType = sumTypeNode.type();
          for (int i = 0; i < sumTypeNode.variantTypeNodes().size(); i++) {
            var variantTypeNode = sumTypeNode.variantTypeNodes().get(i);
            var name = variantTypeNode.typeName().name().toName();
            var funcType = variantTypeNode.type().constructorType(sumType);
            fieldTypes.put(name, funcType);
          }
        }
        case StructTypeNode typeNode -> {
          var name = typeNode.typeName().name().toName();
          fieldTypes.put(name, typeNode.type().constructorType());
        }
        case TupleTypeNode typeNode -> {
          var name = typeNode.typeName().name().toName();
          fieldTypes.put(name, typeNode.type().constructorType());
        }
        case null, default -> {}
      }
    });
    struct.functions().forEach(func -> {
      // TODO Need to validate the the function signature has type annotations
      var funcSig = func.functionSignature();
      funcSig.parameters().forEach(param -> {
        if (param.typeNode() == null) {
          errors.add(new TypeAnnotationRequiredError("parameter", param.range()));
        }
      });
      if (funcSig.returnTypeNode() == null) {
        errors.add(new TypeAnnotationRequiredError("return", funcSig.range()));
      }

      var resolvedFuncSig = resolveFuncSignatureType(funcSig);
      var type = resolvedFuncSig.type();
      fieldTypes.put(func.name(), type);
      nameResolvedFunctions.add(new NamedFunction(func.id(),
          new Function(resolvedFuncSig, func.expression())));
    });

    var modScopedTypes = new ResolverModuleScopedTypes();
    struct.fields().forEach(field -> {
      var resolver = new MemberScopedTypeResolver(nameResolutionResult, modScopedTypes);
      var result = resolver.checkField(field);
      typedFields.add((TField) result.getTypedMember());
      errors.addAll(result.errors());
    });

    var typeStatements = namedTypeResolver.mapResolveTypeNode(struct.typeStatements());

    typedStructBuilder.name(struct.name())
        .useList(struct.useList())
        .typeStatements(typeStatements)
        .fields(typedFields)
        .range(struct.range());

    var namedStructTypes = typeStatements.stream()
        .flatMap(alias -> alias.type() instanceof SumType sumType ? Stream.of(sumType)
            : Stream.empty())
        .collect(toMap(sumType -> sumType.qualifiedTypeName().name(), identity()));

    thisStructType = new StructType(struct.name(), fieldTypes, namedStructTypes);
    return thisStructType;
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
    var typeParams = typeParams(funcSignature.typeParameters(), TypeParameter::toUniversal);
    return namedTypeResolver.resolveTypeNode(funcSignature, typeParams);
  }

  public class ResolverModuleScopedTypes implements ModuleScopedTypes {
    @Override
    public StructType getThisType() {
      return requireNonNull(thisStructType);
    }

    @Override
    public StructName getLiteralStructName(Map<UnqualifiedName, Type> memberTypes) {
      return structTypeNames.computeIfAbsent(StructTypeKey.from(memberTypes), _ -> moduleDecl.name()
          .qualifyInner(new UnqualifiedTypeName(Integer.toString(structTypeNames.size()))));
    }

    @Override
    public FuncCallType getFunctionCallType(LocalFunctionCall funcCall) {
      var callTarget = nameResolutionResult.getLocalFunctionCallTarget(funcCall);
      return switch (callTarget) {
        case QualifiedFunction _, VariantStructConstructor _ -> new FuncCallType.Module();
        case LocalVariable localVar -> new LocalVar(localVar);
      };
    }

    @Override
    public VarRefType getVarReferenceType(VarReference varRef) {
      return switch (nameResolutionResult.getVarReference(varRef)) {
        case Local local -> new VarRefType.LocalVar(local.localVariable());
        case Module(var modDecl) ->
            new VarRefType.Module(modDecl.id(), moduleTypeProvider.getModuleType(modDecl.id()));
        case Singleton _ -> new VarRefType.Singleton();
      };
    }

  }

  private record StructTypeKey(List<Entry<UnqualifiedName, ClassDesc>> memberTypes) {
    static StructTypeKey from(Map<UnqualifiedName, Type> memberTypes) {
      var memberList = memberTypes.entrySet()
          .stream()
          .sorted(Entry.comparingByKey())
          .map(entry -> Map.entry(entry.getKey(), entry.getValue().classDesc()))
          .toList();
      return new StructTypeKey(memberList);
    }
  }

  static class TypeAnnotationRequiredError extends AbstractRangedError {
    public TypeAnnotationRequiredError(
        String nodeKind, Range range
    ) {
      super("Type annotation required for function " + nodeKind, range, null);
    }

    @Override
    public String toPrettyString(Source source) {
      return """
          %s
          %s
          """.formatted(getMessage(), source.highlight(range));
    }
  }
}
