package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.expression.Expression;
import com.pentlander.sasquach.ast.expression.LocalVariable;
import com.pentlander.sasquach.ast.expression.NamedFunction;

/**
 * A node in the abstract syntax tree.
 */
public sealed interface Node permits Branch, FunctionSignature, Identifier, ModuleDeclaration,
    Pattern, QualifiedIdentifier, TypeNode, Use, Expression, LocalVariable, NamedFunction {
  /**
   * Range in the source code that this node can be found.
   */
  Range range();

  default String toPrettyString() {
    return toString();
  }
}
