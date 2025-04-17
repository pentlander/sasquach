package com.pentlander.sasquach.rdparser;

import com.pentlander.sasquach.AbstractRangedError;
import com.pentlander.sasquach.Preconditions;
import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.RangedErrorList;
import com.pentlander.sasquach.rdparser.Parser.Child.ChildToken;
import com.pentlander.sasquach.rdparser.Parser.Child.ChildTree;
import com.pentlander.sasquach.rdparser.Parser.Event.EventKind;
import com.pentlander.sasquach.rdparser.Parser.Event.Open;
import com.pentlander.sasquach.rdparser.Scanner.Token;
import com.pentlander.sasquach.rdparser.Scanner.TokenType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

public class Parser {
  private static final int DEFAULT_FUEL = 256;
  /// Index into current location in [#tokens]
  private int current = 0;
  private int fuel = DEFAULT_FUEL;
  private final List<Event> events = new ArrayList<>();
  private final RangedErrorList.Builder errors = RangedErrorList.builder();

  private final List<Token> tokens;
  private final Set<Integer> newlineTokenIndexes;
  private boolean shouldBacktrack;
  private int checkpointCurrent = 0;
  private int checkpointEventsSize = 0;

  Parser(List<Token> tokens, Set<Integer> newlineTokenIndexes, boolean shouldBacktrack) {
    this.tokens = tokens;
    this.newlineTokenIndexes = newlineTokenIndexes;
    this.shouldBacktrack = shouldBacktrack;
  }

  Parser(List<Token> tokens, Set<Integer> newlineTokenIndexes) {
    this(tokens, newlineTokenIndexes, false);
  }

  public RangedErrorList errors() {
    return errors.build();
  }

  MarkOpened open() {
    var mark = new MarkOpened(events.size(), current);
    events.add(Event.open(TreeKind.ERROR_TREE));
    return mark;
  }

  MarkOpened openBefore(Mark mark) {
    var openMark = new MarkOpened(mark.idx(), mark.tokenIdx());
    events.add(mark.idx(), Event.open(TreeKind.ERROR_TREE));
    return openMark;
  }

  MarkClosed close(MarkOpened mark, TreeKind treeKind) {
    events.set(mark.idx(), Event.open(treeKind));
    events.add(EventKind.CLOSE);
    return new MarkClosed(mark.idx(), mark.tokenIdx());
  }

  boolean startOfLine() {
    return newlineTokenIndexes.contains(current);
  }

  boolean isAtEnd() {
    return current == tokens.size();
  }

