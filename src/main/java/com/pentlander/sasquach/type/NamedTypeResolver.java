package com.pentlander.sasquach.type;

import static com.pentlander.sasquach.type.MemberScopedTypeResolver.UnknownType.UNKNOWN_TYPE;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;
import static java.util.stream.Collectors.toMap;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.RangedError;
import com.pentlander.sasquach.RangedErrorList;
import com.pentlander.sasquach.ast.NamedTypeDefinition.ForeignClass;
import com.pentlander.sasquach.ast.TypeAlias;
import com.pentlander.sasquach.name.NameResolutionResult;
import com.pentlander.sasquach.type.MemberScopedTypeResolver.TypeMismatchError;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class NamedTypeResolver {
  private final NameResolutionResult nameResolutionResult;
  private final RangedErrorList.Builder errors = RangedErrorList.builder();

  public NamedTypeResolver(NameResolutionResult nameResolutionResult) {
    this.nameResolutionResult = nameResolutionResult;
  }

  Type resolveNamedTypeNode(NamedType namedType, Map<String, Type> typeArgs, Range range) {
    // Get the alias, type parameter, or foreign class that defines the named type
    var typeDefNode = nameResolutionResult.getNamedType(namedType)
        .orElseThrow(() -> new IllegalStateException("Unable to find named type: " + namedType));

    return switch (typeDefNode) {
      // If it's a type parameter, check if there's a corresponding type argument within the
      // current scope. If not, resolve to a type variable for later unification
//      case TypeParameter typeParameter -> requireNonNull(typeArgs.get(typeParameter.typeName()),
//          "No mapping for type param '%s'".formatted(typeParameter.typeName()));
//      case TypeParameter typeParameter -> typeArgs.getOrDefault(typeParameter.typeName(),
//          new TypeVariable(typeParameter.typeName()));
      case TypeParameter typeParameter -> typeArgs.getOrDefault(typeParameter.typeName(),
          namedType);
      case TypeAlias typeAlias -> {
        if (typeAlias.typeParameters().size() != namedType.typeArguments().size()) {
          yield addError(new TypeMismatchError(
              "Number of type args does not match number of "
                  + "type parameters for type '%s'".formatted(typeAlias.toPrettyString()),
              requireNonNullElse(range, typeAlias.range())));
        }
        // Construct a new map of type arguments that includes the
        var newTypeArgs = new HashMap<>(typeArgs);
        var resolvedTypeArgs = new ArrayList<Type>();
        for (int i = 0; i < typeAlias.typeParameters().size(); i++) {
          var typeParam = typeAlias.typeParameters().get(i);
          var typeArg = resolveNamedType(namedType.typeArguments().get(i), typeArgs, range);
          newTypeArgs.put(typeParam.typeName(), typeArg);
          resolvedTypeArgs.add(typeArg);
        }

        yield switch (namedType) {
          case ModuleNamedType moduleNamedType -> new ResolvedModuleNamedType(moduleNamedType.moduleName(),
              moduleNamedType.name(),
              resolvedTypeArgs,
              resolveNamedType(typeAlias.type(), newTypeArgs, range));
          case LocalNamedType localNamedType -> new ResolvedLocalNamedType(localNamedType.typeName(),
              resolvedTypeArgs,
              resolveNamedType(typeAlias.type(), newTypeArgs, range));
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

  public Type resolveNamedType(Type type, Range range) {
    return resolveNamedType(type, Map.of(), range);
  }

  public Type resolveNamedType(Type type, Map<String, Type> typeArgs, Range range) {
    if (type instanceof NamedType namedType) {
      return resolveNamedTypeNode(namedType, typeArgs, range);
    } else if (type instanceof ParameterizedType parameterizedType) {
      return switch (parameterizedType) {
        case StructType structType -> new StructType(structType.typeName(),
            structType.fieldTypes().entrySet().stream()
            .collect(toMap(Entry::getKey, e -> resolveNamedType(e.getValue(), typeArgs, range))));
        case FunctionType funcType -> {
          var newTypeArgs = new HashMap<>(typeArgs);
          yield new FunctionType(
              resolveNamedTypes(funcType.parameterTypes(), newTypeArgs, range),
              funcType.typeParameters(),
              resolveNamedType(funcType.returnType(), newTypeArgs, range));
        }
        case ExistentialType existentialType -> typeArgs.getOrDefault(existentialType.typeName(),
            existentialType);
        case TypeVariable typeVariable -> typeVariable;
        case SumType sumType -> new SumType(sumType.moduleName(),
            sumType.name(),
            sumType.types().stream().map(t -> resolveNamedType(t, typeArgs, range))
                .map(VariantType.class::cast).toList());
        case ResolvedModuleNamedType namedType -> new ResolvedModuleNamedType(namedType.moduleName(),
            namedType.name(),
            resolveNamedTypes(namedType.typeArgs(), typeArgs,  range),
            resolveNamedType(namedType.type(), typeArgs, range));
        case ResolvedLocalNamedType namedType -> new ResolvedLocalNamedType(namedType.name(),
            resolveNamedTypes(namedType.typeArgs(), typeArgs, range),
            resolveNamedType(namedType.type(), typeArgs, range));
        case ClassType classType -> new ClassType(classType.typeClass(),
            resolveNamedTypes(classType.typeArguments(), typeArgs, range));
      };
    }

    return type;
  }

  public RangedErrorList errors() {
    return errors.build();
  }

  private List<Type> resolveNamedTypes(List<Type> types, Map<String, Type> typeArgs, Range range) {
    return types.stream().map(t -> resolveNamedType(t, typeArgs, range)).toList();
  }

  private Type addError(RangedError error) {
    errors.add(error);
    return UNKNOWN_TYPE;
  }
}
