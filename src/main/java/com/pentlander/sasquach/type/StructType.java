package com.pentlander.sasquach.type;

import static com.pentlander.sasquach.Util.seqMap;
import static java.util.Objects.requireNonNullElse;
import static java.util.stream.Collectors.joining;

import com.pentlander.sasquach.ast.StructName;
import com.pentlander.sasquach.ast.StructName.UnnamedStruct;
import com.pentlander.sasquach.ast.UnqualifiedName;
import com.pentlander.sasquach.ast.UnqualifiedTypeName;
import com.pentlander.sasquach.runtime.StructBase;
import com.pentlander.sasquach.type.ModuleScopedTypes.SumWithVariantIdx;
import com.pentlander.sasquach.type.StructType.RowModifier.NamedRow;
import com.pentlander.sasquach.type.StructType.RowModifier.None;
import com.pentlander.sasquach.type.StructType.RowModifier.UnnamedRow;
import java.lang.constant.ClassDesc;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.SequencedMap;
import java.util.StringJoiner;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Type of a struct.
 *
 * @param memberTypes Map of field names to types. Field types include any value type, as well as
 *                   functions.
 */
public record StructType(StructName structName, List<TypeParameter> typeParameters, SequencedMap<UnqualifiedName, Type> memberTypes, Map<UnqualifiedTypeName, SumType> namedStructTypes, RowModifier rowModifier) implements
    ParameterizedType, VariantType, TypeNester {
  private static final String PREFIX = "Struct";
  private static final Pattern TUPLE_FIELD_PATTERN = Pattern.compile("^_[0-9]+$");

  public StructType {
    memberTypes = requireNonNullElse(memberTypes, seqMap());
    memberTypes.values().forEach(value -> Objects.requireNonNull(value, toString()));
    structName = requireNonNullElse(structName, new UnnamedStruct(PREFIX + hashFieldTypes(
        memberTypes)));
    typeParameters = requireNonNullElse(typeParameters, List.of());
    namedStructTypes = requireNonNullElse(namedStructTypes, Map.of());
  }

  public StructType(@Nullable StructName name, SequencedMap<UnqualifiedName, Type> fieldTypes, Map<UnqualifiedTypeName, SumType> namedStructTypes) {
    this(name, List.of(), fieldTypes, namedStructTypes, RowModifier.none());
  }

  public StructType(@Nullable StructName name, SequencedMap<UnqualifiedName, Type> fieldTypes) {
    this(name, fieldTypes, Map.of());
  }

  public static StructType unnamed(SequencedMap<UnqualifiedName, Type> fieldTypes, RowModifier rowModifier) {
    return new StructType(null, List.of(), fieldTypes, Map.of(), rowModifier);
  }

  public static StructType unnamed(SequencedMap<UnqualifiedName, Type> fieldTypes) {
    return new StructType(null, List.of(), fieldTypes, Map.of(), RowModifier.none());
  }

  @Override
  public String typeNameStr() {
    return structName.toString();
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

  public @Nullable SumWithVariantIdx constructableType(UnqualifiedTypeName typeName) {
    for (var namedStructTypeEntry : namedStructTypes.entrySet()) {
      var namedStructType = namedStructTypeEntry.getValue();
      for (int i = 0; i < namedStructType.types().size(); i++) {
        var variant = namedStructType.types().get(i);
        if (variant.typeNameStr().endsWith(typeName.toString())) {
          return new SumWithVariantIdx(namedStructType, i);
        }
      }
    }
    return null;
  }

  public List<Type> sortedFieldTypes() {
    return memberTypes().entrySet()
        .stream()
        .sorted(Entry.comparingByKey())
        .map(Entry::getValue)
        .toList();
  }

  public List<Entry<UnqualifiedName, Type>> sortedFields() {
    return memberTypes().entrySet().stream().sorted(Entry.comparingByKey()).toList();
  }

  @Override
  public ClassDesc classDesc() {
    return TypeUtils.classDesc(StructBase.class);
  }

  @Override
  public String internalName() {
    return typeNameStr().replace(".", "/");
  }

  public ClassDesc internalClassDesc() {
    return ClassDesc.ofInternalName(internalName());
  }

  @Override
  public boolean isAssignableFrom(Type other) {
    var structType = TypeUtils.asStructType(other);

    if (structType.isPresent()) {
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
    if (structName instanceof UnnamedStruct) {
      var joiner = new StringJoiner(", ", "{ ", "}");
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
    return typeNameStr();
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