  boolean shouldBacktrack() {
    return shouldBacktrack;
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
      addError(tokenType.toString());
      System.err.println(msg);
    }
  }

  private void addError(String expected) {
    var tokenFound = peekToken();
    var msg = "expected '%s', but found: %s".formatted(expected, tokenFound);
    errors.add(new ParseError(msg, tokenFound.range()));
  }

  static class ParseError extends AbstractRangedError {
    protected ParseError(String message, Range range) {
      super(message, range, null);
    }
  }

  MarkClosed advanceWithError(MarkOpened mark, String error) {
    System.err.println("error: " + error);
    addError(error);
    advance();
    return close(mark, TreeKind.ERROR_TREE);
  }

  void advanceWithError(String error) {
    var mark = open();
    advanceWithError(mark, error);
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
          stack.getLast().children().add(new ChildTree(tree, tree.firstToken(), tree.lastToken()));
        }
      }
    }

    Preconditions.checkState(stack.size() == 1,"stack must only contain single tree");
    Preconditions.checkState(!tokensIter.hasNext(),"must consume all tokens");

    return stack.getLast();
  }

  interface Mark {
    int idx();
    int tokenIdx();
  }

  record MarkOpened(int idx, int tokenIdx) implements Mark {}
  record MarkClosed(int idx, int tokenIdx) implements Mark {}

  ///  Indicates when a new sub [Tree] should open/close
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

    MODULE,
    EXPR_STRUCT,
    EXPR_NAMED_STRUCT, STRUCT_STATEMENT, TYPE_ARG_LIST, TYPE_ANNOTATION,
    VAR_DECL,
    BLOCK_PRINT_STMT,

    // Function definition
    TYPE_PARAM_LIST, FUNCTION_PARAM, FUNCTION_PARAM_LIST,

    // Types
    TYPE_EXPR, NAMED_TYPE, FUNCTION_TYPE, TUPLE_TYPE,
    SUM_TYPEDEF, VARIANT_TYPE_STRUCT, VARIANT_TYPE_TUPLE, VARIANT_TYPE_SINGLETON,
    STRUCT_TYPE, STRUCT_TYPE_MEMBER, STRUCT_TYPE_SPREAD,

    // Expressions
    BLOCK_EXPR, EXPR_LITERAL, EXPR_VAR_REF, EXPR_LOOP, EXPR_PAREN, EXPR_MATCH, EXPR_IF, EXPR_BLOCK, EXPR_FUNC, EXPR_TUPLE, EXPR_NEGATE, EXPR_NOT,
    EXPR_MEMBER_ACCESS, EXPR_FOREIGN_ACCESS, EXPR_APPLY, EXPR_PIPE,
    EXPR_BIN_MATH,
    EXPR_BIN_COMPARE,

    PATTERN, MATCH_BRANCH,
    LOOP_VAR_DECLS,
    EXPR_BIN_BOOLEAN,
  }

  // Need to attach a Range to the tree
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
          case ChildTree(var childTree, _, _) -> sb.append(treeString(childTree, depth + 1));
        }
      }
      return sb.toString();
    }

    public Token firstToken() {
      return switch (children.getFirst()) {
        case ChildTree(_, var firstToken, _) -> firstToken;
        case ChildToken(var token) -> token;
      };
    }

    public Token lastToken() {
      return switch (children.getLast()) {
        case ChildTree(_, _, var lastToken) -> lastToken;
        case ChildToken(var token) -> token;
      };
    }

    public TreeReader read() {
      var range = firstToken().range().join(lastToken().range());
      return new TreeReader(treeKind, range, children);
    }
  }

  static class TreeReader {
    private final TreeKind treeKind;
    private final Range range;
    private final Deque<Child> children;

    TreeReader(TreeKind treeKind, Range range, Collection<Child> children) {
      this.treeKind = treeKind;
      this.range = range;
      this.children = new ArrayDeque<>(children);
    }

    TreeReader copy() {
      return new TreeReader(treeKind, range, children);
    }

    void advance() {
      children.removeFirst();
    }

    Range range() {
      return range;
    }

    Range.Single singleRange() {
      return (Range.Single) range;
    }

    boolean hasRemaining() {
      return !children.isEmpty();
    }

    boolean nextIs(TokenType tokenType) {
      var next = children.getFirst();
      if (next instanceof ChildToken(var token)) {
        return token.hasType(tokenType);
      }
      return false;
    }

    Token peekToken() {
      return children.getFirst().expectToken();
    }

    TreeReader peekTree() {
      return children.getFirst().expectTree().read();
    }

    @Nullable
    Token eatToken(TokenType tokenType) {
      if (hasRemaining() && children.getFirst() instanceof ChildToken(var token) && token.hasType(tokenType)) {
        children.removeFirst();
        return token;
      }
      return null;
    }

    @Nullable
    TreeReader eatTree(TreeKind treeKind) {
      if (hasRemaining() && children.getFirst() instanceof ChildTree(var tree, _, _) && tree.treeKind().equals(treeKind)) {
        children.removeFirst();
        return tree.read();
      }
      return null;
    }

    Token expectToken() {
      return children.removeFirst().expectToken();
    }

    TreeReader expectTree() {
      return children.removeFirst().expectTree().read();
    }

    Token expectToken(TokenType tokenType) {
      return children.removeFirst().expect(tokenType);
    }

    TreeReader expectTree(TreeKind treeKind) {
      return children.removeFirst().expect(treeKind);
    }

    TreeReader assertTree(TreeKind treeKind) {
      Preconditions.checkArgument(this.treeKind.equals(treeKind), "Tree %s@%s does not match "
          + "expected: '%s'", this.treeKind, range(), treeKind);
      return this;
    }

    List<Child> remaining() {
      return List.copyOf(children);
    }

    Stream<TreeReader> filterChildren(TreeKind ...treeKinds) {
      var treeKindsSet = Set.of(treeKinds);
      return children.stream()
          .flatMap(child -> child instanceof ChildTree(var t, _, _) && treeKindsSet.contains(t.treeKind())
              ? Stream.of(t.read()) : Stream.empty());
    }

    Stream<TreeReader> filterChildrenTrees() {
      return children.stream()
          .flatMap(child -> child instanceof ChildTree(var t, _, _) ? Stream.of(t.read())
              : Stream.empty());
    }

    Stream<Token> filterChildren(TokenType tokenType) {
      return children.stream()
          .flatMap(child -> child instanceof ChildToken(var ct) && ct.type().equals(tokenType)
              ? Stream.of(ct) : Stream.empty());
    }

    public TreeKind treeKind() {
      return treeKind;
    }
  }

  sealed interface Child {
    record ChildToken(Token token) implements Child {
      @Override
      public String toString() {
        return token.toString();
      }
    }
    record ChildTree(Tree tree, Token firstToken, Token lastToken) implements Child {
      @Override
      public String toString() {
        return tree.toString();
      }
    }

    default Token expectToken() {
      return switch (this) {
        case ChildToken(var token) -> token;
        case ChildTree(_, _, _) -> throw new IllegalStateException("expected token");
      };
    }

    default Tree expectTree() {
      return switch (this) {
        case ChildTree(var tree, _, _) -> tree;
        case ChildToken(_) -> throw new IllegalStateException("expected tree, found: " + this);
      };
    }


    default TreeReader expect(TreeKind treeKind) {
      var tree = expectTree();
      var tr = tree.read();
      Preconditions.checkState(
          tree.treeKind().equals(treeKind),
          "%s@%s does not match expected tree kind: %s",
          tree.treeKind(),
          tr.range(),
          treeKind);
      return tree.read();
    }

    default Token expect(TokenType tokenType) {
      var token = expectToken();
      Preconditions.checkState(
          token.type().equals(tokenType),
          "does not match expected token type: %s",
          tokenType);
      return token;
    }
  }
}
