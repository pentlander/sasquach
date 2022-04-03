package com.pentlander.sasquach.type;

import static java.util.Objects.requireNonNull;

import com.pentlander.sasquach.RangedErrorList;
import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.expression.Expression;
import com.pentlander.sasquach.type.MemberScopedTypeResolver.QualifiedFunctionId;
import java.util.HashMap;
import java.util.Map;

public class TypeResolutionResult implements TypeFetcher {
  public static final TypeResolutionResult EMPTY = new TypeResolutionResult(Map.of(), Map.of(),
      Map.of(), RangedErrorList.empty());

  private final Map<Identifier, Type> idTypes;
  private final Map<Expression, Type> exprTypes;
  private final Map<QualifiedFunctionId, ForeignFunctionType> foreignFuncTypes;
  private final RangedErrorList errors;

  public TypeResolutionResult(Map<Identifier, Type> idTypes, Map<Expression, Type> exprTypes,
      Map<QualifiedFunctionId, ForeignFunctionType> foreignFuncTypes, RangedErrorList errors) {
    this.idTypes = idTypes;
    this.exprTypes = exprTypes;
    this.foreignFuncTypes = foreignFuncTypes;
    this.errors = errors;
  }

  @Override
  public Type getType(Expression expression) {
    return requireNonNull(exprTypes.get(expression), expression.toString());
  }

  @Override
  public Type getType(Identifier identifier) {
    return requireNonNull(idTypes.get(identifier), identifier.toString());
  }

  @Override
  public ForeignFunctionType getType(Identifier classAlias, Identifier functionName) {
    var id = new QualifiedFunctionId(classAlias, functionName);
    return requireNonNull(foreignFuncTypes.get(id), id.toString());
  }

  public RangedErrorList errors() {
    return errors;
  }

  public TypeResolutionResult merge(TypeResolutionResult other) {
    var newIdTypes = new HashMap<>(idTypes);
    newIdTypes.putAll(other.idTypes);
    var newExprTypes = new HashMap<>(exprTypes);
    newExprTypes.putAll(other.exprTypes);
    var newForeignFuncTypes = new HashMap<>(foreignFuncTypes);
    newForeignFuncTypes.putAll(other.foreignFuncTypes);

    return new TypeResolutionResult(newIdTypes,
        newExprTypes,
        newForeignFuncTypes,
        errors.concat(other.errors));
  }
}
