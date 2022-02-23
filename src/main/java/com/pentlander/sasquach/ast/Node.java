package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.expression.Expression;
import com.pentlander.sasquach.ast.expression.LocalVariable;

/**
 * A node in the abstract syntax tree.
 */
public sealed interface Node permits FunctionSignature, Identifier,
    ModuleDeclaration, QualifiedIdentifier, TypeAlias, TypeNode, Use, Expression, LocalVariable {
  /**
   * Range in the source code that this node can be found.
   */
  Range range();

  default String toPrettyString() {
    return toString();
  }
}
