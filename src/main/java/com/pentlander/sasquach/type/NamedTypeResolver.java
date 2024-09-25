package com.pentlander.sasquach.type;

import static com.pentlander.sasquach.Util.toSeqMap;
import static java.util.Objects.requireNonNull;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.RangedError;
import com.pentlander.sasquach.RangedErrorList;
import com.pentlander.sasquach.RangedErrorList.Builder;
import com.pentlander.sasquach.name.ModuleScopedTypeName;
import com.pentlander.sasquach.ast.NamedTypeDefinition.ForeignClass;
import com.pentlander.sasquach.ast.typenode.TypeNode;
import com.pentlander.sasquach.ast.typenode.TypeStatement;
import com.pentlander.sasquach.name.UnqualifiedTypeName;
import com.pentlander.sasquach.nameres.NameResolutionResult;
import com.pentlander.sasquach.type.MemberScopedTypeResolver.TypeMismatchError;
import com.pentlander.sasquach.type.MemberScopedTypeResolver.UnknownType;
import com.pentlander.sasquach.type.StructType.RowModifier;
import com.pentlander.sasquach.type.StructType.RowModifier.None;
import com.pentlander.sasquach.type.StructType.RowModifier.UnnamedRow;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class NamedTypeResolver {
  private final NameResolutionResult nameResolutionResult;
//  private final Function<Map<UnqualifiedName, Type>, StructName> structNameResolver = null;
  private final Builder errors = RangedErrorList.builder();

  public NamedTypeResolver(NameResolutionResult nameResolutionResult) {
    this.nameResolutionResult = nameResolutionResult;
  }

  private Type resolveNamedType(NamedType namedType, Map<String, Type> typeArgs, Range range) {
    var typeDefNode = nameResolutionResult.getNamedTypeDef(namedType)
        .orElseThrow(() -> new IllegalStateException("Unable to find named type: " + namedType));

    return switch (typeDefNode) {
      case TypeParameter typeParameter ->
          typeArgs.getOrDefault(typeParameter.name(), namedType);
      case TypeStatement typeStatement -> {
        if (typeStatement.typeParameters().size() != namedType.typeArguments().size()) {
          yield addError(new TypeMismatchError("Number of type args does not match number of "
              + "type parameters for type '%s'".formatted(typeStatement.toPrettyString()), range));
        }
        // Construct a new map of type arguments that includes the
        var newTypeArgs = new HashMap<>(typeArgs);
        var resolvedTypeArgs = new ArrayList<Type>();
        for (int i = 0; i < typeStatement.typeParameters().size(); i++) {
          var typeParam = typeStatement.typeParameters().get(i);
          var typeArg = resolveNames(namedType.typeArguments().get(i), typeArgs, range);
          newTypeArgs.put(typeParam.name(), typeArg);
          resolvedTypeArgs.add(typeArg);
        }

        yield switch (namedType.typeName()) {
          case ModuleScopedTypeName(var moduleName, var name) -> new ResolvedModuleNamedType(
              moduleName,
              name,
              resolvedTypeArgs,
              resolveNames(typeStatement.type(), newTypeArgs, range));
          case UnqualifiedTypeName typeName -> new ResolvedLocalNamedType(typeName.toString(),
              resolvedTypeArgs,
              resolveNames(typeStatement.type(), newTypeArgs, range));
        };
      }
      case ForeignClass foreignClass -> {
        var clazz = foreignClass.clazz();
        if (clazz.getTypeParameters().length != namedType.typeArguments().size()) {
          yield addError(new TypeMismatchError("Number of type args does not match number of "
              + "type parameters for type '%s'".formatted(clazz.getName()),
              requireNonNull(range)));
        }
        yield new ClassType(foreignClass.clazz(),
            resolveNamedTypes(namedType.typeArguments(), typeArgs, range));
      }
    };
  }

  public Type resolveNames(TypeNode typeNode, Map<String, Type> typeArgs) {
    return resolveNames(typeNode.type(), typeArgs, typeNode.range());
  }

  @SuppressWarnings("unchecked")
  public <T extends Type> T resolveNames(T type, Map<String, Type> typeArgs, Range range) {
    return switch (type) {
      case NamedType namedType -> (T) resolveNamedType(namedType, typeArgs, range);
      case TypeNester typeNester -> (T) resolveNestedTypes(typeNester, typeArgs, range);
      default -> type;
    };
  }

  // For any parameterized type, you must recursively resolve any nested named types.
  private Type resolveNestedTypes(TypeNester typeNester,
      Map<String, Type> typeArgs, Range range) {
    return switch (typeNester) {
      case StructType structType -> new StructType(structType.name(),
          structType.typeParameters(),
          structType.memberTypes()
              .entrySet()
              .stream()
              .collect(toSeqMap(Entry::getKey, e -> resolveNames(e.getValue(), typeArgs, range))),
          switch (structType.rowModifier()) {
            case RowModifier.NamedRow(var type) -> new RowModifier.NamedRow(resolveNames(type, typeArgs, range));
            case UnnamedRow unnamedRow -> unnamedRow;
            case None none -> none;
          });
      case FunctionType funcType -> {
        var newTypeArgs = new HashMap<>(typeArgs);
        var params = funcType.parameters()
            .stream()
            .map(param -> param.mapType(type -> resolveNames(type, newTypeArgs, range)))
            .toList();
        yield new FunctionType(
            params,
            funcType.typeParameters(),
            resolveNames(funcType.returnType(), newTypeArgs, range));
      }
      case UniversalType universalType ->
          typeArgs.getOrDefault(universalType.typeNameStr(), universalType);
      case TypeVariable typeVariable -> typeVariable;
      case SumType sumType -> new SumType(
          sumType.qualifiedTypeName(),
          sumType.typeParameters(),
          sumType.types()
              .stream()
              .map(t -> resolveNames(t, typeArgs, range))
              .map(VariantType.class::cast)
              .toList());
      case ResolvedModuleNamedType namedType -> new ResolvedModuleNamedType(namedType.moduleName(),
          namedType.name(),
          resolveNamedTypes(namedType.typeArgs(), typeArgs, range),
          resolveNames(namedType.type(), typeArgs, range));
      case ResolvedLocalNamedType namedType -> new ResolvedLocalNamedType(namedType.name(),
          resolveNamedTypes(namedType.typeArgs(), typeArgs, range),
          resolveNames(namedType.type(), typeArgs, range));
      case ClassType classType -> new ClassType(classType.typeClass(),
          resolveNamedTypes(classType.typeArguments(), typeArgs, range));
    };
  }

  public RangedErrorList errors() {
    return errors.build();
  }

  private List<Type> resolveNamedTypes(List<Type> types, Map<String, Type> typeArgs, Range range) {
    return types.stream().map(t -> resolveNames(t, typeArgs, range)).toList();
  }

  private Type addError(RangedError error) {
    errors.add(error);
    return new UnknownType();
  }
}
