
///usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS org.openjdk.jmh:jmh-core:1.37
//DEPS org.openjdk.jmh:jmh-generator-annprocess:1.37
//DEPS com.github.tonivade:diesel:0.14

//JAVA 27+

//JAVAC_OPTIONS -processor org.openjdk.jmh.generators.BenchmarkProcessor

/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package test;

import static com.github.tonivade.diesel.Combine.chain;
import static com.github.tonivade.diesel.Combine.pipe;
import static com.github.tonivade.diesel.Concurrent.parZip;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.*;
import org.openjdk.jmh.runner.options.*;

import com.github.tonivade.diesel.Program;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
public class DieselPerformance {

  /*
   * Runtime benchmarks
   */

  @State(Scope.Thread)
  public static class RuntimeState {

    @Param({ "1000", "10000" })
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

    @Setup(Level.Trial)
    public void setup() {
      program = buildComposition(n);
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
    int value = 0;
    for (int i = 0; i < state.n; i++) {
      value++;
    }
    return value;
  }

  /*
   * Error handling benchmarks
   */

  @State(Scope.Thread)
  public static class ErrorState {

    private Program<Void, Integer, Integer> typedFailure;
    private Program<Void, Void, Integer> exceptionFailure;

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
  public int javaTypedFailureRecovery() {
    Integer error = 1;
    int value = 0;

    if (error != null) {
      return 42;
    }
    return value;
  }

  @Benchmark
  public int javaExceptionRecovery() {
    try {
      throw new IllegalStateException();
    } catch (IllegalStateException error) {
      return 42;
    }
  }

  /*
   * Fiber benchmarks
   */

  @State(Scope.Thread)
  public static class FiberState {

    @Param({ "1000", "10000", "100000" })
    public int work;

    private Program<Void, Void, Long> program;

    @Setup(Level.Trial)
    public void setup() {
      program = Program.supply(() -> cpuWork(work));
    }
  }

  @Benchmark
  public long dieselForkJoin(FiberState state) {
    return pipe(
        state.program.fork(),
        fiber -> fiber.<Void>join())
        .evalOrElseThrow();
  }

  /*
   * Parallel execution benchmarks
   */

  @State(Scope.Thread)
  public static class ParallelState {

    @Param({ "1000", "10000", "100000", "1000000" })
    public int work;

    private Program<Void, Void, Long> first;
    private Program<Void, Void, Long> second;
    private Program<Void, Void, Long> sequential;
    private Program<Void, Void, Long> parallel;

    private ExecutorService javaExecutor;

    @Setup(Level.Trial)
    public void setup() {
      first = Program.supply(() -> cpuWork(work));
      second = Program.supply(() -> cpuWork(work));

      sequential = pipe(
          first,
          left -> pipe(
              second,
              right -> Program.success(left + right)));

      parallel = parZip(
          first,
          second,
          Long::sum);

      javaExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @TearDown(Level.Trial)
    public void tearDown() throws InterruptedException {
      javaExecutor.close();
    }
  }

  @Benchmark
  public long javaSequential(ParallelState state) {
    long left = cpuWork(state.work);
    long right = cpuWork(state.work);
    return left + right;
  }

  @Benchmark
  public long javaParallel(ParallelState state) throws Exception {
    Future<Long> first = state.javaExecutor.submit(
        () -> cpuWork(state.work));
    Future<Long> second = state.javaExecutor.submit(
        () -> cpuWork(state.work));

    return first.get() + second.get();
  }

  @Benchmark
  public long dieselSequential(ParallelState state) {
    return state.sequential.evalOrElseThrow();
  }

  @Benchmark
  public long dieselParallel(ParallelState state) {
    return state.parallel.evalOrElseThrow();
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
        .include(DieselPerformance.class.getSimpleName())
        .addProfiler("gc")
        .build();

    new Runner(opt).run();
  }
}
