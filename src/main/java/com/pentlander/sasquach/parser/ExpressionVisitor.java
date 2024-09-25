package com.pentlander.sasquach.parser;

import static com.pentlander.sasquach.Util.mapNonNull;
import static java.util.Objects.requireNonNullElse;

import com.pentlander.sasquach.SourcePath;
import com.pentlander.sasquach.ast.Argument;
import com.pentlander.sasquach.ast.Branch;
import com.pentlander.sasquach.ast.Id;
import com.pentlander.sasquach.ast.NamedTypeNode;
import com.pentlander.sasquach.ast.Pattern;
import com.pentlander.sasquach.ast.PatternVariable;
import com.pentlander.sasquach.ast.TypeId;
import com.pentlander.sasquach.ast.UnqualifiedName;
import com.pentlander.sasquach.ast.expression.BinaryExpression;
import com.pentlander.sasquach.ast.expression.BinaryExpression.BooleanExpression;
import com.pentlander.sasquach.ast.expression.BinaryExpression.BooleanOperator;
import com.pentlander.sasquach.ast.expression.BinaryExpression.CompareExpression;
import com.pentlander.sasquach.ast.expression.BinaryExpression.CompareOperator;
import com.pentlander.sasquach.ast.expression.BinaryExpression.MathOperator;
import com.pentlander.sasquach.ast.expression.Block;
import com.pentlander.sasquach.ast.expression.Expression;
import com.pentlander.sasquach.ast.expression.ForeignFieldAccess;
import com.pentlander.sasquach.ast.expression.ForeignFunctionCall;
import com.pentlander.sasquach.ast.expression.FunctionCall;
import com.pentlander.sasquach.ast.expression.IfExpression;
import com.pentlander.sasquach.ast.expression.LocalFunctionCall;
import com.pentlander.sasquach.ast.expression.Loop;
import com.pentlander.sasquach.ast.expression.Match;
import com.pentlander.sasquach.ast.expression.MemberAccess;
import com.pentlander.sasquach.ast.expression.MemberFunctionCall;
import com.pentlander.sasquach.ast.expression.Not;
import com.pentlander.sasquach.ast.expression.PipeOperator;
import com.pentlander.sasquach.ast.expression.PrintStatement;
import com.pentlander.sasquach.ast.expression.Recur;
import com.pentlander.sasquach.ast.expression.Value;
import com.pentlander.sasquach.ast.expression.VarReference;
import com.pentlander.sasquach.ast.expression.VariableDeclaration;
import com.pentlander.sasquach.parser.SasquachParser.*;
import com.pentlander.sasquach.parser.Visitor.StructIdentifier;
import com.pentlander.sasquach.type.BuiltinType;
import java.util.List;

