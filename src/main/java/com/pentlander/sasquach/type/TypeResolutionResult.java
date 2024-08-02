package com.pentlander.sasquach.type;

import static java.util.Objects.requireNonNull;

import com.pentlander.sasquach.RangedErrorList;
import com.pentlander.sasquach.ast.QualifiedModuleId;
import com.pentlander.sasquach.tast.TModuleDeclaration;
import com.pentlander.sasquach.tast.TypedMember;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public class TypeResolutionResult {
  public static final TypeResolutionResult EMPTY = new TypeResolutionResult(
      Map.of(),
      null,
      RangedErrorList.empty());

  private final Map<QualifiedModuleId, TModuleDeclaration> typedModules;
  private final TypedMember typedMember;
  private final RangedErrorList errors;

  public TypeResolutionResult(Map<QualifiedModuleId, TModuleDeclaration> typedModules,
      @Nullable TypedMember typedMember, RangedErrorList errors) {
    this.typedModules = typedModules;
    this.typedMember = typedMember;
    this.errors = errors;
  }

  public static TypeResolutionResult ofTypedMember(
      TypedMember typedMember,
      RangedErrorList errors) {
    return new TypeResolutionResult(Map.of(), typedMember, errors);
  }

  public static TypeResolutionResult ofTypedModules(
      Map<QualifiedModuleId, TModuleDeclaration> typedModules, RangedErrorList errors) {
    return new TypeResolutionResult(typedModules, null, errors);
  }

  public TypedMember getTypedMember() {
    return requireNonNull(typedMember);
  }

  public Collection<TModuleDeclaration> getModuleDeclarations() {
    return typedModules.values();
  }

  public RangedErrorList errors() {
    return errors;
  }

  public TypeResolutionResult merge(TypeResolutionResult other) {
    var newTypedModules = new HashMap<>(typedModules);
    newTypedModules.putAll(other.typedModules);

    return new TypeResolutionResult(
        newTypedModules,
        null,
        errors.concat(other.errors));
  }
}
