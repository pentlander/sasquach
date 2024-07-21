package com.pentlander.sasquach.e2e;

import static com.pentlander.sasquach.TestUtils.invokeName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.pentlander.sasquach.CompilationException;
import com.pentlander.sasquach.Compiler;
import com.pentlander.sasquach.SasquachClassloader;
import com.pentlander.sasquach.Source;
import com.pentlander.sasquach.TestUtils;
import com.pentlander.sasquach.ast.QualifiedModuleName;
import java.nio.file.Path;
import org.assertj.core.util.Files;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

public class EndToEndTest {
  private String testName;

  @BeforeEach
  void setup(TestInfo testInfo) {
    testName = testInfo.getDisplayName();
  }

  @Test
  void addition() throws Exception {
    var clazz = compileClass( """
        Main {
          plus = (): Int -> 3 + 4
        }
        """);
    int sum = invokeName(clazz, "plus");

    assertThat(sum).isEqualTo(7);
  }

  @Test
  void booleanExpr() throws Exception {
    var clazz = compileClass( """
        Main {
          foo = (): Boolean -> false && true || true
        }
        """);
    boolean bool = invokeName(clazz, "foo");

    assertThat(bool).isEqualTo(true);
  }

  @Test
  void tuple() throws Exception {
    var clazz = compileClass( """
        Main {
          tuplify = [A, B](a: A, b: B): (A, B) -> (a, b),
          foo = (): Int -> {
            let tuple = tuplify(5, "something")
            tuple._0
          },
        }
        """);
    // Figure out why the local named type isn't being replaced
    int sum = invokeName(clazz, "foo");

    assertThat(sum).isEqualTo(5);
  }

  @Test
  void loopRecur() throws Exception {
    var clazz = compileClass("""
        Main {
          plus = (): Int -> {
            loop (let a = 0) -> if (a > 4) {
              a
            } else {
              recur(a + 1)
            }
          }
        }
        """);
    int sum = invokeName(clazz, "plus");

    assertThat(sum).isEqualTo(5);
  }

  @Test
  void higherOrderFunc() throws Exception {
    var clazz = compileClassDebug( """
        Main {
          plus = (): Int -> {
            let add = (a: Int, b: Int): Int -> a + b
            add(1, 4)
          }
        }
        """);
    int sum = invokeName(clazz, "plus");

    assertThat(sum).isEqualTo(5);
  }

  @Test
  void applyOperator() throws Exception {
    var clazz = compileClass( """
        Main {
          inc = (i: Int): Int -> i + 1,
          timesTwo = (i: Int): Int -> i * 2,
          
          foo = (): Int -> {
            let add = (a: Int, b: Int): Int -> a + b
            10 |> inc() |> timesTwo() |> add(5)
          }
        }
        """);
    int sum = invokeName(clazz, "foo");

    assertThat(sum).isEqualTo(27);
  }

  @Test
  void matchSumType() throws Exception {
    var clazz = compileClass("""
        Main {
          type Option[T] = | Some(T) | None,
          
          foo = (): String -> {
            let option = if (true) Some("foo") else None
            match option {
              Some(str) -> str,
              None -> "",
            }
          },
        }
        """);
    String sum = invokeName(clazz, "foo");

    assertThat(sum).isEqualTo("foo");
  }

  @Test
  void matchSumType_generic() throws Exception {
    // Failed because the A in the typedef and the func get resolved to the same Universal type and both get replaced.
    var clazz = compileClass("""
        Main {
          type Option[A] = | Some(A) | None,
          
          isSome = [A](option: Option[A]): Boolean -> match option {
            Some(_) -> true,
            None -> false,
          },
          
          foo = (): Boolean -> {
            let option = if (true) Some("foo") else None
            isSome(option)
          },
        }
        """);
    boolean sum = invokeName(clazz, "foo");

    assertThat(sum).isTrue();
  }

