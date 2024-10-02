package com.pentlander.sasquach.e2e;

import static com.pentlander.sasquach.TestUtils.invokeMain;
import static com.pentlander.sasquach.TestUtils.invokeName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.pentlander.sasquach.BaseTest;
import com.pentlander.sasquach.CompilationException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class EndToEndTest extends BaseTest {
  @Test
  void missingParamType() throws Exception {
    // Figure out why the local named type isn't being replaced
    var ex = assertThrows(CompilationException.class, () ->
        compile("""
            Main {
              missingTypes = (str): String -> "foo",
              
              main = (): String -> missingTypes("something"),
            }
            """));
    assertThat(ex).hasMessageContaining("Type annotation required for function parameter");
  }

  @Test
  void missingReturnType() throws Exception {
    // Figure out why the local named type isn't being replaced
    var ex = assertThrows(CompilationException.class, () ->
        compile("""
            Main {
              missingTypes = (str: String) -> "foo",
              
              main = (): String -> missingTypes("something"),
            }
            """));
    assertThat(ex).hasMessageContaining("Type annotation required for function return");
  }

  @Test
  void tuple() throws Exception {
    var clazz = compile( """
        Main {
          main = (): Int -> {
            let tuple = ("string", 5)
            tuple._1
          },
        }
        """);
    // Figure out why the local named type isn't being replaced
    int sum = invokeMain(clazz);

    assertThat(sum).isEqualTo(5);
  }

  @Test
  void tuple_generic() throws Exception {
    var clazz = compile( """
        Main {
          tuplify = [A, B](a: A, b: B): (A, B) -> (a, b),
          main = (): Int -> {
            let tuple = tuplify(5, "something")
            tuple._0
          },
        }
        """);
    // Figure out why the local named type isn't being replaced
    int sum = invokeMain(clazz);

    assertThat(sum).isEqualTo(5);
  }

  @Test
  void loopRecur() throws Exception {
    var clazz = compile("""
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
    var clazz = compile( """
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
  void higherOrderFunc_noTypeSig() throws Exception {
    var clazz = compile( """
        Main {
          plus = (): Int -> {
            let add = (a, b) -> a + b
            add(1, 4)
          }
        }
        """);
    int sum = invokeName(clazz, "plus");

    assertThat(sum).isEqualTo(5);
  }

  @Test
  void higherOrderFunc_withCapture() throws Exception {
    var clazz = compile( """
        Main {
          plus = (): Int -> {
            let capture = 5
            let addFive = (a: Int): Int -> a + capture
            addFive(1)
          }
        }
        """);
    int sum = invokeName(clazz, "plus");

    assertThat(sum).isEqualTo(6);
  }

  @Test
  void higherOrderFunc_withNestedCapture() throws Exception {
    var clazz = compile( """
        Main {
          plus = (): Int -> {
            let innerCapture = 5
            let outerCapture = 7
            let addVars = (a: Int, b: Int): Int -> {
              let addCapture = (c: Int): Int -> c + innerCapture
              a + outerCapture + addCapture(b)
            }
            addVars(1, 2)
          }
        }
        """);
    int sum = invokeName(clazz, "plus");

    assertThat(sum).isEqualTo(15);
  }

  @Test
  void literalStructFunc_withCapture() throws Exception {
    /*
     * So the TFunction properly has a capture associated with it, which means the func gets created
     * with a type sig that includes the capture var. However, the methodhandle with the capture
     * never gets created. So in reality, the static func needs to be created on the struct and then
     * a field need to be initialized with the captured method handle. This leaves us with three
     * options:
     * 1. Take the capture var in the constructor and add a synthetic field for the captured variable.
     * Then generate the function normally without captures in the parameters
     * 2. Take the capture var in the constructor and init a field of type Func from a static method
     * within the struct and the capture var.
     * 3. Initialize the Func object before constructor and then take in that Func object in the
     * constructor.
     *
     *
     */
    var clazz = compile( """
        Main {
          main = (): Int -> {
            let capture = 5
            let adder = { addFive = (a: Int): Int -> a + capture, }
            adder.addFive(1)
          }
        }
        """);
    int sum = invokeMain(clazz);

    assertThat(sum).isEqualTo(6);
  }

  @Test
  void higherOrderFunc_inStruct() throws Exception {
    var clazz = compile("""
        Main {
          wrapFn = (fn: (s: String) -> String): { fn: (s: String) -> String } -> {
            fn = fn,
          },
          
          main = (): String -> {
            let wrappedFn = wrapFn((s: String): String -> "hello")
            wrappedFn.fn("foo")
          }
        }
        """);
    String sum = invokeMain(clazz);

    assertThat(sum).isEqualTo("hello");
  }

  @Test
  void applyOperator() throws Exception {
    var clazz = compile( """
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
    var clazz = compile("""
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
  void matchSumType_otherModule() throws Exception {
    var clazz = compile("""
        Option {
          type Option[T] = | Some(T) | None,
        }
        
        Main {
          use main/Option,
        
          foo = (): String -> {
            let option = if (true) Option.Some("foo") else Option.None
            match option {
              Option.Some(str) -> str,
              Option.None -> "",
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
    var clazz = compile("""
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
    var clazz = compile("""
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

  @Test
  void matchSumType_structVariantDifferentModule() throws Exception {
    var clazz = compile("""
        Option {
          type T[A] = | Some { inner: A } | None,
        }
        
        Main {
          use main/Option,
          
          foo = (): String -> {
            let option = if (true) Option.Some { inner = "foo" } else Option.None
            match option {
              Option.Some { inner } -> inner,
              Option.None -> "",
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
    var ex = assertThrows(CompilationException.class, () -> compile("""
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
        """));
    assertThat(ex).hasMessageContaining("should be 'String'");
  }

  @Test
  void sumTypeSingleton() throws Exception {
    var clazz = compile( """
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
  void sumType_withFunc() throws Exception {
    var clazz = compile( """
        Main {
          type Option = | Test { foo: (bar: Int) -> String },
          
          
          main = (): String -> {
            let some = Test { foo = (bar: Int): String -> "fox" }
            match some {
              Test { foo } -> foo(10),
            }
          },
        }
        """);
    String sum = invokeMain(clazz);

    assertThat(sum).isEqualTo("fox");
  }

  @Test
  void sumType_withFuncCapture() throws Exception {
    var clazz = compile( """
        Main {
          type Test = | Foo { foo: (bar: Int) -> String },
          
          
          main = (): String -> {
            let capture = "fox"
            let some = Foo { foo = (bar: Int): String -> capture }
            match some {
              Foo { foo } -> foo(10),
            }
          },
        }
        """);
    String sum = invokeMain(clazz);

    assertThat(sum).isEqualTo("fox");
  }

  @Test @Disabled
  void sumType_withFunc_referToField() throws Exception {
    var clazz = compile( """
        Main {
          type Test = | Foo { foo: (bar: Int) -> String, bar: String },
          
          
          main = (): String -> {
            let some = Foo { foo = (): String -> bar, bar = "fox" }
            match some {
              Foo { foo } -> foo(),
            }
          },
        }
        """);
    String sum = invokeMain(clazz);

    assertThat(sum).isEqualTo("fox");
  }

  @Test
  void namedStruct() throws Exception {
    var clazz = compile("""
        Main {
          type IntBox = { i: Int },
        
          main = (): IntBox -> IntBox { i = 5 }
        }
        """);
    Object baz = invokeMain(clazz);

    assertThat(baz).hasFieldOrPropertyWithValue("i", 5);
  }

  @Test
  void spread() throws Exception {
    var clazz = compile("""
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
  void interfaceType() throws Exception {
    var clazz = compile("""
        Main {
          type Foo[A] = { foo: A, .. },
          
          newFoo = [A](foo: A): Foo[A] -> {
            foo = foo
          },
          
          main = (): Foo[String] -> {
            newFoo("hello")
          }
        }
        """);

    Object struct = invokeName(clazz, "main");

    assertThat(struct).hasFieldOrPropertyWithValue("foo", "hello");
  }

  @Test
  void genericRow() throws Exception {
    var clazz = compile("""
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

  @Test @Disabled
  void genericRow_combine() throws Exception {
    // Not sure how this ever worked since a StructType can only have 1 row var right now
    var clazz = compile("""
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
    var clazz = compile( """
        Main {
          use foreign java/io/PrintStream,
          use foreign java/lang/System,
          
          foo = (): Void -> {
            PrintStream#println(System#out, "test")
          }
        }
        """);
    invokeName(clazz, "foo");
  }

  @Test
  void foreignFunctionCall_mapGet() throws Exception {
    var clazz = compile( """
        Main {
          use foreign java/util/HashMap,
          
          foo = (): String -> {
            let map = HashMap#new()
            HashMap#put(map, "foo", "bar")
            HashMap#get(map, "foo")
          }
        }
        """);
    String value = invokeName(clazz, "foo");

    assertThat(value).isEqualTo("bar");
  }

  @Test
  void typeAliasStruct() throws Exception {
    var clazz = compile( """
        Main {
          type Point = { x: Int, y: Int },
          newPoint = (x: Int): Point -> Point { x = x, y = 4 },
          getX = (point: Point): Int -> point.x,
          main = (): Int -> getX(newPoint(5))
        }
        """);
    int sum = invokeName(clazz, "main");

    assertThat(sum).isEqualTo(5);
  }

  @Test
  void typeAliasStructs() throws Exception {
    var clazz = compile("""
        Main {
          type Point = { x: Int, y: Int },
          newPoint = (x: Int): Point -> Point { x = x, y = 2 },
          getX = (point: Point): Int -> point.x,
          main = (): Int -> getX(newPoint(5))
        }
        """);
    int sum = invokeName(clazz, "main");

    assertThat(sum).isEqualTo(5);
  }

  @Test
  void typeAliasFunction() throws Exception {
    var clazz = compile( """
        Main {
          typealias MathFunc = (x: Int, y: Int) -> Int,
          getX = (x: Int, func: MathFunc): Int -> func(x, 6),
          main = (): Int -> getX(3, (x: Int, y: Int): Int -> x + y)
        }
        """);
    int sum = invokeName(clazz, "main");

    assertThat(sum).isEqualTo(9);
  }

  // Add test for local named type where the type captureName doesn't exist

  @Test
  void typeAliasStruct_importModule() throws Exception {
    var clazz = compile( """
        Point {
          type T = { x: Int, y: Int },
          
          new = (x: Int): T -> T { x = x, y = 4 }
        }
                
        Main {
          use main/Point,
          
          getX = (point: Point.T): Int -> point.x,
          main = (): Int -> {
            getX(Point.new(5))
          }
        }
        """);
    int sum = invokeName(clazz, "main");

    assertThat(sum).isEqualTo(5);
  }

  @Test
  void typeCheckForeignParamType() throws Exception {
    var clazz = compile( """
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
    Object boxedInt = invokeName(clazz, "main");

    assertThat(boxedInt).hasFieldOrPropertyWithValue("value", 5);
  }

  @Test
  void complexParameterizedExpression() throws Exception {
    var clazz = compile( """
        Main {
          map = [T, U](value: T, mapper: { map: (inp: T) -> U }): { value: U } ->
              { value = mapper.map(value) },
          main = (): { value: Int } -> map("foobar", { map = (inp: String): Int -> 10 })
        }
        """);
    Object boxedInt = invokeName(clazz, "main");

    assertThat(boxedInt).hasFieldOrPropertyWithValue("value", 10);
  }

  @Test
  void complexParameterizedExpression_multiModule() throws Exception {
    var clazz = compile( """
        Box {
          type T[A] = { value: A },
          
          new = [A](value: A): T[A] -> T { value = value },
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
    int boxedInt = invokeName(clazz, "main");

    assertThat(boxedInt).isEqualTo(10);
  }

  @Test
  void complexParameterizedExpression_sameTypeVarName() throws Exception {
    var clazz = compile( """
        Main {
          type Box[A] = { value: A },
                
          new = [B](value: B): Box[B] -> Box { value = value },
          main = (): Box[Int] -> {
            let boxInt = new(10)
            let boxStr = new("hello")
            boxInt
          }
        }
        """);
    Object boxedInt = invokeName(clazz, "main");

    assertThat(boxedInt).hasFieldOrPropertyWithValue("value", 10);
  }

  @Test
  void complexParameterizedExpression_sameTypeVarNamee() throws Exception {
    var clazz = compile( """
        Main {
          type Box[A] = { value: A },
                
          new = [B](value: B): Box[B] -> Box { value = value },
          map = [A, B](box: Box[A], mapper: (value: A) -> B): Box[B] -> Box { value = mapper(box.value) },
          main = (): String -> {
            let box = new(10) |> map((n: Int): String -> "hi")
            box.value
          }
        }
        """);
    String str = invokeName(clazz, "main");

    assertThat(str).isEqualTo("hi");
  }

  @Test
  void complexParameterizedExpression_sameAliasParamName() throws Exception {
    // BoxTwoConstr Main one:A two:B
    // 3: one:A
    // 4: two:B
    // 5: Box:B
    var clazz = compile( """
        Main {
          type Box[A] = { value: A },
          type BoxTwo[A, B] = { one: A, two: Box[B] },
                
          new = [A, B](one: A, two: B): BoxTwo[A, B] -> BoxTwo {
            one = one,
            two = Box { value = two },
          },
          main = (): Box[String] -> {
            let box = new(10, "hello")
            box.two
          }
        }
        """);
    Object boxedStr = invokeName(clazz, "main");

    assertThat(boxedStr).hasFieldOrPropertyWithValue("value", "hello");
  }

  @Test
  void complexParameterizedExpression_withAlias() throws Exception {
    var clazz = compile( """
        Main {
          type T[A] = { value: A },
          type Mapper[A, B] = { map: (inp: A) -> B },
                
          map = [A, B](value: A, mapper: Mapper[A, B]): T[B] ->
              T { value = mapper.map(value) },
          main = (): T[Int] -> map("foo", Mapper { map = (inp: String): Int -> 10 })
        }
        """);
    Object boxedInt = invokeName(clazz, "main");

    assertThat(boxedInt).hasFieldOrPropertyWithValue("value", 10);
  }
}
