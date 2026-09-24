///usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS org.openjdk.jmh:jmh-core:1.37
//DEPS org.openjdk.jmh:jmh-generator-annprocess:1.37
//DEPS com.github.tonivade:diesel:0.10

//JAVA 27+

//JAVAC_OPTIONS -processor org.openjdk.jmh.generators.BenchmarkProcessor

/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package test;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.*;
import org.openjdk.jmh.runner.options.*;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import com.github.tonivade.diesel.Program;
import com.github.tonivade.diesel.Result;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class DieselBenchmark {

  @Param({ "10", "20", "30" })
  public int n;

  @Benchmark
  public Integer iterative() {
    if (n < 2) {
      return 1;
    }
    int a = 1, b = 1;
    for (int i = 2; i <= n; i++) {
      int temp = a + b;
      a = b;
      b = temp;
    }
    return b;
  }

  // the memoized function is created on each invocation so the cache starts empty
  @Benchmark
  @SuppressWarnings("unchecked")
  public Result<Void, Integer> recursive() {
    Function<Integer, Program<Void, Void, Integer>>[] fib = new Function[1];
    fib[0] = Program.memoize(k -> {
      if (k < 2) {
        return Program.success(1);
      }
      var fib2 = Program.suspend(() -> fib[0].apply(k - 2));
      var fib1 = Program.suspend(() -> fib[0].apply(k - 1));
      return Program.zip(fib2, fib1, Integer::sum);
    });
    return fib[0].apply(n).eval(null);
  }

  public static void main(String[] args) throws Exception {
    Options opt = new OptionsBuilder()
        .include(DieselBenchmark.class.getSimpleName())
        .addProfiler("gc")
        .build();

    new Runner(opt).run();
  }
}