  @Test
  void matchSumType_structVariant() throws Exception {
    var clazz = compileClass("""
        Main {
          type Option[T] = | Some { inner: T } | None,
          
          foo = (): String -> {
            let option = if (true) Some { inner = "foo" } else None
            match option {
              Some { inner } -> inner,
              None -> "",
            }
          },
        }
        """);
    String sum = invokeName(clazz, "foo");

    assertThat(sum).isEqualTo("foo");
  }

  // should fail
  @Test
  void matchSumType_sameParam_multipleVariants() throws Exception {
    var source = Source.fromString("main", """
        Main {
          type Same[T] = | A(T) | B(T),
          
          foo = (): String -> {
            let option = if (true) A("foo") else B(10)
            match option {
              A(str) -> str,
              B(int) -> int,
            }
          },
        }
        """);
    var ex = assertThrows(CompilationException.class, () -> compileClass(source));
    assertThat(ex).hasMessageContaining("should be 'String'");
  }

  @Test
  void sumTypeSingleton() throws Exception {
    var clazz = compileClass( """
        Main {
          type Option[T] = | Some(T) | None,
          
          identity = [T](option: Option[T]): Option[T] -> option,
          
          foo = (): Option[String] -> {
            let opt = None
            let ident = identity(None)
            ident
          },
        }
        """);
    Object singleton = invokeName(clazz, "foo");

    assertThat(singleton.getClass().getName()).isEqualTo("main.Main$None");
  }

  @Test
  @Disabled
    // Need to generate an interface for the sum type or more likely generate a class that takes a
    // function object in the constructor
  void sumTypeWithFunc() throws Exception {
    // let none = identity(None)
    var clazz = compileClass( """
        Main {
          type Option[T] = | Test { foo: (bar: Int) -> String },
          
          identity = [T](option: Option[T]): Option[T] -> option,
          
          foo = (): String -> {
            let some = Test { foo = (bar: Int): String -> "fox" }
            some.foo("foo")
          },
        }
        """);
    String sum = invokeName(clazz, "foo");

    assertThat(sum).isEqualTo("fox");
  }

  @Test
  void spread() throws Exception {
    var clazz = compileClass("""
        Main {
          main = (): { other: String, bar: String, baz: String } -> {
            let foo = { bar = "bar", baz = "baz" }
            { other = "hello", bar = "override", ..foo }
          }
        }
        """);
    Object baz = invokeName(clazz, "main");

    assertThat(baz).hasFieldOrPropertyWithValue("other", "hello")
        .hasFieldOrPropertyWithValue("bar", "override")
        .hasFieldOrPropertyWithValue("baz", "baz");
  }

  @Test
  void genericRow() throws Exception {
    var clazz = compileClass("""
        Main {
          addOther = [R](barRow: { bar: String, ..R }): { bar: String, other: String, ..R } -> {
            { other = "hello", ..barRow }
          },
          
          main = (): { bar: String, baz: String, other: String } -> {
            let foo = { bar = "bar", baz = "baz" }
            addOther(foo)
          }
        }
        """);

    Object struct = invokeName(clazz, "main");

    assertThat(struct).hasFieldOrPropertyWithValue("other", "hello")
        .hasFieldOrPropertyWithValue("bar", "bar")
        .hasFieldOrPropertyWithValue("baz", "baz");
  }

  @Test
  void genericRow_combine() throws Exception {
    var clazz = compileClass("""
        Main {
          combine = [R1, R2](struct1: { ..R1 }, struct2: { ..R2 }): { ..R1, ..R2 } -> {
            { ..struct1, ..struct2 }
          },
          
          main = (): { foo: String, bar: String } -> {
            let foo = { foo = "foo" }
            let bar = { bar = "bar", gg = "gg" }
            let baz = { baz = "baz" }
            combine(foo, baz)
            combine(foo, bar)
          }
        }
        """);

    Object struct = invokeName(clazz, "main");

    assertThat(struct).hasFieldOrPropertyWithValue("foo", "foo")
        .hasFieldOrPropertyWithValue("bar", "bar");
  }

