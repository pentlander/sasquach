package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.ast.expression.Expression;
import com.pentlander.sasquach.ast.expression.FunctionParameter;

public interface NodeVisitor<T> {
  default T visit(Node node) {
    return switch (node) {
      case FunctionParameter funcParam -> visit(funcParam);
      case FunctionSignature funcSig -> visit(funcSig);
      case ModuleDeclaration modDecl -> visit(modDecl);
      case TypeNode typeNode -> visit(typeNode);
      case Use use -> visit(use);
      case TypeAlias typeAlias -> visit(typeAlias);
      case Expression expression -> visit(expression);
      case null, default -> throw new IllegalStateException();
    };
  }

  default T visit(FunctionParameter functionParameter) {
    return visit(functionParameter.typeNode());
  }

  default T visit(FunctionSignature functionSignature) {
    functionSignature.typeParameters().forEach(this::visit);
    functionSignature.parameters().forEach(this::visit);
    return visit(functionSignature.returnTypeNode());
  }

  default T visit(ModuleDeclaration moduleDeclaration) {
    return visit(moduleDeclaration.struct());
  }

  T visit(TypeNode typeNode);


  T visit(TypeAlias typeAlias);

  default T visit(Use use) {
    return switch (use) {
      case Use.Module useModule -> visit(useModule);
      case Use.Foreign useForeign -> visit(useForeign);
    };
  }

  T visit(Use.Module use);

  T visit(Use.Foreign use);

  T visit(Expression expression);
}
