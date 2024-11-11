package com.pentlander.sasquach.rdparser;

import static com.pentlander.sasquach.rdparser.Scanner.TokenType.ARROW;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.COLON;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.COMMA;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.DOT;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.DOT_DOT;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.EOF;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.EQ;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.FOREIGN;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.LET;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.L_BRACK;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.L_CURLY;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.L_PAREN;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.NAME;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.PIPE;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.R_BRACK;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.R_CURLY;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.R_PAREN;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.SLASH;

import com.pentlander.sasquach.Preconditions;
import com.pentlander.sasquach.rdparser.Parser.BacktrackException;
import com.pentlander.sasquach.rdparser.Parser.Mark;
import com.pentlander.sasquach.rdparser.Parser.MarkClosed;
import com.pentlander.sasquach.rdparser.Parser.MarkOpened;
import com.pentlander.sasquach.rdparser.Parser.TreeKind;
import com.pentlander.sasquach.rdparser.Scanner.TokenType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class SasquachParser {
  private final Parser p;

  public SasquachParser(Parser parser) {
    this.p = parser;
  }

  void compilationUnit() {
    var mark = p.open();
    while (!p.isAtEnd()) {
      if (p.at(NAME)) {
        module();
      } else if (!p.eat(EOF)) {
        p.advanceWithError("expected a module name");
      }
    }
    p.close(mark, TreeKind.COMP_UNIT);
  }

  private void module() {
    // Module identifier
    var mark = expectOpen(NAME);
    if (p.at(L_CURLY)) {
      struct();
    }

    p.close(mark, TreeKind.MODULE);
  }

  private void struct() {
    var mark = expectOpen(L_CURLY);

    while (!p.at(R_CURLY) && !p.isAtEnd()) {
      structStatement();
      if (!p.at(R_CURLY)) {
        p.expect(COMMA);
      }
    }
    p.expect(R_CURLY);
    p.close(mark, TreeKind.STRUCT);
  }

  // TypeParamList = '[' Identifier (',' Identifier)* ']'
  private void typeParamList() {
    var mark = expectOpen(L_BRACK);
    commaSeparated(R_BRACK, () -> p.eat(NAME), () -> {});
    p.close(mark, TreeKind.TYPE_PARAM_LIST);
  }

  private void sumTypeDef() {
    var mark = expectOpen(PIPE);
  }

  // StructTypeMember = Name TypeAnnotation
  // StructTypeSpread = '..' NamedType?
  // StructType = '{' StructTypeMember | StructTypeSpread (',' StructTypeMember | StructTypeSpread)* '}'
  private void structType() {
    var mark = expectOpen(L_CURLY);
    boolean shouldContinue = true;
    while (!p.at(R_CURLY) && !p.isAtEnd() && shouldContinue) {
      switch (p.peek()) {
        case NAME -> assertTree(NAME, TreeKind.STRUCT_TYPE_MEMBER, this::typeAnnotation);
        case DOT_DOT -> assertTree(DOT_DOT, TreeKind.STRUCT_TYPE_SPREAD, () -> {
          if (p.at(NAME)) namedType();
        });
        default -> shouldContinue = false;
      }
      if (!p.at(R_CURLY)) {
        p.expect(COMMA);
      }
    }
    p.expect(R_CURLY);
    p.close(mark, TreeKind.STRUCT_TYPE);
  }

  private void tupleType() {
    var mark = expectOpen(L_PAREN);
    commaSeparated(R_PAREN, this::isTypeExprStart, this::typeExpr);
    p.close(mark, TreeKind.TUPLE_TYPE);
  }

  // TypeArgList = '[' TypeExpr, (',' TypeExpr)* ']'
  private void typeArgList() {
    var mark = expectOpen(L_BRACK);
    commaSeparated(R_BRACK, this::isTypeExprStart, this::typeExpr);
    p.close(mark, TreeKind.TYPE_ARG_LIST);
  }

  // NamedType = Identifier ('.' Identifier)? TypeArgList?
  private void namedType() {
    var mark = expectOpen(NAME);
    if (p.eat(DOT)) {
      p.expect(NAME);
    }

    if (p.at(L_BRACK)) {
      typeArgList();
    }
    p.close(mark, TreeKind.NAMED_TYPE);
  }


  // FunctionParam = Identifier? Identifier TypeAnnotation? ('=' Expression)?
  private void functionParam() {
    // Either the label or the param name
    assertTree(NAME, TreeKind.FUNCTION_PARAM, () -> {
      // If consume the next Identifier as well in case the first was a label
      p.eat(NAME);
      if (p.at(COLON)) {
        typeAnnotation();
      }
      if (p.eat(EQ)) {
        expr();
      }
    });
  }

  // FunctionParamList = '(' FunctionParam? (',' FunctionParam)* ')'
  private void functionParamList() {
    assertTree(
        L_PAREN,
        TreeKind.FUNCTION_PARAM_LIST,
        () -> commaSeparated(R_PAREN, () -> p.at(NAME), this::functionParam));
  }

  // FunctionType = FunctionParamList '->' TypeExpr
  private void functionType() {
    var mark = p.open();
    functionParamList();
    p.expect(ARROW);
    typeExpr();
    p.close(mark, TreeKind.FUNCTION_TYPE);
  }

  // TypeAnnotation = ':' TypeExpr
  private void typeAnnotation() {
    var mark = expectOpen(COLON);
    typeExpr();
    p.close(mark, TreeKind.TYPE_ANNOTATION);
  }

  private boolean isTypeExprStart() {
    return switch (p.peek()) {
      case L_CURLY, NAME, L_PAREN -> true;
      default -> false;
    };
  }

  // TypeExpr = StructType | NamedType | FunctionType | TupleType
  void typeExpr() {
    checkStart(this::isTypeExprStart, "type expr");

    var mark = p.open();
    boolean isError = false;
    switch (p.peek()) {
      // StructType
      case L_CURLY -> structType();
      // NamedType
      case NAME -> namedType();
      // FunctionType | TupleType
      case L_PAREN -> {
        switch (p.nth(1)) {
          case EOF -> {}
          // Must
          case R_PAREN -> functionType();
          // Could still be either a function type or a tuple type
          case NAME -> {
            switch (p.nth(2)) {
              //  foo followed by '[A]' | ',' means it must be a tuple type.
              case L_BRACK, COMMA, R_PAREN -> tupleType();
              default -> functionType();
            }
          }
          // '(' must be followed by a label or param name in a function type, so it must be a tuple
          default -> tupleType();
        }
      }
      default -> isError = true;
    }
    p.close(mark, !isError ? TreeKind.TYPE_EXPR : TreeKind.ERROR_TREE);
  }

  void commaSeparated(TokenType endToken, Supplier<Boolean> checkShouldParse, Runnable parser, TokenType... recoveryTokens) {
    var recoverySet = Set.of(recoveryTokens);
    while (!p.at(endToken) && !p.isAtEnd()) {
      if (checkShouldParse.get()) {
        parser.run();
        if (!p.at(endToken)) {
          p.expect(COMMA);
        }
      } else {
        if (recoveryTokens.length == 0) {
          break;
        } else {
          if (recoverySet.contains(p.peek())) {
            break;
          }
          p.advanceWithError("expected");
        }
      }
    }
    p.expect(endToken);
  }

  private void qualifiedName() {
    assertTree(NAME, TreeKind.QUALIFIED_NAME, () -> {
        do {
          p.expect(SLASH);
          p.expect(NAME);
        } while (p.at(SLASH) && !p.isAtEnd());
    });
  }

  private void varDecl() {
    assertTree(LET, TreeKind.VAR_DECL, () -> {
      p.expect(NAME);
      if (p.at(COLON)) {
        typeAnnotation();
      }
      p.expect(EQ);
      expr();
    });
  }

  private boolean isPatternStart() {
    return p.peek() == NAME;
  }
  private void pattern() {
    checkStart(this::isPatternStart, "pattern");

    var mark = p.open();
    namedType();
    // Named tuple pattern
    if (p.eat(L_PAREN)) {
      commaSeparated(R_PAREN, () -> p.at(NAME), p::advance);
    } else if (p.eat(L_CURLY)) {
      commaSeparated(R_CURLY, () -> p.at(NAME), p::advance);
    }
    p.close(mark, TreeKind.PATTERN);
  }

  private void funcExpr() {
    assertAt(L_PAREN);
    functionParamList();
    if (p.eat(COLON)) {
      typeExpr();
    }
    p.expect(ARROW);
    expr();
  }


  public boolean isBlockStatementStart() {
    return switch (p.peek()) {
      case LET, PRINT -> true;
      default -> atExprDelimitedStart();
    };
  }

  private void blockStatement() {
    var mark = p.open();
    switch (p.peek()) {
      case LET -> {
        p.advance();
        p.expect(NAME);
        if (p.at(COLON)) {
          typeAnnotation();
        }
        p.expect(EQ);
        expr();
        p.close(mark, TreeKind.VAR_DECL);
      }
      case PRINT -> {
        p.advance();
        expr();
        p.close(mark, TreeKind.PRINT_STMT);
      }
      default -> {
        if (atExprDelimitedStart()) {
          expr();
          p.close(mark, TreeKind.EXPR);
        } else {
          throw new IllegalStateException("Must check if valid start before calling");
        }
      }
    }
  }

  enum ExprState {
    UNARY, BINARY
  }

  enum Operator {
    UNKNOWN(-1),
    MEMBER_ACCESS(0), FOREIGN_ACCESS(0),
    NOT(1), NEG(1),
    MULT(2), DIV(2),
    PLUS(3), MINUS(3),
    EQ(4), NEQ(4), GE(4), GT(4), LE(4), LT(4),
    AND(5), OR(5),
    SEPARATOR(6),
    APPLY(7), PIPE(7),
    CLOSE_PAREN(8);

    private final int precedence;

    Operator(int precedence) {
      this.precedence = precedence;
    }

    public boolean higherPrecedenceThan(Operator other) {
      return precedence < other.precedence;
    }

    public static Operator fromToken(TokenType tokenType) {
      return switch (tokenType) {
        case PLUS -> Operator.PLUS;
        case MINUS -> Operator.MINUS;
        case STAR -> Operator.MULT;
        case SLASH -> Operator.DIV;
        case DOT -> Operator.MEMBER_ACCESS;
        case EQ -> Operator.EQ;
        case BANG_EQ -> Operator.NEQ;
        case GT_EQ -> Operator.GE;
        case GT -> Operator.GT;
        case LT_EQ -> Operator.LE;
        case LT -> Operator.LT;
        case AMP_AMP -> Operator.AND;
        case PIPE_PIPE -> Operator.OR;
        case L_PAREN -> Operator.APPLY;
        case PIPE_OP -> Operator.PIPE;
        case COMMA -> Operator.SEPARATOR;
        case R_PAREN -> Operator.CLOSE_PAREN;
        default -> Operator.UNKNOWN;
      };
    }
  }

  private static final class OperatorState {
    private ExprState exprState = ExprState.UNARY;
    private final List<Operator> operatorStack = new ArrayList<>();
    private final List<Mark> operandStack = new ArrayList<>();
    private int openCount = 0;

    public ExprState exprState() {
      return exprState;
    }

    public void toggleState() {
      exprState = switch (exprState) {
        case UNARY -> ExprState.BINARY;
        case BINARY -> ExprState.UNARY;
      };
    }

    public void addOperator(Operator op) {
      if (op == Operator.APPLY) openCount++;

      operatorStack.add(op);
      toggleState();
    }

    public boolean operatorsEmpty() {
      return operatorStack.isEmpty();
    }

    public Operator peekOperator() {
      return operatorStack.getLast();
    }

    public Operator popOperator() {
      var op = operatorStack.removeLast();
      if (op == Operator.APPLY) openCount--;
      return op;
    }

    public void addOperand(Mark mark) {
      operandStack.add(mark);
    }

    public Mark popOperand() {
      return operandStack.removeLast();
    }

    public boolean isOpen() {
      return openCount > 0;
    }

    @Override
    public String toString() {
      return "OperatorState[" + "exprState=" + exprState + ", " + "operatorStack=" + operatorStack
          + ", " + "operandStack=" + operandStack + ']';
    }
  }

  void reduceOperator(OperatorState opState) {
    var operator = opState.popOperator();
    switch (operator) {
      case PLUS -> {
        opState.popOperand();
        var first = opState.popOperand();
        var mark = p.openBefore(first);
        p.close(mark, TreeKind.EXPR_ADD);
        opState.addOperand(mark);
      }
      case MULT -> {
        opState.popOperand();
        var first = opState.popOperand();
        var mark = p.openBefore(first);
        p.close(mark, TreeKind.EXPR_MULT);
        opState.addOperand(mark);
      }
      case SEPARATOR -> {}
      case APPLY -> {
        opState.popOperand();
        var first = opState.popOperand();
        var mark = p.openBefore(first);
        p.close(mark, TreeKind.EXPR_APPLY);
        opState.addOperand(mark);
      }
    }
  }

  private void expr() {
    var state = new OperatorState();
    while (true) {
      if (!exprState(state)) {
        break;
      }
    }
  }

  private boolean exprState(OperatorState opState) {
    boolean shouldContinue = true;
    switch (opState.exprState()) {
      case UNARY -> {
        if (atExprDelimitedStart()) {
          opState.addOperand(exprDelimited());
          opState.toggleState();
        } else if (p.at(NAME)) {
          // Don't know if this is a var reference, func call, named struct, etc. until we see the next token
          var mark = p.open();
          p.advance();
          p.close(mark, TreeKind.NAME);
          opState.addOperand(mark);
          opState.toggleState();
        } else {
          p.advanceWithError("invalid expr");
          shouldContinue = false;
        }
      }
      // foo(1 + 3, bar)
      case BINARY -> {
        var op = Operator.fromToken(p.peek());
        if (op == Operator.CLOSE_PAREN && opState.isOpen()) {
          var operands = new ArrayDeque<Mark>();
          operands.addFirst(opState.popOperand());
          Operator operator;
          do {
            operands.addFirst(opState.popOperand());
            operator = opState.popOperator();
          } while (operator != Operator.APPLY);
          var mark = p.openBefore(operands.getFirst());
          p.close(mark, TreeKind.EXPR_APPLY);
          opState.addOperand(mark);
          p.advance();
          // A separator (i.e. ',') is only valid when there's some sort of "open" operator in the stack, usually a '('. If a separator appears without an open operator, then it must be the end of a statement or an error
        } else if (op != Operator.UNKNOWN) {
          while (!opState.operatorsEmpty() && opState.peekOperator()
              .higherPrecedenceThan(op)) {
            reduceOperator(opState);
          }
          if (op == Operator.SEPARATOR && !opState.isOpen()) {
            shouldContinue = false;
          } else {
            opState.addOperator(op);
            p.advance();
          }
        } else {
          while (!opState.operatorsEmpty()) {
            reduceOperator(opState);
          }
          shouldContinue = false;
        }
      }
    }
    return shouldContinue;
  }

  private boolean atExprDelimitedStart() {
    return switch (p.peek()) {
      case TRUE, FALSE, NUMBER, STRING, LOOP, MATCH, L_PAREN, L_CURLY, MINUS, BANG -> true;
      default -> false;
    };
  }

  private MarkClosed exprDelimited() {
    checkStart(this::atExprDelimitedStart, "expr");

    var mark = p.open();
    return switch (p.peek()) {
      case TRUE, FALSE, NUMBER, STRING -> {
        p.advance();
        yield p.close(mark, TreeKind.EXPR_LITERAL);
      }
      case LOOP -> {
        p.advance();
        p.expect(L_PAREN);
        commaSeparated(R_PAREN, () -> p.at(LET), this::varDecl);
        p.expect(ARROW);
        expr();
        yield p.close(mark, TreeKind.EXPR_LOOP);
      }
      case MATCH -> {
        p.advance();
        expr();
        p.expect(L_CURLY);
        commaSeparated(R_CURLY, this::isPatternStart, this::pattern);
        yield p.close(mark, TreeKind.EXPR_MATCH);
      }
      // ParenExpr | TupleExpr | FuncExpr
      case L_PAREN -> {
        if (tryParse(this::funcExpr)) {
          yield p.close(mark, TreeKind.EXPR_FUNC);
        } else {
          p.advance();
          expr();
          if (p.eat(COMMA)) {
            commaSeparated(R_PAREN, this::atExprDelimitedStart, this::expr);
            yield p.close(mark, TreeKind.EXPR_TUPLE);
          } else if (p.eat(R_PAREN)) {
            yield p.close(mark, TreeKind.EXPR_PAREN);
          } else {
            yield p.close(mark, TreeKind.ERROR_TREE);
          }
        }
      }
      case L_CURLY -> {
        p.advance();
        while (!p.at(R_CURLY) && !p.isAtEnd()) {
          if (isBlockStatementStart()) {
            blockStatement();
          } else {
            break;
          }
        }
        p.expect(R_CURLY);
        yield p.close(mark, TreeKind.EXPR_BLOCK);
      }
      case MINUS -> {
        p.advance();
        expr();
        yield p.close(mark, TreeKind.EXPR_NEGATE);
      }
      case BANG -> {
        p.advance();
        expr();
        yield p.close(mark, TreeKind.EXPR_NOT);
      }
      default -> p.advanceWithError(mark, "failed");
    };
  }

  private void structStatement() {
    var mark = p.open();
    switch (p.peek()) {
      case USE -> {
        p.advance();
        p.eat(FOREIGN);
        qualifiedName();
      }
      case TYPE, TYPEALIAS -> {
        p.advance();
        p.expect(NAME);
        if (p.at(L_BRACK)) {
          typeParamList();
        }
        p.expect(EQ);

        switch (p.peek()) {
          case PIPE -> sumTypeDef();
          default -> typeExpr();
        }
      }
      // Member definition
      case NAME -> {
        p.advance();
        p.expect(EQ);
        expr();
      }
      // Spread operator
      case DOT_DOT -> {
        p.advance();
        p.expect(NAME);
      }
      default -> {
        if (!p.isAtEnd()) p.advance();
      }
    }
    p.close(mark, TreeKind.STRUCT_STATEMENT);
  }

  private MarkOpened assertOpen(TokenType tokenType) {
    assertAt(tokenType);
    return p.open();
  }

  private MarkOpened expectOpen(TokenType tokenType) {
    var mark = assertOpen(tokenType);
    p.expect(tokenType);
    return mark;
  }

  private void assertTree(TokenType tokenType, TreeKind treeKind, Runnable runnable) {
    assertAt(tokenType);
    var mark = p.open();
    p.expect(tokenType);
    runnable.run();
    p.close(mark, treeKind);
  }

  private void assertAt(TokenType... tokenTypes) {
    for (var type : tokenTypes) {
      if (p.at(type)) return;
    }
    throw new IllegalStateException("Expected to be at token type '%s', found: %s".formatted(Arrays.toString(
        tokenTypes), p.peekToken()));
  }

  // Check that the method used to check if the starting token is valid matches the actual implementation
  private void checkStart(Supplier<Boolean> startPred, String checkName) {
    Preconditions.checkState(startPred.get(), "Does not match " + checkName + " start");
  }

  private boolean tryParse(Runnable parser) {
    p.checkpoint();
    try {
      parser.run();
      p.clearCheckpoint();
      return true;
    } catch (BacktrackException e) {
      p.restore();
      return false;
    }
  }
}