  @Test
  void foreignFunctionCall_withField() throws Exception {
    var clazz = compileClass( """
        Main {
          use foreign java/io/PrintStream,
          use foreign java/lang/System,
          
          foo = (): Void -> {
            PrintStream#println(System#out, "test")
          }
        }
        """);
    invokeName(clazz, "foo", null);
  }

  @Test
  void foreignFunctionCall_mapGet() throws Exception {
    var clazz = compileClass( """
        Main {
          use foreign java/util/HashMap,
          
          foo = (): String -> {
            let map = HashMap#new()
            HashMap#put(map, "foo", "bar")
            HashMap#get(map, "foo")
          }
        }
        """);
    String value = invokeName(clazz, "foo", null);

    assertThat(value).isEqualTo("bar");
  }

  @Test
  void typeAliasStruct() throws Exception {
    var clazz = compileClass( """
        Main {
          type Point = { x: Int, y: Int },
          newPoint = (x: Int): Point -> { x = x, y = 4 },
          getX = (point: Point): Int -> point.x,
          main = (): Int -> getX(newPoint(5))
        }
        """);
    int sum = invokeName(clazz, "main", null);

    assertThat(sum).isEqualTo(5);
  }

  @Test
  void typeAliasStructs() throws Exception {
    var clazz = compileClass( """
        Main {
          type Point = { x: Int, y: Int },
          newPoint = (x: Int): Point -> { x = x, y = 2 },
          getX = (point: Point): Int -> point.x,
          main = (): Int -> getX(newPoint(5))
        }
        """);
    int sum = invokeName(clazz, "main", null);

    assertThat(sum).isEqualTo(5);
  }

  @Test
  void typeAliasFunction() throws Exception {
    var clazz = compileClass( """
        Main {
          type MathFunc = (x: Int, y: Int) -> Int,
          getX = (x: Int, func: MathFunc): Int -> func(x, 6),
          main = (): Int -> getX(3, (x: Int, y: Int): Int -> x + y)
        }
        """);
    int sum = invokeName(clazz, "main", null);

    assertThat(sum).isEqualTo(9);
  }

  // Add test for local named type where the type captureName doesn't exist

  @Test
  void typeAliasStruct_importModule() throws Exception {
    var clazz = compileClass( """
        Point {
          type T = { x: Int, y: Int },
          
          new = (x: Int): T -> { x = x, y = 4 }
        }
                
        Main {
          use main/Point,
          
          getX = (point: Point.T): Int -> point.x,
          main = (): Int -> {
            getX(Point.new(5))
          }
        }
        """);
    int sum = invokeName(clazz, "main", null);

    assertThat(sum).isEqualTo(5);
  }

  @Test
  void typeCheckForeignParamType() throws Exception {
    var clazz = compileClass( """
        Main {
          use foreign java/util/ArrayList,
          use foreign java/util/Iterator,
          
          stringFn = (str: String): Int -> 5,
          main = (): { value: Int } -> {
            let list = ArrayList#new()
            ArrayList#add(list, "foo")
            let iter = ArrayList#iterator(list)
            
            let n = iter |> Iterator#next() |> stringFn()
            { value = n }
          },
        }
        """);
    Object boxedInt = invokeName(clazz, "main", null);

    assertThat(boxedInt).hasFieldOrPropertyWithValue("value", 5);
  }

  @Test
  void complexParameterizedExpression() throws Exception {
    var clazz = compileClass( """
        Main {
          map = [T, U](value: T, mapper: { map: (inp: T) -> U }): { value: U } ->
              { value = mapper.map(value) },
          main = (): { value: Int } -> map("foobar", { map = (inp: String): Int -> 10 })
        }
        """);
    Object boxedInt = invokeName(clazz, "main", null);

    assertThat(boxedInt).hasFieldOrPropertyWithValue("value", 10);
  }

