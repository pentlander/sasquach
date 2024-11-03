package com.pentlander.sasquach.rdparser;

import com.pentlander.sasquach.Preconditions;
import com.pentlander.sasquach.rdparser.Parser.Child.ChildToken;
import com.pentlander.sasquach.rdparser.Parser.Child.ChildTree;
import com.pentlander.sasquach.rdparser.Parser.Event.EventKind;
import com.pentlander.sasquach.rdparser.Parser.Event.Open;
import com.pentlander.sasquach.rdparser.Scanner.Token;
import com.pentlander.sasquach.rdparser.Scanner.TokenType;
import java.util.ArrayList;
import java.util.List;

public class Parser {
  private static final int DEFAULT_FUEL = 256;
  private int current = 0;
  private int fuel = DEFAULT_FUEL;
  private final List<Event> events = new ArrayList<>();

  private final List<Token> tokens;
  private boolean shouldBacktrack;
  private int checkpointCurrent = 0;
  private int checkpointEventsSize = 0;

  Parser(List<Token> tokens, boolean shouldBacktrack) {
    this.tokens = tokens;
    this.shouldBacktrack = shouldBacktrack;
  }

  Parser(List<Token> tokens) {
    this(tokens, false);
  }

  MarkOpened open() {
    var mark = new MarkOpened(events.size());
    events.add(Event.open(TreeKind.ERROR_TREE));
    return mark;
  }

  void close(MarkOpened mark, TreeKind treeKind) {
    events.set(mark.idx(), Event.open(treeKind));
    events.add(EventKind.CLOSE);
  }

  boolean isAtEnd() {
    return current == tokens.size();
  }

  void advance() {
    Preconditions.checkState(!isAtEnd(), "advanced past EOF");
    fuel = DEFAULT_FUEL;
    events.add(EventKind.ADVANCE);
    current++;
  }

  Token peekToken() {
    return tokens.get(current);
  }

  TokenType peek() {
    return nth(0);
  }

  TokenType nth(int lookahead) {
    Preconditions.checkState(fuel != 0, "parser is stuck");
    fuel--;

    int idx = current + lookahead;
    return idx < tokens.size() ? tokens.get(idx).type() : TokenType.EOF;
  }

  boolean at(TokenType tokenType) {
    return peek().equals(tokenType);
  }

  /** Advance the cursor if the token type matches. */
  boolean eat(TokenType tokenType) {
    if (at(tokenType)) {
      advance();
      return true;
    }
    return false;
  }

  /** Advance the cursor if the token type matches, otherwise report an error. */
  void expect(TokenType tokenType) {
    if (eat(tokenType)) {
      return;
    }

    var tokenFound = current < tokens.size() ? tokens.get(current) : null;
    var msg = "expected '%s', but found: %s".formatted(tokenType, tokenFound);
    if (shouldBacktrack) {
      throw new BacktrackException(msg);
    } else {
      System.err.println(msg);
    }
  }

  void advanceWithError(String error) {
    var mark = open();
    System.err.println("error: " + error);
    advance();
    close(mark, TreeKind.ERROR_TREE);
  }

  void checkpoint() {
    shouldBacktrack = true;
    checkpointCurrent = current;
    checkpointEventsSize = events.size();
  }

  void clearCheckpoint() {
    shouldBacktrack = false;
    checkpointCurrent = 0;
    checkpointEventsSize = 0;
  }

  void restore() {
    current = checkpointCurrent;
    events.subList(checkpointEventsSize, events.size()).clear();
    clearCheckpoint();
  }

  Tree buildTree() {
    var tokensIter = tokens.iterator();
    var stack = new ArrayList<Tree>();

    Preconditions.checkState(EventKind.CLOSE.equals(events.removeLast()), "must end in close event");
    for (var event : events) {
      switch (event) {
        case Open(var treeKind) -> stack.add(new Tree(treeKind, new ArrayList<>()));
        case EventKind.ADVANCE -> stack.getLast().children().add(new Child.ChildToken(tokensIter.next()));
        case EventKind.CLOSE -> {
          var tree = stack.removeLast();
          stack.getLast().children().add(new ChildTree(tree));
        }
      }
    }

    Preconditions.checkState(stack.size() == 1,"stack must only contain single tree");
    Preconditions.checkState(!tokensIter.hasNext(),"must consume all tokens");

    return stack.getLast();
  }

  record MarkOpened(int idx) {}

  sealed interface Event {
    record Open(TreeKind treeKind) implements Event {}

    enum EventKind implements Event {
      CLOSE, ADVANCE
    }

    static Event.Open open(TreeKind treeKind) {
      return new Open(treeKind);
    }
  }


  static final class BacktrackException extends RuntimeException {
    public BacktrackException(String message) {
      super(message);
    }
  }

  enum TreeKind {
    ERROR_TREE, COMP_UNIT, QUALIFIED_NAME,

    MODULE, STRUCT, STRUCT_STATEMENT, TYPE_ARG_LIST, TYPE_ANNOTATION, VAR_DECL,

    // Function definition
    TYPE_PARAM_LIST, FUNCTION_PARAM, FUNCTION_PARAM_LIST,

    // Types
    TYPE_EXPR, NAMED_TYPE, FUNCTION_TYPE, TUPLE_TYPE, SUM_TYPEDEF,
    STRUCT_TYPE, STRUCT_TYPE_MEMBER, STRUCT_TYPE_SPREAD,

    // Expressions
    EXPR, EXPR_LITERAL, EXPR_VAR_REF, EXPR_LOOP, EXPR_PAREN, EXPR_MATCH, EXPR_IF, EXPR_BLOCK, EXPR_FUNC, EXPR_TUPLE,
    PATTERN,
  }

  record Tree(TreeKind treeKind, List<Child> children) {
    @Override
    public String toString() {
      var sb = new StringBuilder(treeKind.toString());
      for (var child : children) {
        sb.append("\n  ").append(child);
      }
      return sb.toString();
    }

    public String treeString() {
      return treeString(this, 0);
    }

    private static String treeString(Tree tree, int depth) {
      var sb = new StringBuilder(tree.treeKind().toString());
      for (var child : tree.children()) {
        sb.append("\n").append(" ".repeat(depth * 2));
        switch (child) {
          case ChildToken childToken -> sb.append(childToken);
          case ChildTree(var childTree) -> sb.append(treeString(childTree, depth + 1));
        }
      }
      return sb.toString();
    }
  }

  sealed interface Child {
    record ChildToken(Token token) implements Child {
      @Override
      public String toString() {
        return token.toString();
      }
    }
    record ChildTree(Tree tree) implements Child {
      @Override
      public String toString() {
        return tree.toString();
      }
    }
  }
}