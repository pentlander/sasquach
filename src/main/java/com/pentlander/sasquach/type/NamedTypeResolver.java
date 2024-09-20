package com.pentlander.sasquach.type;

import static com.pentlander.sasquach.Util.toSeqMap;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;
import static java.util.stream.Collectors.toMap;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.RangedError;
import com.pentlander.sasquach.RangedErrorList;
import com.pentlander.sasquach.RangedErrorList.Builder;
import com.pentlander.sasquach.ast.BasicTypeNode;
import com.pentlander.sasquach.ast.FunctionSignature;
import com.pentlander.sasquach.ast.ModuleScopedTypeName;
import com.pentlander.sasquach.ast.NamedTypeDefinition.ForeignClass;
import com.pentlander.sasquach.ast.NamedTypeNode;
import com.pentlander.sasquach.ast.StructTypeNode;
import com.pentlander.sasquach.ast.StructTypeNode.RowModifier.NamedRow;
import com.pentlander.sasquach.ast.SumTypeNode;
import com.pentlander.sasquach.ast.SumTypeNode.VariantTypeNode.Singleton;
import com.pentlander.sasquach.ast.SumTypeNode.VariantTypeNode.Struct;
import com.pentlander.sasquach.ast.SumTypeNode.VariantTypeNode.Tuple;
import com.pentlander.sasquach.ast.TupleTypeNode;
import com.pentlander.sasquach.ast.TypeStatement;
import com.pentlander.sasquach.ast.TypeNode;
import com.pentlander.sasquach.ast.UnqualifiedTypeName;
import com.pentlander.sasquach.ast.expression.FunctionParameter;
import com.pentlander.sasquach.name.NameResolutionResult;
import com.pentlander.sasquach.type.MemberScopedTypeResolver.TypeMismatchError;
import com.pentlander.sasquach.type.MemberScopedTypeResolver.UnknownType;
import com.pentlander.sasquach.type.StructType.RowModifier;
import com.pentlander.sasquach.type.StructType.RowModifier.None;
import com.pentlander.sasquach.type.StructType.RowModifier.UnnamedRow;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class NamedTypeResolver {
  private final NameResolutionResult nameResolutionResult;
  private final Builder errors = RangedErrorList.builder();

  public NamedTypeResolver(NameResolutionResult nameResolutionResult) {
    this.nameResolutionResult = nameResolutionResult;
  }
  // Create a more general transform API for Types so e.g. can turn a FunctionType populated with
  // universal types into a FunctionType populated with type variables

  @SuppressWarnings({"unchecked", "RedundantSuppression"})
  public <T extends TypeNode> T resolveTypeNode(T typeNode, Map<String, Type> typeArgs) {
    return (T) switch (typeNode) {
      case BasicTypeNode node -> node;
      case FunctionSignature(var parameters, var typeParameters, var returnType, var range) -> {
        var newTypeArgs = new HashMap<>(typeArgs);
        newTypeArgs.putAll(MemberScopedTypeResolver.typeParams(typeParameters,
            TypeParameter::toUniversal));
        var resolvedParameters = parameters.stream()
            .map(p -> {
              var paramTypeNode =
                  p.typeNode() != null ? resolveTypeNode(p.typeNode(), newTypeArgs) : null;
              return new FunctionParameter(p.id(), p.label(), paramTypeNode, p.defaultExpr());
            })
            .toList();
        var returnTypeNode = returnType != null ? resolveTypeNode(returnType, newTypeArgs) : null;
        yield new FunctionSignature(resolvedParameters,
            typeParameters,
            returnTypeNode,
            range);
      }
      case StructTypeNode(var name, var fieldTypeNodes, var rowModifier, var range) -> {
        var resolvedFieldTypes = fieldTypeNodes.entrySet()
            .stream()
            .map(e -> Map.entry(e.getKey(), resolveTypeNode(e.getValue(), typeArgs)))
            .collect(toMap(Entry::getKey, Entry::getValue));
        var resolvedRowModifier = rowModifier;
        if (rowModifier instanceof NamedRow namedRow) {
          // Right now the localnamedtype is getting replaced with a universal type. This might be
          // ok here since this is just the name resolution step. However, at some point the
          // universal type needs to get converted into a special struct type variable that's able
          // to take on the fields/funcs of the struct provided as an arg
          resolvedRowModifier = new NamedRow(resolveTypeNode(namedRow.typeNode(), typeArgs));
        }
        yield new StructTypeNode(name, resolvedFieldTypes, resolvedRowModifier, range);
      }
      case SumTypeNode(
          var moduleName, var id, var typeParameters, var variantTypeNodes, var range
      ) -> new SumTypeNode(moduleName,
          id,
          typeParameters,
          mapResolveTypeNode(variantTypeNodes, typeArgs),
          range);
      case TupleTypeNode(var name, var fields, var range) ->
          new TupleTypeNode(name, mapResolveTypeNode(fields, typeArgs), range);
      case TypeStatement(var id, var typeParameters, var aliasTypeNode, var isAlias, var range) -> {
        var newTypeArgs = new HashMap<>(typeArgs);
        newTypeArgs.putAll(MemberScopedTypeResolver.typeParams(typeParameters,
            TypeParameter::toUniversal));
        yield new TypeStatement(id, typeParameters, resolveTypeNode(aliasTypeNode, newTypeArgs), isAlias, range);
      }
      case Singleton(var moduleName, var aliasId, var id) ->
          new Singleton(moduleName, aliasId, id);
      case Tuple(var moduleName, var aliasId, var id, var tupleTypeNode) ->
          new Tuple(moduleName,
              aliasId,
              id,
              resolveTypeNode(tupleTypeNode, typeArgs));
      case Struct(var moduleName, var aliasId, var id, var structTypeNode) ->
          new Struct(moduleName,
              aliasId,
              id,
              resolveTypeNode(structTypeNode, typeArgs));
      case NamedTypeNode(var id, var typeArgNodes, var type, var range) ->
          new NamedTypeNode(id, typeArgNodes, resolveNames(type, typeArgs, range), range);
    };
  }

  public <T extends TypeNode> List<T> mapResolveTypeNode(Collection<T> typeNodes) {
    return typeNodes.stream().map(t -> resolveTypeNode(t, Map.of())).toList();
  }

  private <T extends TypeNode> List<T> mapResolveTypeNode(Collection<T> typeNodes,
      Map<String, Type> typeArgs) {
    return typeNodes.stream().map(t -> resolveTypeNode(t, typeArgs)).toList();
  }

  private Type resolveNamedType(NamedType namedType, Map<String, Type> typeArgs, Range range) {
    var typeDefNode = nameResolutionResult.getNamedTypeDef(namedType)
        .orElseThrow(() -> new IllegalStateException("Unable to find named type: " + namedType));

    return switch (typeDefNode) {
      case TypeParameter typeParameter ->
          typeArgs.getOrDefault(typeParameter.name(), namedType);
      case TypeStatement typeStatement -> {
        if (typeStatement.typeParameters().size() != namedType.typeArguments().size()) {
          yield addError(new TypeMismatchError(
              "Number of type args does not match number of "
                  + "type parameters for type '%s'".formatted(typeStatement.toPrettyString()),
              requireNonNullElse(range, typeStatement.range())));
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

  @SuppressWarnings("unchecked")
  public <T extends Type> T resolveNames(T type, Map<String, Type> typeArgs, Range range) {
    return switch (type) {
      case NamedType namedType -> (T) resolveNamedType(namedType, typeArgs, range);
      case TypeNester typeNester -> (T) resolveNestedTypes(typeNester, typeArgs, range);
      case null, default -> type;
    };
  }

  // For any parameterized type, you must recursively resolve any nested named types.
  private Type resolveNestedTypes(TypeNester typeNester,
      Map<String, Type> typeArgs, Range range) {
    return switch (typeNester) {
      case StructType structType -> new StructType(structType.structName(),
          structType.typeParameters(),
          structType.memberTypes()
              .entrySet()
              .stream()
              .collect(toSeqMap(Entry::getKey, e -> resolveNames(e.getValue(), typeArgs, range))),
          structType.namedStructTypes(),
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