  @Test
  void complexParamaterizedExpression_multiModule() throws Exception {
    var clazz = compileClass( """
        Box {
          type T[A] = { value: A },
          
          new = [A](value: A): T[A] -> { value = value },
          unbox = [A](box: T[A]): A -> box.value,
        }
                
        Main {
          use main/Box,
                
          main = (): Int -> {
            let box = Box.new(10)
            Box.unbox(box)
          }
        }
        """);
    int boxedInt = invokeName(clazz, "main", null);

    assertThat(boxedInt).isEqualTo(10);
  }

  @Test
  void complexParameterizedExpression_sameTypeVarName() throws Exception {
    var clazz = compileClass( """
        Main {
          type Box[A] = { value: A },
                
          new = [B](value: B): Box[B] -> { value = value },
          main = (): Box[Int] -> {
            let boxInt = new(10)
            let boxStr = new("hello")
            boxInt
          }
        }
        """);
    Object boxedInt = invokeName(clazz, "main", null);

    assertThat(boxedInt).hasFieldOrPropertyWithValue("value", 10);
  }

  @Test
  void complexParameterizedExpression_sameTypeVarNamee() throws Exception {
    var clazz = compileClass( """
        Main {
          type Box[A] = { value: A },
                
          new = [B](value: B): Box[B] -> { value = value },
          map = [A, B](box: Box[A], mapper: (value: A) -> B): Box[B] -> { value = mapper(box.value) },
          main = (): String -> {
            let box = new(10) |> map((n: Int): String -> "hi")
            box.value
          }
        }
        """);
    String str = invokeName(clazz, "main", null);

    assertThat(str).isEqualTo("hi");
  }

  @Test
  void complexParameterizedExpression_sameAliasParamName() throws Exception {
    var clazz = compileClass( """
        Main {
          type Box[A] = { value: A },
          type BoxTwo[A, B] = { one: A, two: Box[B] },
                
          new = [A, B](one: A, two: B): BoxTwo[A, B] -> {
            one = one,
            two = { value = two },
          },
          main = (): Box[String] -> {
            let box = new(10, "hello")
            box.two
          }
        }
        """);
    Object boxedStr = invokeName(clazz, "main", null);

    assertThat(boxedStr).hasFieldOrPropertyWithValue("value", "hello");
  }

  @Test
  void complexParameterizedExpression_withAlias() throws Exception {
    var clazz = compileClass( """
        Main {
          type T[A] = { value: A },
          type Mapper[A, B] = { map: (inp: A) -> B },
                
          map = [A, B](value: A, mapper: Mapper[A, B]): T[B] ->
              { value = mapper.map(value) },
          main = (): T[Int] -> map("foo", { map = (inp: String): Int -> 10 })
        }
        """);
    Object boxedInt = invokeName(clazz, "main", null);

    assertThat(boxedInt).hasFieldOrPropertyWithValue("value", 10);
  }

  private Class<?> compileClass(String source) throws ClassNotFoundException, CompilationException {
    return compileClass(Source.fromString("main", source), false);
  }

  private Class<?> compileClassDebug(String source) throws ClassNotFoundException, CompilationException {
    return compileClass(Source.fromString("main", source), true);
  }

  private Class<?> compileClass(Source source) throws ClassNotFoundException, CompilationException {
    return compileClass(source, false);
  }

  private Class<?> compileClassDebug(Source source)
      throws ClassNotFoundException, CompilationException {
    return compileClass(source, true);
  }

  private Class<?> compileClass(Source source, boolean dumpClasses)
      throws ClassNotFoundException, CompilationException {
    var bytecode = new Compiler().compile(source);
    var cl = new SasquachClassloader();
    if (dumpClasses) {
      var path = Path.of(Files.temporaryFolderPath(), testName);
      TestUtils.dumpGeneratedClasses(path, bytecode.generatedBytecode());
    }
    bytecode.generatedBytecode().forEach(cl::addClass);
    return cl.loadModule(new QualifiedModuleName("main", "Main"));
  }
}
