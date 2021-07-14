package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.ast.expression.Expression;

public interface NodeVisitor<T> {
  default T visit(Node node) {
    if (node instanceof FunctionParameter funcParam) {
      return visit(funcParam);
    } else if (node instanceof FunctionSignature funcSig) {
      return visit(funcSig);
    } else if (node instanceof ModuleDeclaration modDecl) {
      return visit(modDecl);
    } else if (node instanceof TypeNode typeNode) {
      return visit(typeNode);
    } else if (node instanceof Use use) {
      return visit(use);
    } else if (node instanceof Expression expression) {
      return visit(expression);
    } else {
      throw new IllegalStateException();
    }
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

  default T visit(Use use) {
    if (use instanceof Use.Module useModule) {
      return visit(useModule);
    } else if (use instanceof Use.Foreign useForeign) {
      return visit(useForeign);
    } else {
      throw new IllegalStateException();
    }
  }

  T visit(Use.Module use);

  T visit(Use.Foreign use);

  T visit(Expression expression);
}
