///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS org.openjdk.jmh:jmh-core:1.37
//DEPS org.openjdk.jmh:jmh-generator-annprocess:1.37
//DEPS com.github.tonivade:diesel:0.14
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
import org.openjdk.jmh.runner.options.Options;
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

  private Program<Void, Integer, Integer> pure;
  private Program<Void, Integer, Integer> map;
  private Program<Void, Integer, Integer> flatMap;
  private Program<Void, Integer, Integer> foldMapSuccess;
  private Program<Void, Integer, Integer> foldMapFailure;

  @Setup(Level.Trial)
  public void setup() {
    pure = Program.success(0);
    map = buildMap(depth);
    flatMap = buildFlatMap(depth);
    foldMapSuccess = buildFoldMapSuccess(depth);
    foldMapFailure = buildFoldMapFailure(depth);
  }

  @Benchmark
  public Object pure() {
    return pure.eval();
  }

  @Benchmark
  public Object map() {
    return map.eval();
  }

  @Benchmark
  public Object flatMap() {
    return flatMap.eval();
  }

  @Benchmark
  public Object foldMapSuccess() {
    return foldMapSuccess.eval();
  }

  @Benchmark
  public Object foldMapFailure() {
    return foldMapFailure.eval();
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
    Options opt = new OptionsBuilder()
        .include(DieselInterpreterBenchmark.class.getSimpleName())
        .addProfiler("gc")
        .addProfiler("jfr")
        .build();

    new Runner(opt).run();
  }
}
