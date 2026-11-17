
///usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS org.openjdk.jmh:jmh-core:1.37
//DEPS org.openjdk.jmh:jmh-generator-annprocess:1.37
//DEPS com.github.tonivade:diesel:${diesel.version:0.14}
//REPOS mavenlocal,mavencentral

// Runs against the diesel version given with -Ddiesel.version, the last release by default. To
// measure the working copy: ./gradlew publishToMavenLocal, and then
//   jbang run -Ddiesel.version=0.15-SNAPSHOT benchmarks/<Benchmark>.java

//JAVA 25+

//JAVAC_OPTIONS -processor org.openjdk.jmh.generators.BenchmarkProcessor

/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
// the cost of diesel compared with the same work in plain Java
package test;

import static com.github.tonivade.diesel.Combine.chain;
import static com.github.tonivade.diesel.Combine.pipe;
import static com.github.tonivade.diesel.Concurrent.parZip;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.*;
import org.openjdk.jmh.runner.options.*;

import com.github.tonivade.diesel.Program;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
public class DieselOverheadBenchmark {

  /*
   * Runtime benchmarks
   */

  @State(Scope.Thread)
  public static class RuntimeState {

    // a small amount of work shows the fixed cost of evaluating a program
    @Param({ "10", "1000", "10000" })
    public int work;

    private Program<Void, Void, Long> program;

    @Setup(Level.Trial)
    public void setup() {
      program = Program.supply(() -> cpuWork(work));
    }
  }

  @Benchmark
  public long javaDirect(RuntimeState state) {
    return cpuWork(state.work);
  }

  @Benchmark
  public long programSupply(RuntimeState state) {
    return state.program.evalOrElseThrow();
  }

  /*
   * Composition benchmarks
   */

  @State(Scope.Thread)
  public static class CompositionState {

    @Param({ "1", "10", "100", "500", "1000" })
    public int n;

    private Program<Void, Void, Integer> program;
    // the same functions composed directly, so the JIT can't fold the loop into a constant
    private Function<Integer, Integer> composed;

    @Setup(Level.Trial)
    public void setup() {
      program = buildComposition(n);
      composed = Function.identity();
      for (int i = 0; i < n; i++) {
        composed = composed.andThen(value -> value + 1);
      }
    }
  }

  @Benchmark
  public Program<Void, Void, Integer> compositionConstruction(
      CompositionState state) {
    return buildComposition(state.n);
  }

  @Benchmark
  public int compositionEvaluation(CompositionState state) {
    return state.program.evalOrElseThrow();
  }

  @Benchmark
  public int compositionConstructionAndEvaluation(
      CompositionState state) {
    return buildComposition(state.n).evalOrElseThrow();
  }

  @Benchmark
  public int javaCompositionEvaluation(CompositionState state) {
    return state.composed.apply(0);
  }

  /*
   * Error handling benchmarks
   */

  @State(Scope.Thread)
  public static class ErrorState {

    private Program<Void, Integer, Integer> typedFailure;
    private Program<Void, Void, Integer> exceptionFailure;

    // read from the state, so the JIT can't turn the baselines into constants
    private Integer error = 1;
    private int value = 0;
    private Supplier<IllegalStateException> exception = IllegalStateException::new;

    @Setup(Level.Trial)
    public void setup() {
      typedFailure = Program.<Void, Integer, Integer>failure(1)
          .foldMap(
              error -> Program.success(42),
              value -> Program.success(value));

      exceptionFailure = Program.<Void, Void, Integer>raise(
          IllegalStateException::new)
          .catchAll(error -> Program.success(42));
    }
  }

  @Benchmark
  public int typedFailureRecovery(ErrorState state) {
    return state.typedFailure.evalOrElseThrow();
  }

  @Benchmark
  public int exceptionRecovery(ErrorState state) {
    return state.exceptionFailure.evalOrElseThrow();
  }

  @Benchmark
  public int javaTypedFailureRecovery(ErrorState state) {
    if (state.error != null) {
      return 42;
    }
    return state.value;
  }

  // the exception is created like in the diesel version, with its stack trace
  @Benchmark
  public int javaExceptionRecovery(ErrorState state) {
    try {
      throw state.exception.get();
    } catch (IllegalStateException error) {
      return 42;
    }
  }

  /*
   * Helpers
   */

  private static Program<Void, Void, Integer> buildComposition(int size) {
    Program<Void, Void, Integer> program = Program.success(0);

    for (int i = 0; i < size; i++) {
      program = chain(program, value -> value + 1);
    }

    return program;
  }

  private static long cpuWork(int iterations) {
    long value = 1;

    for (int i = 0; i < iterations; i++) {
      value = (value * 1664525L + 1013904223L) & 0x7fffffffL;
    }

    return value;
  }

  public static void main(String[] args) throws Exception {
    Options opt = new OptionsBuilder()
        .include(DieselOverheadBenchmark.class.getSimpleName())
        .addProfiler("gc")
        .build();

    new Runner(opt).run();
  }
}
