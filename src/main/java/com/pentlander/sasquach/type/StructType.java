package com.pentlander.sasquach.type;

import static java.util.Objects.requireNonNullElseGet;
import static java.util.stream.Collectors.joining;

import com.pentlander.sasquach.name.StructName;
import com.pentlander.sasquach.name.StructName.SyntheticName;
import com.pentlander.sasquach.name.UnqualifiedName;
import com.pentlander.sasquach.runtime.StructBase;
import com.pentlander.sasquach.type.StructType.RowModifier.NamedRow;
import com.pentlander.sasquach.type.StructType.RowModifier.None;
import com.pentlander.sasquach.type.StructType.RowModifier.UnnamedRow;
import java.lang.constant.ClassDesc;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SequencedMap;
import java.util.StringJoiner;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Type of a struct.
 *
 * @param memberTypes Map of field names to types. Field types include any value type, as well as
 *                    functions.
 */
public record StructType(StructName name, List<TypeParameter> typeParameters,
                         SequencedMap<UnqualifiedName, Type> memberTypes,
                         RowModifier rowModifier) implements ParameterizedType, VariantType,
    TypeNester {
  private static final String PREFIX = "Struct";
  private static final Pattern TUPLE_FIELD_PATTERN = Pattern.compile("^_[0-9]+$");

  public StructType(
      @Nullable StructName name,
      List<TypeParameter> typeParameters,
      SequencedMap<UnqualifiedName, Type> memberTypes,
      RowModifier rowModifier
  ) {
    this.name = requireNonNullElseGet(
        name,
        () -> SyntheticName.unqualified(PREFIX + hashFieldTypes(memberTypes)));
    this.typeParameters = typeParameters;
    this.memberTypes = memberTypes;
    this.rowModifier = rowModifier;
  }

  public StructType(StructName name, SequencedMap<UnqualifiedName, Type> fieldTypes) {
    this(name, List.of(), fieldTypes, RowModifier.none());
  }

  public static StructType synthetic(SequencedMap<UnqualifiedName, Type> memberTypes) {
    var name = SyntheticName.unqualified(PREFIX + hashFieldTypes(memberTypes));
    return new StructType(name, memberTypes);
  }

  @Override
  public String typeNameStr() {
    return name.toString();
  }

  public boolean isTuple() {
    return memberTypes.keySet().stream().allMatch(name -> TUPLE_FIELD_PATTERN.matcher(name.toString()).matches());
  }

  public FunctionType constructorType() {
    return constructorType(this);
  }

  public FunctionType constructorType(ParameterizedType returnType) {
    var isTuple = isTuple();
    var params = memberTypes()
        .entrySet()
        .stream()
        .map(entry -> new FunctionType.Param(entry.getValue(), !isTuple ? entry.getKey() : null, false))
        .toList();
    return new FunctionType(params, returnType.typeParameters(), returnType);
  }

  private static String hashFieldTypes(Map<UnqualifiedName, Type> fieldTypes) {
    return Integer.toHexString(fieldTypes.entrySet()
        .stream()
        .sorted(Entry.comparingByKey())
        .toList()
        .hashCode());
  }

  public @Nullable Type fieldType(UnqualifiedName fieldName) {
    return memberTypes.get(fieldName);
  }

  @Override
  public ClassDesc classDesc() {
    return isRow() || isSynthetic() ? StructBase.CD : internalClassDesc();
  }

  @Override
  public String internalName() {
    return typeNameStr().replace(".", "/");
  }

  public ClassDesc internalClassDesc() {
    return ClassDesc.ofInternalName(internalName());
  }

  private boolean isSynthetic() {
    return name instanceof SyntheticName;
  }

  @Override
  public boolean isAssignableFrom(Type other) {
    var structType = TypeUtils.asStructType(other);

    if (structType.isPresent()) {
      // For it to be assignable:
      // 1. Both types need to be synthetic,
      // 2. Both need to be named with the same name
      // 3. One has a row and the other doesn't matter
      if (!isRow() && (!isSynthetic() || !structType.get().isSynthetic()) && !name.equals(structType.get().name)) {
        return false;
      }

      var otherFieldTypes = new HashMap<>(structType.get().memberTypes);
      for (var entry : memberTypes.entrySet()) {
        var name = entry.getKey();
        var type = entry.getValue();
        var otherType = otherFieldTypes.remove(name);
        if (otherType == null || !type.isAssignableFrom(otherType)) {
          return false;
        }
      }
      return isRow() || otherFieldTypes.isEmpty();
    }
    return false;
  }

  @Override
  public String toPrettyString() {
    if (name instanceof SyntheticName) {
      var joiner = new StringJoiner(", ", "{ ", " }");
      var fieldTypesStr = memberTypes().entrySet()
          .stream()
          .map(e -> e.getKey() + ": " + e.getValue().toPrettyString())
          .collect(joining(", "));
      if (!fieldTypesStr.isBlank()) joiner.add(fieldTypesStr);

      var rowStr = switch (rowModifier) {
        case NamedRow(var type) -> ".." + type.toPrettyString() + " ";
        case UnnamedRow _ -> ".. ";
        case None _ -> " ";
      };
      if (!rowStr.isBlank()) joiner.add(rowStr);

      return joiner.toString();
    }
    return name.toPrettyString();
  }

  public boolean isRow() {
    return switch (rowModifier) {
      case NamedRow _, UnnamedRow _ -> true;
      case None _ -> false;
    };
  }

  public sealed interface RowModifier {
    record NamedRow(Type type) implements RowModifier {}

    final class UnnamedRow implements RowModifier {
      private static final UnnamedRow INSTANCE = new UnnamedRow();
      private UnnamedRow() {}
    }

    static UnnamedRow unnamedRow() {
      return UnnamedRow.INSTANCE;
    }

    final class None implements RowModifier {
      private static final None INSTANCE = new None();
      private None() {}
    }

    static None none() {
      return None.INSTANCE;
    }
  }
}
