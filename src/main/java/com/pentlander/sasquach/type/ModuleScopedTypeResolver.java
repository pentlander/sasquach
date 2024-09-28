package com.pentlander.sasquach.type;

import static java.util.Objects.requireNonNull;

import com.pentlander.sasquach.AbstractRangedError;
import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.RangedErrorList;
import com.pentlander.sasquach.RangedErrorList.Builder;
import com.pentlander.sasquach.Source;
import com.pentlander.sasquach.ast.ModuleDeclaration;
import com.pentlander.sasquach.ast.expression.LocalFunctionCall;
import com.pentlander.sasquach.ast.expression.LocalVariable;
import com.pentlander.sasquach.ast.expression.ModuleStruct;
import com.pentlander.sasquach.ast.expression.NamedFunction;
import com.pentlander.sasquach.ast.expression.VarReference;
import com.pentlander.sasquach.name.QualifiedModuleName;
import com.pentlander.sasquach.name.StructName;
import com.pentlander.sasquach.name.StructName.SyntheticName;
import com.pentlander.sasquach.name.UnqualifiedName;
import com.pentlander.sasquach.name.UnqualifiedTypeName;
import com.pentlander.sasquach.nameres.MemberScopedNameResolver.QualifiedFunction;
import com.pentlander.sasquach.nameres.MemberScopedNameResolver.ReferenceDeclaration.Local;
import com.pentlander.sasquach.nameres.MemberScopedNameResolver.ReferenceDeclaration.Module;
import com.pentlander.sasquach.nameres.MemberScopedNameResolver.ReferenceDeclaration.Singleton;
import com.pentlander.sasquach.nameres.MemberScopedNameResolver.VariantStructConstructor;
import com.pentlander.sasquach.nameres.NameResolutionResult;
import com.pentlander.sasquach.tast.TModuleDeclaration;
import com.pentlander.sasquach.tast.TNamedFunction;
import com.pentlander.sasquach.tast.expression.TModuleStruct.TypeDef;
import com.pentlander.sasquach.tast.expression.TModuleStructBuilder;
import com.pentlander.sasquach.tast.expression.TStruct.TField;
import com.pentlander.sasquach.type.ModuleScopedTypes.FuncCallType.LocalVar;
import java.lang.constant.ClassDesc;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.jspecify.annotations.Nullable;

public class ModuleScopedTypeResolver {
  private final NameResolutionResult nameResolutionResult;
  private final NamedTypeResolver namedTypeResolver;
  private final ModuleDeclaration moduleDecl;
  private final ModuleTypeProvider moduleTypeProvider;

  private final List<ResolvedFunctionType> nameResolvedFuncTypes = new ArrayList<>();
  private final TModuleStructBuilder typedStructBuilder = TModuleStructBuilder.builder();
  private final Map<StructTypeKey, StructName> structTypeNames = new LinkedHashMap<>();
  private final Builder errors = RangedErrorList.builder();

  private @Nullable StructType thisStructType;


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
      var typeArgs = TypeUtils.typeParamsToUniversal(typeAlias.typeParameters());
      var resolvedAliasType = namedTypeResolver.resolveNames(typeAlias, typeArgs);
      switch (resolvedAliasType) {
        case SumType sumType -> {
          for (var variantType : sumType.types()) {
            var name = variantType.name().simpleName().toName();
            var funcType = variantType.constructorType(sumType);
            fieldTypes.put(name, funcType);
          }
        }
        case StructType structType -> {
          var name = structType.name().simpleName().toName();
          fieldTypes.put(name, structType.constructorType());
        }
        default -> {}
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

      var funcTypeParams = TypeUtils.typeParamsToUniversal(funcSig.typeParameters());
      var funcType = namedTypeResolver.resolveNames(funcSig.type(),
          funcTypeParams,
          func.range());
      fieldTypes.put(func.name(), funcType);

      nameResolvedFuncTypes.add(new ResolvedFunctionType(func, funcType));
    });

    var modScopedTypes = new ResolverModuleScopedTypes();
    struct.fields().forEach(field -> {
      var resolver = new MemberScopedTypeResolver(nameResolutionResult, modScopedTypes);
      var result = resolver.checkField(field);
      typedFields.add((TField) result.getTypedMember());
      errors.addAll(result.errors());
    });

    var typeDefs = struct.typeStatements().stream().map(stmt -> {
      var typeNode = stmt.typeNode();
      var typeParams = TypeUtils.typeParamsToUniversal(stmt.typeParameters());
      var resolvedType = namedTypeResolver.resolveNames(typeNode, typeParams);
      return new TypeDef(resolvedType, stmt.range().sourcePath());
    }).toList();

    typedStructBuilder.name(struct.moduleName())
        .typeDefs(typeDefs)
        .fields(typedFields)
        .range(struct.range());

    thisStructType = new StructType(struct.name(), fieldTypes);
    return thisStructType;
  }

  public TypeResolutionResult resolveFunctions() {
    var modScopedTypes = new ResolverModuleScopedTypes();
    var typedFunctions = new ArrayList<TNamedFunction>();
    var mergedResult = nameResolvedFuncTypes.stream().map(func -> {
      var result = new MemberScopedTypeResolver(nameResolutionResult, modScopedTypes).checkType(func.func(), func.type());
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

  public StructName getLiteralStructName(Map<UnqualifiedName, Type> memberTypes) {
    return structTypeNames.computeIfAbsent(StructTypeKey.from(memberTypes), _ -> {
      var unqualifiedName = new UnqualifiedTypeName(Integer.toString(structTypeNames.size()));
      return new SyntheticName(moduleDecl.name().qualifyInner(unqualifiedName));
    });
  }

  private record ResolvedFunctionType(NamedFunction func, FunctionType type) {}

  public class ResolverModuleScopedTypes implements ModuleScopedTypes {
    @Override
    public StructType getThisType() {
      return requireNonNull(thisStructType);
    }

    @Override
    public StructName getLiteralStructName(Map<UnqualifiedName, Type> memberTypes) {
      return ModuleScopedTypeResolver.this.getLiteralStructName(memberTypes);
    }

    @Override
    public StructType getModuleType(QualifiedModuleName moduleName) {
      return moduleTypeProvider.getModuleType(moduleName);
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
            new VarRefType.Module(modDecl.id(), moduleTypeProvider.getModuleType(modDecl.name()));
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
