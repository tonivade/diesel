
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
// forking and running programs in parallel, compared with plain Java on virtual threads
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
public class DieselConcurrencyBenchmark {

  /*
   * Fiber benchmarks
   */

  @State(Scope.Thread)
  public static class FiberState {

    @Param({ "1000", "10000", "100000" })
    public int work;

    private Program<Void, Void, Long> program;
    private ExecutorService javaExecutor;

    @Setup(Level.Trial)
    public void setup() {
      program = Program.supply(() -> cpuWork(work));
      javaExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @TearDown(Level.Trial)
    public void tearDown() {
      javaExecutor.close();
    }
  }

  // the same as a fork and a join, with the virtual threads that diesel uses by default
  @Benchmark
  public long javaForkJoin(FiberState state) throws Exception {
    return state.javaExecutor.submit(() -> cpuWork(state.work)).get();
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

  private static long cpuWork(int iterations) {
    long value = 1;

    for (int i = 0; i < iterations; i++) {
      value = (value * 1664525L + 1013904223L) & 0x7fffffffL;
    }

    return value;
  }

  public static void main(String[] args) throws Exception {
    Options opt = new OptionsBuilder()
        .include(DieselConcurrencyBenchmark.class.getSimpleName())
        .addProfiler("gc")
        .build();

    new Runner(opt).run();
  }
}
