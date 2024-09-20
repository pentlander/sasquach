package com.pentlander.sasquach.ast;

import static com.pentlander.sasquach.Util.toLinkedMap;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.StructTypeNode.RowModifier.NamedRow;
import com.pentlander.sasquach.ast.StructTypeNode.RowModifier.None;
import com.pentlander.sasquach.ast.StructTypeNode.RowModifier.UnnamedRow;
import com.pentlander.sasquach.type.StructType;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Type node for a struct that contains type nodes for all of its fields. */
public record StructTypeNode(@Nullable StructName name, Map<UnqualifiedName, TypeNode> fieldTypeNodes, RowModifier rowModifier,
                             Range range) implements TypeNode {

  public StructTypeNode(Map<UnqualifiedName, TypeNode> fieldTypeNodes, RowModifier rowModifier, Range range) {
    this(null, fieldTypeNodes, rowModifier, range);
  }

  @Override
  public StructType type() {
    var fieldTypes = fieldTypeNodes.entrySet().stream()
        .collect(toLinkedMap(Entry::getKey, entry -> entry.getValue().type()));
    var rowModifier = switch (rowModifier()) {
      case NamedRow namedRow -> new StructType.RowModifier.NamedRow(namedRow.typeNode().type());
      case None _ -> StructType.RowModifier.none();
      case UnnamedRow _ -> StructType.RowModifier.unnamedRow();
    };
    return new StructType(name, List.of(), fieldTypes, Map.of(), rowModifier);
  }

  @Override
  public String typeNameStr() {
    return Objects.requireNonNullElse(name, type().structName()).toString();
  }

  public sealed interface RowModifier {
    record NamedRow(NamedTypeNode typeNode) implements RowModifier {
      public String name() {
        return typeNode.type().typeNameStr();
      }
    }

    static NamedRow namedRow(TypeId id, Range range) {
      return new NamedRow(new NamedTypeNode(id, List.of(), range));
    }

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
