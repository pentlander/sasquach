package com.pentlander.sasquach.parser;

import com.pentlander.sasquach.SourcePath;
import com.pentlander.sasquach.ast.FunctionSignature;
import com.pentlander.sasquach.ast.expression.Function;
import com.pentlander.sasquach.parser.SasquachParser.FunctionContext;
import com.pentlander.sasquach.parser.SasquachParser.FunctionDeclarationContext;

class FunctionVisitor extends
    com.pentlander.sasquach.parser.SasquachBaseVisitor<Function> implements VisitorHelper {
  private final ModuleContext moduleCtx;

  public FunctionVisitor(ModuleContext moduleCtx) {
    this.moduleCtx = moduleCtx;
  }

  @Override
  public Function visitFunction(FunctionContext ctx) {
    var funcSignature = functionDeclaration(ctx.functionDeclaration());

    var expr = ctx.expression().accept(new ExpressionVisitor(moduleCtx));

    return new Function(funcSignature, expr);
  }

  private FunctionSignature functionDeclaration(FunctionDeclarationContext ctx) {
    var typeVisitor = new TypeVisitor(moduleCtx);
    var params = parameterList(typeVisitor, new ExpressionVisitor(moduleCtx), ctx.functionParameterList());

    return new FunctionSignature(
        params,
        typeParams(ctx.typeParameterList()),
        VisitorHelper.typeAnnotation(ctx.typeAnnotation(), typeVisitor),
        rangeFrom(ctx));
  }

  @Override
  public SourcePath sourcePath() {
    return moduleCtx.sourcePath();
  }
}
