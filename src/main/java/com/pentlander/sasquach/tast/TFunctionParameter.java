package com.pentlander.sasquach.tast;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Id;
import com.pentlander.sasquach.ast.UnqualifiedName;
import com.pentlander.sasquach.ast.expression.Expression;
import com.pentlander.sasquach.tast.expression.TLocalVariable;
import com.pentlander.sasquach.tast.expression.TypedExpression;
import com.pentlander.sasquach.type.Type;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Function parameter captureName with a type.
 */
public record TFunctionParameter(Id id, Label label, Type type, Range range) implements TLocalVariable {
  /**
   * Name of the parameter variable.
   */
  public UnqualifiedName name() {
    return id.name();
  }

  @Override
  public String toPrettyString() {
    return name() + ": " + type().toPrettyString();
  }

  public sealed interface Label {
    UnqualifiedName name();

    static None none() {
      return None.INSTANCE;
    }

    static Label of(@Nullable Id label, @Nullable TypedExpression defaultExpr) {
      return label == null ? none()
          : defaultExpr == null ? new Some(label) : new WithDefault(label, defaultExpr);
    }

    final class None implements Label {
      private static final None INSTANCE = new None();

      private None() {
      }

      @Override
      public UnqualifiedName name() {
        return UnqualifiedName.EMPTY;
      }
    }

    record Some(Id label) implements Label {
      @Override
      public UnqualifiedName name() {
        return label.name();
      }
    }

    record WithDefault(Id label, TypedExpression defaultExpr) implements Label {
      @Override
      public UnqualifiedName name() {
        return label.name();
      }
    }
  }
}