class ExpressionVisitor extends
    com.pentlander.sasquach.parser.SasquachBaseVisitor<Expression> implements VisitorHelper {
  private final ModuleContext moduleCtx;

  public ExpressionVisitor(ModuleContext moduleCtx) {
    this.moduleCtx = moduleCtx;
  }

  @Override
  public Expression visitVarReference(VarReferenceContext ctx) {
    var name = new UnqualifiedName(ctx.getText());
    return new VarReference(new Id(name, rangeFrom(ctx.ID())));
  }

  @Override
  public Expression visitParenExpression(ParenExpressionContext ctx) {
    return ctx.expression().accept(this);
  }

  @Override
  public Expression visitNotExpression(NotExpressionContext ctx) {
    return new Not(ctx.expression().accept(this), rangeFrom(ctx));
  }

  @Override
  public Expression visitBinaryOperation(BinaryOperationContext ctx) {
    String operatorString = ctx.operator.getText();
    var leftExpr = ctx.left.accept(this);
    var rightExpr = ctx.right.accept(this);
    return new BinaryExpression.MathExpression(MathOperator.fromString(operatorString),
        leftExpr,
        rightExpr,
        rangeFrom(ctx));
  }

  @Override
  public Expression visitFunctionCall(FunctionCallContext ctx) {
    var arguments = args(ctx.application());
    var funcId = ctx.functionName().ID();
    if (funcId.getText().equals("recur")) {
      return new Recur(arguments, rangeFrom(ctx));
    }

    return new LocalFunctionCall(id(ctx.functionName().ID()), arguments, rangeFrom(ctx));
  }

  @Override
  public Expression visitCompareExpression(CompareExpressionContext ctx) {
    var leftExpr = ctx.left.accept(this);
    var rightExpr = ctx.right.accept(this);
    return new CompareExpression(CompareOperator.fromString(ctx.operator.getText()),
        leftExpr,
        rightExpr,
        rangeFrom(ctx));
  }

  @Override
  public Expression visitBooleanExpression(BooleanExpressionContext ctx) {
    var leftExpr = ctx.left.accept(this);
    var rightExpr = ctx.right.accept(this);
    return new BooleanExpression(BooleanOperator.fromString(ctx.operator.getText()),
        leftExpr,
        rightExpr,
        rangeFrom(ctx));
  }

  private ForeignFunctionCall foreignFuncCall(
      ForeignNameContext foreignNameCtx, MemberApplicationContext memberApplicationCtx
  ) {
    var classAlias = typeId(foreignNameCtx);
    var memberId = id(memberApplicationCtx.memberName().ID());
    var arguments = args(memberApplicationCtx.application());
    return new ForeignFunctionCall(classAlias,
        memberId,
        arguments,
        classAlias.range().join(rangeFrom(memberApplicationCtx)));
  }

  @Override
  public Expression visitPipeExpression(PipeExpressionContext ctx) {
    var expr = ctx.expr.accept(this);
    FunctionCall funcCall;
    if (ctx.functionCall() != null) {
      funcCall = (FunctionCall) ctx.functionCall().accept(this);
    } else if (ctx.memberExpression != null) {
      funcCall = memberFuncCall(ctx.memberExpression, ctx.memberApplication());
    } else if (ctx.foreignName() != null) {
      funcCall = foreignFuncCall(ctx.foreignName(), ctx.memberApplication());
    } else {
      throw new IllegalStateException();
    }
    return new PipeOperator(expr, funcCall, rangeFrom(ctx));
  }

  @Override
  public Expression visitPrintStatement(PrintStatementContext ctx) {
    Expression expr = ctx.expression().accept(this);
    return new PrintStatement(expr, rangeFrom(ctx));
  }

  @Override
  public Expression visitIfExpression(IfExpressionContext ctx) {
    var ifBlock = ctx.ifBlock();
    var ifCondition = ifBlock.ifCondition.accept(this);
    var trueExpr = ifBlock.trueBlock.accept(this);
    Expression falseExpr = null;
    if (ifBlock.falseBlock != null) {
      falseExpr = ifBlock.falseBlock.accept(this);
    }
    return new IfExpression(ifCondition, trueExpr, falseExpr, rangeFrom(ctx));
  }

  @Override
  public Expression visitVariableDeclaration(VariableDeclarationContext ctx) {
    var typeAnnotation = VisitorHelper.typeAnnotation(ctx.typeAnnotation(), new TypeVisitor(moduleCtx));
    var expr = ctx.expression().accept(this);
    return new VariableDeclaration(id(ctx.ID()), typeAnnotation, expr, rangeFrom(ctx));
  }

  @Override
  public Expression visitStruct(StructContext ctx) {
    return ctx.accept(structVisitorForLiteral());
  }

  @Override
  public Expression visitNamedStruct(NamedStructContext ctx) {
    var namedTypeNode = (NamedTypeNode) ctx.namedType().accept(new TypeVisitor(moduleCtx));
    return ctx.struct().accept(structVisitorForNamed(namedTypeNode));
  }

  @Override
  public Expression visitFunction(FunctionContext ctx) {
    return ctx.accept(new FunctionVisitor(moduleCtx));
  }

  private List<Argument> args(ApplicationContext ctx) {
    if (ctx.arguments() == null) {
      return List.of();
    }
    return ctx.arguments().argument().stream().map(argCtx -> {
      var label = label(argCtx.label());
      var expr = argCtx.expression().accept(this);
      return new Argument(mapNonNull(label, Id::name), expr, rangeFrom(argCtx));
    }).toList();
  }

  @Override
  public Expression visitMemberApplicationExpression(MemberApplicationExpressionContext ctx) {
    return memberFuncCall(ctx.expression(), ctx.memberApplication());
  }

  private MemberFunctionCall memberFuncCall(
      ExpressionContext exprCtx, MemberApplicationContext memberAppCtx
  ) {
    var expr = exprCtx.accept(this);
    var memberId = id(memberAppCtx.memberName().ID());
    var arguments = args(memberAppCtx.application());
    return new MemberFunctionCall(expr,
        memberId,
        arguments,
        rangeFrom(exprCtx).join(rangeFrom(memberAppCtx)));
  }

  @Override
  public Expression visitMemberAccessExpression(MemberAccessExpressionContext ctx) {
    var expr = ctx.expression().accept(this);
    var memberId = id(ctx.memberName().ID());
    return new MemberAccess(expr, memberId);
  }

  @Override
  public Expression visitForeignMemberAccessExpression(ForeignMemberAccessExpressionContext ctx) {
    var classAliasId = typeId(ctx.foreignName());
    var memberId = id(ctx.memberName().ID());
    return new ForeignFieldAccess(classAliasId, memberId);
  }

  @Override
  public Expression visitForeignMemberApplicationExpression(
      ForeignMemberApplicationExpressionContext ctx
  ) {
    return foreignFuncCall(ctx.foreignName(), ctx.memberApplication());
  }

  @Override
  public Expression visitBlock(BlockContext ctx) {
    List<Expression> expressions = ctx.blockStatement()
        .stream()
        .map(blockCtx -> blockCtx.accept(this))
        .toList();
    return new Block(expressions, rangeFrom(ctx));
  }

  @Override
  public Expression visitIntLiteral(IntLiteralContext ctx) {
    return new Value(BuiltinType.INT, ctx.getText(), rangeFrom(ctx));
  }

  @Override
  public Expression visitDblLiteral(DblLiteralContext ctx) {
    return new Value(BuiltinType.DOUBLE, ctx.getText(), rangeFrom(ctx));
  }

  @Override
  public Expression visitStringLiteral(StringLiteralContext ctx) {
    return new Value(BuiltinType.STRING, ctx.getText(), rangeFrom(ctx));
  }

  @Override
  public Expression visitBooleanLiteral(BooleanLiteralContext ctx) {
    return new Value(BuiltinType.BOOLEAN, ctx.getText(), rangeFrom(ctx));
  }

  @Override
  public Expression visitLoopExpression(LoopExpressionContext ctx) {
    var loop = ctx.loop();
    var varDeclCtx = requireNonNullElse(loop.variableDeclaration(),
        List.<VariableDeclarationContext>of());
    var varDecls = varDeclCtx.stream()
        .map(this::visitVariableDeclaration)
        .map(VariableDeclaration.class::cast)
        .toList();
    return new Loop(varDecls, loop.expression().accept(this), rangeFrom(ctx));
  }

  @Override
  public Expression visitTupleExpression(TupleExpressionContext ctx) {
    var tupCtx = ctx.tuple();
    var expressions = tupCtx.expression().stream().map(exprCtx -> exprCtx.accept(this)).toList();
    return com.pentlander.sasquach.ast.expression.Struct.tupleStruct(expressions, rangeFrom(ctx));
  }

  @Override
  public Expression visitMatchExpression(MatchExpressionContext ctx) {
    var match = ctx.match();
    var branches = match.branch().stream().map(branch -> {
      var pattern = switch (branch.pattern()) {
        case SingletonPatternContext pat -> new Pattern.Singleton(typeId(pat.namedType()));
        case SingleTupleVariantPatternContext pat ->
            new Pattern.VariantTuple(typeId(pat.namedType()),
                List.of(new PatternVariable(id(pat.ID()))),
                rangeFrom(pat));
        case MultiTupleVariantPatternContext pat ->
            new Pattern.VariantTuple(typeId(pat.namedType()),
                pat.ID().stream().map(this::id).map(PatternVariable::new).toList(),
                rangeFrom(pat));
        case StructVariantPatternContext pat -> new Pattern.VariantStruct(typeId(pat.namedType()),
            pat.ID().stream().map(this::id).map(PatternVariable::new).toList(),
            rangeFrom(pat));
        default ->
            throw new IllegalStateException("Unknown match branch: " + branch.pattern().getText());
      };
      return new Branch(pattern, branch.expression().accept(this), rangeFrom(branch));
    }).toList();

    return new Match(match.expression().accept(this), branches, rangeFrom(ctx));
  }

  private TypeId typeId(NamedTypeContext ctx) {
    var namedTypeNode = (NamedTypeNode) ctx.accept(new TypeVisitor(moduleCtx));
    return (TypeId) namedTypeNode.id();
  }


  public StructVisitor structVisitorForLiteral() {
    // TODO: Set the metadata at the end of the visitStruct func so struct methods work properly
    // TODO: Figure out how to reference parent scope from struct literal
    return new StructVisitor(moduleCtx, StructIdentifier.NONE);
  }

  public StructVisitor structVisitorForNamed(NamedTypeNode node) {
    // TODO: Set the metadata at the end of the visitStruct func so struct methods work properly
    // TODO: Figure out how to reference parent scope from struct literal
    return new StructVisitor(moduleCtx, new StructIdentifier.TypeNode(node));
  }

  @Override
  public SourcePath sourcePath() {
    return moduleCtx.sourcePath();
  }
}
