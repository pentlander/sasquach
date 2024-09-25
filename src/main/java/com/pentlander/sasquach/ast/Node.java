package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.expression.Expression;
import com.pentlander.sasquach.ast.expression.LocalVariable;
import com.pentlander.sasquach.ast.expression.NamedFunction;
import com.pentlander.sasquach.ast.expression.Struct.Field;
import com.pentlander.sasquach.ast.id.Id;
import com.pentlander.sasquach.ast.id.QualifiedModuleId;
import com.pentlander.sasquach.ast.id.TypeId;
import com.pentlander.sasquach.ast.typenode.FunctionSignature;
import com.pentlander.sasquach.ast.typenode.TypeNode;
import com.pentlander.sasquach.type.TypeParameter;

/**
 * A node in the abstract syntax tree.
 */
public sealed interface Node permits Branch, FunctionSignature, Id, ModuleDeclaration, Pattern,
    QualifiedModuleId, TypeId, TypeNode, Use, Expression, LocalVariable, Argument, NamedFunction,
    Field, TypeParameter {
  /**
   * Range in the source code that this node can be found.
   */
  Range range();

  default String toPrettyString() {
    return toString();
  }
}
