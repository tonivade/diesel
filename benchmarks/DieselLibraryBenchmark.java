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
// the cost of the main operations of the library, each one used by a single thread, without
// contention: the fixed cost of their hot path
package test;

import static com.github.tonivade.diesel.Combine.pipe;

import com.github.tonivade.diesel.Combine;
import com.github.tonivade.diesel.Program;
import com.github.tonivade.diesel.Queue;
import com.github.tonivade.diesel.Ref;
import com.github.tonivade.diesel.Resource;
import com.github.tonivade.diesel.Semaphore;
import com.github.tonivade.diesel.Source;
import com.github.tonivade.diesel.Unit;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

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
public class DieselLibraryBenchmark {

  /*
   * Collections: traverse and Source, by number of elements
   */

  @State(Scope.Thread)
  public static class CollectionState {

    @Param({ "10", "100", "1000" })
    public int size;

    private Program<Void, Void, Collection<Integer>> traverse;
    private Program<Void, Void, List<Integer>> sourceMapToList;

    @Setup(Level.Trial)
    public void setup() {
      var values = IntStream.range(0, size).boxed().toList();
      // the result is collected in a persistent vector, one element at a time
      traverse = Combine.traverse(value -> Program.<Void, Void, Integer>success(value + 1), values);
      sourceMapToList = Source.<Void, Void, Integer>from(values).map(value -> value + 1).toList();
    }
  }

  @Benchmark
  public Object traverse(CollectionState state) {
    return state.traverse.eval();
  }

  @Benchmark
  public Object sourceMapToList(CollectionState state) {
    return state.sourceMapToList.eval();
  }

  /*
   * Primitives, used without contention
   */

  @State(Scope.Thread)
  public static class PrimitiveState {

    private Program<Void, Void, Unit> refUpdate;
    private Program<Void, Void, Integer> queueOfferTake;
    private Program<Void, Void, Integer> semaphoreWithPermit;

    @Setup(Level.Trial)
    public void setup() {
      var ref = Ref.of(0);
      refUpdate = ref.update(value -> value + 1);

      var queue = Queue.<Void, Void, Integer>makeUnbounded().evalOrElseThrow();
      queueOfferTake = pipe(queue.offer(1), _ -> queue.take());

      var semaphore = Semaphore.of(1);
      semaphoreWithPermit = semaphore.withPermit(Program.success(1));
    }
  }

  @Benchmark
  public Object refUpdate(PrimitiveState state) {
    return state.refUpdate.eval();
  }

  @Benchmark
  public Object queueOfferTake(PrimitiveState state) {
    return state.queueOfferTake.eval();
  }

  @Benchmark
  public Object semaphoreWithPermit(PrimitiveState state) {
    return state.semaphoreWithPermit.eval();
  }

  /*
   * Resources: acquiring, using and releasing
   */

  @State(Scope.Thread)
  public static class ResourceState {

    private Program<Void, Void, Integer> bracket;
    private Program<Void, Void, Integer> bracketCase;
    private Program<Void, Void, Integer> resource;
    private Program<Void, Void, Integer> composedResources;

    @Setup(Level.Trial)
    public void setup() {
      bracket = Combine.bracket(
          Program.<Void, Void, Integer>success(1),
          Program::success,
          _ -> Program.<Void, Void>unit());
      bracketCase = Combine.bracketCase(
          Program.<Void, Void, Integer>success(1),
          Program::success,
          (_, _) -> Program.<Void, Void>unit());
      resource = resource(1).use(Program::success);
      composedResources = resource(1)
          .flatMap(a -> resource(2).map(b -> a + b))
          .flatMap(ab -> resource(3).map(c -> ab + c))
          .use(Program::success);
    }

    private static Resource<Void, Void, Integer> resource(int value) {
      return Resource.make(Program.success(value), _ -> Program.<Void, Void>unit());
    }
  }

  @Benchmark
  public Object bracket(ResourceState state) {
    return state.bracket.eval();
  }

  @Benchmark
  public Object bracketCase(ResourceState state) {
    return state.bracketCase.eval();
  }

  @Benchmark
  public Object resource(ResourceState state) {
    return state.resource.eval();
  }

  // three resources composed: acquired in order and released in reverse order
  @Benchmark
  public Object composedResources(ResourceState state) {
    return state.composedResources.eval();
  }

  public static void main(String[] args) throws Exception {
    Options opt = new OptionsBuilder()
        .include(DieselLibraryBenchmark.class.getSimpleName())
        .addProfiler("gc")
        .build();

    new Runner(opt).run();
  }
}
