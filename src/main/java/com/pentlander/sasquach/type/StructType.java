package com.pentlander.sasquach.type;

import static java.util.stream.Collectors.joining;

import com.pentlander.sasquach.runtime.StructBase;
import com.pentlander.sasquach.type.StructType.RowModifier.NamedRow;
import com.pentlander.sasquach.type.StructType.RowModifier.None;
import com.pentlander.sasquach.type.StructType.RowModifier.UnnamedRow;
import java.lang.constant.ClassDesc;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Type of a struct.
 *
 * @param fieldTypes Map of field names to types. Field types include any value type, as well as
 *                   functions.
 */
public record StructType(String typeName, Map<String, Type> fieldTypes, RowModifier rowModifier) implements
    ParameterizedType, VariantType {
  private static final String PREFIX = "Struct";

  public StructType {
    fieldTypes = Objects.requireNonNullElse(fieldTypes, Map.of());
    fieldTypes.values().forEach(value -> Objects.requireNonNull(value, toString()));
    typeName = Objects.requireNonNullElse(typeName, PREFIX + hashFieldTypes(fieldTypes));
  }

  public StructType(@Nullable String name, Map<String, Type> fieldTypes) {
    this(name, fieldTypes, RowModifier.none());
  }

  public StructType(Map<String, Type> fieldTypes, RowModifier rowModifier) {
    this(null, fieldTypes, rowModifier);
  }

  public StructType(Map<String, Type> fieldTypes) {
    this(null, fieldTypes, RowModifier.none());
  }

  private static String hashFieldTypes(Map<String, Type> fieldTypes) {
    return Integer.toHexString(fieldTypes.entrySet()
        .stream()
        .sorted(Entry.comparingByKey())
        .toList()
        .hashCode());
  }

  public Type fieldType(String fieldName) {
    return fieldTypes().get(fieldName);
  }

  public List<Type> sortedFieldTypes() {
    return fieldTypes().entrySet()
        .stream()
        .sorted(Entry.comparingByKey())
        .map(Entry::getValue)
        .toList();
  }

  public List<Entry<String, Type>> sortedFields() {
    return fieldTypes().entrySet().stream().sorted(Entry.comparingByKey()).toList();
  }

  @Override
  public ClassDesc classDesc() {
    return TypeUtils.classDesc(StructBase.class);
  }

  @Override
  public String internalName() {
    return typeName().replace(".", "/");
  }

  @Override
  public boolean isAssignableFrom(Type other) {
    var structType = TypeUtils.asStructType(other);

    if (structType.isPresent()) {
      var otherFieldTypes = new HashMap<>(structType.get().fieldTypes);
      for (var entry : fieldTypes.entrySet()) {
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
    return typeName().startsWith(PREFIX) ? fieldTypes().entrySet()
        .stream()
        .map(e -> e.getKey() + ": " + e.getValue().toPrettyString())
        .collect(joining(", ", "{ ", " }")) : typeName();
  }

  public boolean isRow() {
    return switch (rowModifier) {
      case NamedRow namedRow -> true;
      case UnnamedRow unnamedRow -> true;
      case None none -> false;
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
