package com.pentlander.sasquach.rdparser;

import static org.assertj.core.api.Assertions.assertThat;

import com.pentlander.sasquach.CompilationException;
import com.pentlander.sasquach.PackageName;
import com.pentlander.sasquach.Source;
import com.pentlander.sasquach.SourcePath;
import com.pentlander.sasquach.Sources;
import com.pentlander.sasquach.rdparser.Parser.Child.ChildTree;
import com.pentlander.sasquach.rdparser.Parser.Tree;
import com.pentlander.sasquach.rdparser.Parser.TreeKind;
import com.pentlander.sasquach.rdparser.Scanner.TokenType;
import java.util.function.Consumer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SasquachParserTest {
  @Test
  void tuple() {
    var tree = parse("""
        Test {
          use std/Option,
          use std/Int,
        
          type Box[A] = { item: A, ..A },
          type Id = (String),
        
          test0 = (true),
          test1 = (true,),
          test2 = (true, 30 + 5),
          test = (foo) -> true,
        }
        
        Bar {
          type T = String,
        }
        """);
    printAndAssertNoErrors(tree);
  }

  @Test
  void ref() {
    var tree = parse("""
        Iterator {
          use std/Option,
          use std/Ref,

          typealias T[A] = { next: () -> Option.T[A], .. },

          // the expr is getting parsed as a block containing a compare instead of a struct init
          new = [A](next: () -> Option.T[A]): T[A] -> { next = next },

          next = [A](iter: T[A]): Option.T[A] -> iter.next(),

          map = [A, B](iter: T[A], fn: (item: A) -> B): T[B] -> new(() ->
            iter |> next() |> Option.map(fn)
          ),

          filter = [A](iter: T[A], fn: (item: A) -> Boolean): T[A] ->
            new(() ->
              loop () -> match next(iter) {
                Option.Some(item) -> if fn(item) Option.Some(item) else recur(),
                Option.None -> Option.None,
              }
            ),

          filterMap = [A, B](iter: T[A], fn: (item: A) -> Option.T[B]): T[B] ->
            new(() ->
              loop () -> match next(iter) {
                Option.Some(item) -> match fn(item) {
                  Option.Some(mappedItem) -> Option.Some(mappedItem),
                  Option.None -> recur(),
                },
                Option.None -> Option.None,
              }
            ),

          flatMap = [A, B](iter: T[A], fn: (item: A) -> T[B]): T[B] -> {
            let nestedIterRef = Ref.new(Option.None)
            new(() -> {
              // Option.T[T[B]]
              Ref.get(nestedIterRef)
                |> Option.orElse(() ->
                  next(iter) |> Option.map((item) -> {
                    let newNestedIter = fn(item)
                    Ref.set(nestedIterRef, Option.Some(newNestedIter))
                    newNestedIter
                  })
                )
                |> Option.flatMap((nestedIter) -> next(nestedIter))
            })
          },

          reduce = [A, B](iter: T[A], init: B, accumulator: (accum: B, value: A) -> B): B ->
            loop (let accum = init) -> match next(iter) {
              Option.Some(item) -> recur(accumulator(accum, item)),
              Option.None -> accum,
            },

          forEach = [A](iter: T[A], fn: (item: A) -> Void): Void -> {
            loop () -> match next(iter) {
              Option.Some(item) -> {
                fn(item)
                recur()
              },
              Option.None -> {},
            }
          },
        }
        """);
    printAndAssertNoErrors(tree);
  }

  @Test
  void tuple2() {
    var tree = parse("""
        Test {
          test0 = (true),
          test1 = (true,),
          test2 = {
            (true, 30)
          },
          test = (foo) -> true,
        }
        
        Bar {
          type T = String,
        }
        """);
    printAndAssertNoErrors(tree);
  }

  @Test
  void namedStruct() {
    var tree = parse("""
        Test {
          type Foo = { bar: Int, baz: String },
          test = Foo { bar = 1 + 2, baz = "hello" },
        }
        """);
    printAndAssertNoErrors(tree);
  }

  @Test
  void funcApplication() {
    var tree = parse("""
        Test {
          test = foo(bar, 10, 5),
        }
        """);
    printAndAssertNoErrors(tree);
  }

  @Test
  void memberFuncApplication() {
    var tree = parse("""
        Test {
          test = foo.baz(bar, 10, 5),
        }
        """);
    printAndAssertNoErrors(tree);
  }

  /**
   * Need to decide when an end of line should complete a statement. It's only relevant when the
   * parser is in a binary statement, in other cases it's unambiguous.
   */
  @Test
  void funcApplication_block() {
    var tree = parse("""
        Test {
          test = {
            foo
            (bar, 10, 5)
          }
        }
        """);
    printAndAssertNoErrors(tree);
  }

  @Test
  void memberAccess() {
    var tree = parse("""
        Test {
          test = foo.bar
        }
        """);
    printAndAssertNoErrors(tree);
  }

  @Test
  void funcWithAlias() {
    var tree = parse("""
        Test {
          typealias Foo = { bar: Int, baz: String },
        
          test = [A](str: String): Foo[A] -> { bar = 1, baz = str },
        }
        """);
    printAndAssertNoErrors(tree);
  }

  @Test
  void namedStruct_otherModule() {
    var tree = parse("""
        Foo {
          type T = { bar: Boolean },
        }
        
        Test {
          use test/Foo,
        
          test = {
            Foo.T {
              bar = true
            }
          }
        }
        """);
    printAndAssertNoErrors(tree);
  }

  @Test
  void funcApplication_noargs() {
    var tree = parse("""
        Test {
          test = foo(),
        }
        """);
    printAndAssertNoErrors(tree);
  }

  @Test
  void infix() {
    var tree = parse("""
        Test {
          test = 3 + 4 * 4,
        }
        """);
    printAndAssertNoErrors(tree);
  }

  @Nested
  class TypeExprTest {
    @Test
    void tuple() {
      var tree = parse(SasquachParser::typeExpr, "(Int, String)");
      System.out.println(tree.treeString());
    }

    @Test
    void function() {
      var tree = parse(SasquachParser::typeExpr, "(i: Int, s: String) -> Void");
      System.out.println(tree.treeString());
    }
  }

  private static void printAndAssertNoErrors(Tree tree) {
    System.out.println(tree.treeString());
    var result = AstBuilder.build(new PackageName("test"), new SourcePath("test.sasq"), tree);
    var errors = result.errors().errors();
    if (!errors.isEmpty()) {
      System.out.println(result.item());
      assertThat(errors).isEmpty();
    }
  }

  private static Tree parse(String sourceStr) {
    var source = Source.fromString("main", sourceStr);
    var scanner = new Scanner(source);
    var result = scanner.scanTokens();
    var parser = new Parser(result.tokens(), result.newlineIndexes());
    var sasqParser = new SasquachParser(parser);
    var res = sasqParser.build();
    try {
      res.errors().throwIfNotEmpty(Sources.single(source));
    } catch (CompilationException e) {
      throw new RuntimeException(e);
    }

    return res.item();
  }

  private static Tree parse(Consumer<SasquachParser> parse, String source) {
    var scanner = new Scanner(Source.fromString("main", source));
    var result = scanner.scanTokens();
    var parser = new Parser(result.tokens(), result.newlineIndexes());
    var mark = parser.open();
    var sasqParser = new SasquachParser(parser);
    parse.accept(sasqParser);
    parser.expect(TokenType.EOF);
    parser.close(mark, TreeKind.COMP_UNIT);
    return ((ChildTree) parser.buildTree().children().getFirst()).tree();
  }
}
