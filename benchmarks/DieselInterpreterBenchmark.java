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
package test;

import static com.github.tonivade.diesel.Combine.chain;
import static com.github.tonivade.diesel.Combine.pipe;

import com.github.tonivade.diesel.Program;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
@State(Scope.Thread)
public class DieselInterpreterBenchmark {

  @Param({"0", "1", "10", "100", "500", "1000"})
  public int depth;

  // thrown without a stack trace, so the benchmark measures the unwinding, not creating it
  private static final RuntimeException ERROR = new BenchmarkError();

  private static final class BenchmarkError extends RuntimeException {
    BenchmarkError() {
      super("error", null, false, false);
    }
  }

  private Program<Void, Integer, Integer> map;
  private Program<Void, Integer, Integer> flatMap;
  private Program<Void, Integer, Integer> mixedMapFlatMap;
  private Program<Void, Integer, Integer> foldMapSuccess;
  private Program<Void, Integer, Integer> foldMapFailure;
  private Program<Void, Integer, Integer> failureThroughFlatMap;
  private Program<Void, Integer, Integer> successThroughFlatMapError;
  private Program<Void, Integer, Integer> mapErrorFailure;
  private Program<Void, Integer, Integer> peek;
  private Program<Void, Integer, Integer> exceptionUnwinding;
  private Program<Void, Integer, Integer> finalizers;

  @Setup(Level.Trial)
  public void setup() {
    map = buildMap(depth);
    flatMap = buildFlatMap(depth);
    mixedMapFlatMap = buildMixedMapFlatMap(depth);
    foldMapSuccess = buildFoldMapSuccess(depth);
    foldMapFailure = buildFoldMapFailure(depth);
    failureThroughFlatMap = buildFailureThroughFlatMap(depth);
    successThroughFlatMapError = buildSuccessThroughFlatMapError(depth);
    mapErrorFailure = buildMapErrorFailure(depth);
    peek = buildPeek(depth);
    exceptionUnwinding = buildExceptionUnwinding(depth);
    finalizers = buildFinalizers(depth);
  }

  @Benchmark
  public Object map() {
    return map.eval();
  }

  @Benchmark
  public Object flatMap() {
    return flatMap.eval();
  }

  // map and flatMap alternated, as in real code: the interpreter sees both kinds of frames mixed,
  // unlike map and flatMap, which only see one
  @Benchmark
  public Object mixedMapFlatMap() {
    return mixedMapFlatMap.eval();
  }

  @Benchmark
  public Object foldMapSuccess() {
    return foldMapSuccess.eval();
  }

  @Benchmark
  public Object foldMapFailure() {
    return foldMapFailure.eval();
  }

  // a failure that goes through flatMaps that don't handle it
  @Benchmark
  public Object failureThroughFlatMap() {
    return failureThroughFlatMap.eval();
  }

  // a success that goes through flatMapErrors that don't handle it, like recover over programs that
  // don't fail
  @Benchmark
  public Object successThroughFlatMapError() {
    return successThroughFlatMapError.eval();
  }

  @Benchmark
  public Object mapErrorFailure() {
    return mapErrorFailure.eval();
  }

  @Benchmark
  public Object peek() {
    return peek.eval();
  }

  // an exception raised under a chain of flatMaps and caught on top
  @Benchmark
  public Object exceptionUnwinding() {
    return exceptionUnwinding.eval();
  }

  @Benchmark
  public Object finalizers() {
    return finalizers.eval();
  }

  private static Program<Void, Integer, Integer> buildFailureThroughFlatMap(int depth) {
    Program<Void, Integer, Integer> program = Program.failure(0);

    for (int i = 0; i < depth; i++) {
      program = program.flatMap(value -> Program.<Void, Integer, Integer>success(value + 1));
    }

    return program;
  }

  private static Program<Void, Integer, Integer> buildSuccessThroughFlatMapError(int depth) {
    Program<Void, Integer, Integer> program = Program.success(0);

    for (int i = 0; i < depth; i++) {
      program = program.flatMapError(error -> Program.<Void, Integer, Integer>failure(error + 1));
    }

    return program;
  }

  private static Program<Void, Integer, Integer> buildMapErrorFailure(int depth) {
    Program<Void, Integer, Integer> program = Program.failure(0);

    for (int i = 0; i < depth; i++) {
      program = program.mapError(error -> error + 1);
    }

    return program;
  }

  private static Program<Void, Integer, Integer> buildPeek(int depth) {
    Program<Void, Integer, Integer> program = Program.success(0);

    for (int i = 0; i < depth; i++) {
      program = program.peek(_ -> {});
    }

    return program;
  }

  private static Program<Void, Integer, Integer> buildExceptionUnwinding(int depth) {
    Program<Void, Integer, Integer> program = Program.raise(() -> ERROR);

    for (int i = 0; i < depth; i++) {
      program = program.flatMap(value -> Program.<Void, Integer, Integer>success(value + 1));
    }

    return program.catchAll(_ -> Program.success(-1));
  }

  private static Program<Void, Integer, Integer> buildFinalizers(int depth) {
    Program<Void, Integer, Integer> program = Program.success(0);

    for (int i = 0; i < depth; i++) {
      program = program.ensuring(Program.<Void, Void>unit());
    }

    return program;
  }

  private static Program<Void, Integer, Integer> buildMap(int depth) {
    Program<Void, Integer, Integer> program = Program.success(0);

    for (int i = 0; i < depth; i++) {
      program = chain(program, value -> value + 1);
    }

    return program;
  }

  private static Program<Void, Integer, Integer> buildFlatMap(int depth) {
    Program<Void, Integer, Integer> program = Program.success(0);

    for (int i = 0; i < depth; i++) {
      program = pipe(program,
          value -> Program.<Void, Integer, Integer>success(value + 1));
    }

    return program;
  }

  private static Program<Void, Integer, Integer> buildMixedMapFlatMap(int depth) {
    Program<Void, Integer, Integer> program = Program.success(0);

    for (int i = 0; i < depth; i++) {
      program = i % 2 == 0
          ? chain(program, value -> value + 1)
          : pipe(program, value -> Program.<Void, Integer, Integer>success(value + 1));
    }

    return program;
  }

  private static Program<Void, Integer, Integer> buildFoldMapSuccess(int depth) {
    Program<Void, Integer, Integer> program = Program.success(0);

    for (int i = 0; i < depth; i++) {
      program = program.foldMap(
          error -> Program.<Void, Integer, Integer>failure(error),
          value -> Program.<Void, Integer, Integer>success(value + 1));
    }

    return program;
  }

  private static Program<Void, Integer, Integer> buildFoldMapFailure(int depth) {
    Program<Void, Integer, Integer> program = Program.failure(0);

    for (int i = 0; i < depth; i++) {
      program = program.foldMap(
          error -> Program.<Void, Integer, Integer>failure(error + 1),
          value -> Program.<Void, Integer, Integer>success(value + 1));
    }

    return program;
  }

  public static void main(String[] args) throws Exception {
    var builder = new OptionsBuilder()
        .include(DieselInterpreterBenchmark.class.getSimpleName())
        .addProfiler("gc");
    // to investigate, not for comparing runs: jbang run -Djfr=true ...
    if (Boolean.getBoolean("jfr")) {
      builder.addProfiler("jfr");
    }

    new Runner(builder.build()).run();
  }
}
