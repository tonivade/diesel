/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Program.bracket;
import static com.github.tonivade.diesel.Program.failure;
import static com.github.tonivade.diesel.Program.success;
import static com.github.tonivade.diesel.Program.supply;
import static com.github.tonivade.diesel.Tuple2.tuple;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class SourceTest {

  @Nested
  class Concat {

    @Test
    void concatKeepsTheOrder() {
      var list = Source.of(1, 2).concat(Source.empty()).concat(Source.of(3)).concat(Source.of(4, 5).concat(Source.of(6)));

      assertThat(list.toList().evalOrElseThrow()).isEqualTo(List.of(1, 2, 3, 4, 5, 6));
    }

    @Test
    void concatIsLazy() {
      var list = Source.<Void, Void, Integer>iterate(0, x -> x + 1).concat(Source.of(-1)).map(x -> x * 2).take(3);

      assertThat(list.toList().evalOrElseThrow()).isEqualTo(List.of(0, 2, 4));
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void concatInALoopIsLinear() {
      Source<Void, Void, Integer> list = Source.empty();
      for (int i = 0; i < 100_000; i++) {
        list = list.concat(Source.of(i));
      }

      var result = list.toList().evalOrElseThrow();

      assertThat(result).hasSize(100_000).startsWith(0, 1, 2).endsWith(99_998, 99_999);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void appendInALoopIsLinear() {
      Source<Void, Void, Integer> list = Source.empty();
      for (int i = 0; i < 100_000; i++) {
        list = list.append(success(i));
      }

      var result = list.toList().evalOrElseThrow();

      assertThat(result).hasSize(100_000).startsWith(0, 1, 2).endsWith(99_998, 99_999);
    }

    @Test
    void concatWithAnEmptySourceReturnsTheSameSource() {
      var nonEmpty = Source.<Void, Void, Integer>nonEmpty(success(1), Source.empty());
      var suspended = Source.<Void, Void, Integer>of(1, 2);
      var concatenated = nonEmpty.concat(Source.<Void, Void, Integer>of(2));

      assertThat(nonEmpty.concat(Source.empty())).isSameAs(nonEmpty);
      assertThat(suspended.concat(Source.empty())).isSameAs(suspended);
      assertThat(concatenated.concat(Source.empty())).isSameAs(concatenated);
    }

    @Test
    void concatenatingEmptySourcesInALoopDoesNotAddLayers() {
      var source = Source.<Void, Void, Integer>nonEmpty(success(1), Source.empty());
      var list = source;
      for (int i = 0; i < 100_000; i++) {
        list = list.concat(Source.empty());
      }

      assertThat(list).isSameAs(source);
      assertThat(list.toList().evalOrElseThrow()).isEqualTo(List.of(1));
    }

    @Test
    void concatWithTheOtherSourceEmptyKeepsTheOrder() {
      var list = Source.<Void, Void, Integer>of(1, 2)
          .concat(Source.empty())
          .concat(Source.of(3))
          .concat(Source.empty());

      assertThat(list.toList().evalOrElseThrow()).isEqualTo(List.of(1, 2, 3));
    }

    @Test
    // in a separate thread: a regression makes the traversal very slow, and it doesn't stop when
    // interrupted, so it would hang the build instead of failing
    @Timeout(value = 10, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void aLongChainOverAConcatIsLinear() {
      Source<Void, Void, Integer> source = Source.<Void, Void, Integer>of(0)
          .concat(Source.iterate(1, x -> x + 1));
      for (int i = 0; i < 10_000; i++) {
        source = source.map(x -> x + 1);
      }

      var result = source.take(3).toList();

      assertThat(result.evalOrElseThrow()).isEqualTo(List.of(10_000, 10_001, 10_002));
    }
  }

  @Nested
  class Prepend {

    @Test
    void prependKeepsTheOrder() {
      var source = Source.<Void, Void, Integer>of(2, 3);

      assertThat(source.prepend(success(1)).toList().evalOrElseThrow()).isEqualTo(List.of(1, 2, 3));
      assertThat(Source.<Void, Void, Integer>empty().prepend(success(1)).toList().evalOrElseThrow())
        .isEqualTo(List.of(1));
      assertThat(source.concat(Source.of(4)).prepend(success(1)).toList().evalOrElseThrow())
        .isEqualTo(List.of(1, 2, 3, 4));
    }

    @Test
    void prependDoesNotEvaluateTheSource() {
      var calls = new AtomicInteger();
      var source = Source.<Void, Void, Integer>iterate(calls::incrementAndGet);

      var result = source.prepend(success(0)).take(1).toList();

      assertThat(result.evalOrElseThrow()).isEqualTo(List.of(0));
      assertThat(calls).hasValue(0);
    }
  }

  @Nested
  class Take {

    @Test
    void takeOnlyEvaluatesTheElementsItTakes() {
      var calls = new AtomicInteger();
      var source = Source.<Void, Void, Integer>iterate(calls::incrementAndGet);

      var result = source.take(2).concat(source.take(3)).toList();

      assertThat(result.evalOrElseThrow()).isEqualTo(List.of(1, 2, 3, 4, 5));
      assertThat(calls).hasValue(5);
    }

    @Test
    // in a separate thread: the search of the filter doesn't stop when interrupted, so a regression
    // would hang the build instead of failing
    @Timeout(value = 10, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void takeStopsAFilterWithNoMoreMatches() {
      var list = Source.<Void, Void, Integer>iterate(0, x -> x + 1).filter(x -> x < 2).take(2);

      assertThat(list.toList().evalOrElseThrow()).isEqualTo(List.of(0, 1));
    }

    @Test
    void takeWhileStopsAtTheFirstElementThatDoesNotMatch() {
      var source = Source.<Void, Void, Integer>of(1, 2, 3, 1).takeWhile(x -> x < 3);

      assertThat(source.toList().evalOrElseThrow()).isEqualTo(List.of(1, 2));
    }

    @Test
    void takeZeroDoesNotEvaluateAnyElement() {
      var calls = new AtomicInteger();
      var source = Source.<Void, Void, Integer>iterate(calls::incrementAndGet);

      var result = source.take(0).toList().evalOrElseThrow();

      assertThat(result).isEmpty();
      assertThat(calls).hasValue(0);
    }

    @Test
    void takeDoesNotEvaluateTheNextElement() {
      var calls = new AtomicInteger();
      var source = Source.<Void, Void, Integer>iterate(calls::incrementAndGet);

      var result = source.take(3).toList().evalOrElseThrow();

      assertThat(result).containsExactly(1, 2, 3);
      assertThat(calls).hasValue(3);
    }

    @Test
    void takeDoesNotEvaluateTheNextElementWithConcat() {
      var calls = new AtomicInteger();
      var source = Source.eval(Program.supply(() -> {
          calls.incrementAndGet();
          return 1;
        })).concat(Source.eval(Program.supply(() -> {
          calls.incrementAndGet();
          return 2;
        })));

      var result = source.take(0).toList().evalOrElseThrow();

      assertThat(result).isEmpty();
      assertThat(calls).hasValue(0);
    }

    @Test
    void takeTerminatesOnAnInfiniteSource() {
      var source = Source.iterate(1, x -> x + 1);

      var result = source.take(5).toList().evalOrElseThrow();

      assertThat(result).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void takePropagatesExceptionFromRequestedElement() {
      var source = Source.<Void, Void, Integer>empty()
          .append(Program.success(1))
          .append(Program.raise(() -> new IllegalStateException("boom")));

      var result = source.take(2).toList();

      assertThatThrownBy(() -> result.evalOrElseThrow())
        .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void takePropagatesFailureFromRequestedElement() {
      var source = Source.<Void, Exception, Integer>empty()
          .append(Program.success(1))
          .append(Program.failure(new IllegalStateException("boom")));

      var result = source.take(2).toList();

      assertThatThrownBy(() -> result.evalOrElseThrow())
        .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void takeOneDoesNotEvaluateFailureInNextElement() {
      var calls = new AtomicInteger();

      var source = Source.<Void, Void, Integer>empty()
          .append(Program.success(1))
          .append(Program.raise(() -> {
            calls.incrementAndGet();
            throw new IllegalStateException("boom");
          }));

      var result = source.take(1).toList().evalOrElseThrow();

      assertThat(result).containsExactly(1);
      assertThat(calls).hasValue(0);
    }
  }

  @Nested
  class Drop {

    @Test
    void dropEvaluatesTheDiscardedElements() {
      var executed = new AtomicInteger();
      var source = Source.<Void, Void, Integer>nonEmpty(
          supply(() -> {
            executed.incrementAndGet();
            return 1;
          }),
          Source.of(2, 3));

      var result = source.drop(1).toList();

      assertThat(result.evalOrElseThrow()).isEqualTo(List.of(2, 3));
      assertThat(executed).hasValue(1);
    }

    @Test
    void dropPropagatesTheFailureOfADiscardedElement() {
      var source = Source.<Void, String, Integer>nonEmpty(failure("boom"), Source.of(2));

      assertThat(source.drop(1).toList().eval())
        .isEqualTo(Result.failure("boom"));
    }

    @Test
    void dropMoreThanTheSizeIsEmpty() {
      assertThat(Source.<Void, Void, Integer>of(1, 2).drop(5).toList().evalOrElseThrow())
        .isEmpty();
      assertThat(Source.<Void, Void, Integer>of(1, 2).drop(0).toList().evalOrElseThrow())
        .isEqualTo(List.of(1, 2));
    }

    @Test
    void dropWhileStartsAtTheFirstElementThatDoesNotMatch() {
      var source = Source.<Void, Void, Integer>of(1, 2, 3, 1).dropWhile(x -> x < 3);

      assertThat(source.toList().evalOrElseThrow()).isEqualTo(List.of(3, 1));
    }
  }

  @Nested
  class Tail {

    @Test
    void tailEvaluatesTheDiscardedHead() {
      var executed = new AtomicInteger();
      var source = Source.<Void, Void, Integer>nonEmpty(
          supply(() -> {
            executed.incrementAndGet();
            return 1;
          }),
          Source.of(2, 3));

      var result = source.tail().toList();

      assertThat(result.evalOrElseThrow()).isEqualTo(List.of(2, 3));
      assertThat(executed).hasValue(1);
    }

    @Test
    void tailPropagatesTheFailureOfTheDiscardedHead() {
      var source = Source.<Void, String, Integer>nonEmpty(failure("boom"), Source.of(2));

      assertThat(source.tail().toList().eval())
        .isEqualTo(Result.failure("boom"));
    }

    @Test
    void tailEvaluatesTheDiscardedHeadThroughSuspendAndConcat() {
      var executed = new AtomicInteger();
      var head = Source.<Void, Void, Integer>nonEmpty(
          supply(() -> {
            executed.incrementAndGet();
            return 1;
          }),
          Source.of(2, 3));

      var suspended = Source.<Void, Void, Integer>suspend(() -> head).tail().toList();
      var concatenated = head.concat(Source.of(4)).tail().toList();

      assertThat(suspended.evalOrElseThrow()).isEqualTo(List.of(2, 3));
      assertThat(concatenated.evalOrElseThrow()).isEqualTo(List.of(2, 3, 4));
      assertThat(executed).hasValue(2);
    }

    @Test
    void tailOfAnEmptySourceIsEmpty() {
      assertThat(Source.<Void, Void, Integer>empty().tail().toList().evalOrElseThrow())
        .isEmpty();
    }
  }

  @Nested
  class HeadAndSplit {

    @Test
    void headOptionReturnsTheFirstElement() {
      assertThat(Source.<Void, Void, Integer>of(1, 2).headOption().evalOrElseThrow()).contains(1);
      assertThat(Source.<Void, Void, Integer>empty().headOption().evalOrElseThrow()).isEmpty();
    }

    @Test
    void splitDoesNotEvaluateTheHead() {
      var executed = new AtomicInteger();
      var source = Source.<Void, Void, Integer>nonEmpty(
          supply(() -> {
            executed.incrementAndGet();
            return 1;
          }),
          Source.of(2, 3));

      var split = source.split().evalOrElseThrow().orElseThrow();

      assertThat(executed).hasValue(0);
      assertThat(split.first().evalOrElseThrow()).isEqualTo(1);
      assertThat(split.second().toList().evalOrElseThrow()).isEqualTo(List.of(2, 3));
      assertThat(Source.<Void, Void, Integer>empty().split().evalOrElseThrow()).isEmpty();
    }
  }

  @Nested
  class Transformations {

    @Test
    // in a separate thread: a regression makes the traversal very slow, and it doesn't stop when
    // interrupted, so it would hang the build instead of failing
    @Timeout(value = 10, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void aLongChainOfOperationsIsLinear() {
      Source<Void, Void, Integer> source = Source.iterate(0, x -> x + 1);
      for (int i = 0; i < 10_000; i++) {
        source = source.map(x -> x + 1).filter(x -> x > 0).flatMap(Source::of);
      }

      var result = source.take(3).toList();

      assertThat(result.evalOrElseThrow()).isEqualTo(List.of(10_000, 10_001, 10_002));
    }

    @Test
    void mapEvalEvaluatesTheMappedPrograms() {
      var source = Source.<Void, Void, Integer>of(1, 2).mapEval(x -> success(x + 1));

      assertThat(source.toList().evalOrElseThrow()).isEqualTo(List.of(2, 3));
    }

    @Test
    void mapEvalPropagatesTheFailureOfAMappedProgram() {
      var source = Source.<Void, String, Integer>of(1, 2, 3)
          .mapEval(x -> x == 2 ? failure("boom") : success(x));

      assertThat(source.toList().eval()).isEqualTo(Result.failure("boom"));
    }

    @Test
    void flatMapConcatenatesTheMappedSources() {
      var source = Source.<Void, Void, Integer>of(1, 2).flatMap(x -> Source.of(x, x * 10));

      assertThat(source.toList().evalOrElseThrow()).isEqualTo(List.of(1, 10, 2, 20));
    }

    @Test
    void collectKeepsAndTransformsTheMatchingElements() {
      var source = Source.<Void, Void, Integer>of(1, 2, 3, 4).collect(x -> x % 2 == 0, x -> x * 10);

      assertThat(source.toList().evalOrElseThrow()).isEqualTo(List.of(20, 40));
    }

    @Test
    void filterNotRemovesTheMatchingElements() {
      var source = Source.<Void, Void, Integer>of(1, 2, 3, 4).filterNot(x -> x % 2 == 0);

      assertThat(source.toList().evalOrElseThrow()).isEqualTo(List.of(1, 3));
    }
  }

  @Nested
  class Fold {

    @Test
    void foldLeftSemantic() {
      var list = Source.of(1, 2, 3);

      var result = list
          .foldLeft(
              "z",
              (n, acc) -> "(" + n + " + " + acc + ")")
          .evalOrElseThrow();

      assertThat(result).isEqualTo("(((z + 1) + 2) + 3)");
    }

    @Test
    void foldRightSemantic() {
      var list = Source.of(1, 2, 3);

      var result = list
          .foldRight(
              success("z"),
              (n, acc) -> acc.map(s -> "(" + n + " + " + s + ")"))
          .evalOrElseThrow();

      assertThat(result).isEqualTo("(1 + (2 + (3 + z)))");
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void foldRightStopsEarlyOnAnInfiniteSource() {
      var calls = new AtomicInteger();
      var source = Source.<Void, Void, Integer>iterate(calls::incrementAndGet);

      var exists = source.foldRight(
          Program.<Void, Void, Boolean>success(false),
          (x, rest) -> x > 3 ? success(true) : rest);

      assertThat(exists.evalOrElseThrow()).isTrue();
      assertThat(calls).hasValue(4);
    }

    @Test
    void foldRightDoesNotEvaluateRestWhenItIsNotNeeded() {
      var calls = new AtomicInteger();
      var source = Source.<Void, Void, Integer>iterate(calls::incrementAndGet);

      var result = source.foldRight(
          Program.<Void, Void, Boolean>success(false),
          (_, _) -> success(true));

      assertThat(result.evalOrElseThrow()).isTrue();
      assertThat(calls).hasValue(1);
    }

    @Test
    void foldRightCanUseTheRestMoreThanOnce() {
      var source = Source.<Void, Void, Integer>from(List.of(1, 2, 3));

      // the combinator evaluates rest twice: without memoized cells, the second time would find the
      // iterator exhausted and the fold would not see the elements
      var result = source.foldRight(
          Program.<Void, Void, Integer>success(0),
          (x, rest) -> rest.flatMap(a -> rest.map(b -> a + b + x)));

      // f(3) = 0 + 0 + 3, f(2) = 3 + 3 + 2, f(1) = 8 + 8 + 1
      assertThat(result.evalOrElseThrow()).isEqualTo(17);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void foldRightIsStackSafe() {
      var source = Source.<Void, Void, Integer>iterate(0, x -> x + 1).take(500_000);

      var sum = source.foldRight(success(0L), (x, rest) -> rest.map(acc -> acc + x));

      assertThat(sum.evalOrElseThrow()).isEqualTo(124_999_750_000L);
    }

    @Test
    void toListCanBeEvaluatedMoreThanOnce() {
      var program = Source.of(1, 2, 3).toList();

      assertThat(program.evalOrElseThrow()).isEqualTo(List.of(1, 2, 3));
      assertThat(program.evalOrElseThrow()).isEqualTo(List.of(1, 2, 3));
    }
  }

  @Nested
  class Zip {

    @Test
    void zip() {
      var stream = Source.of("a", "b", "c");

      var zip = Source.zipWithIndex(stream).toList();

      assertThat(zip.evalOrElseThrow())
        .isEqualTo(List.of(tuple("a", 0), tuple("b", 1), tuple("c", 2)));
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void zipWithAnEmptySourceDoesNotEvaluateTheOther() {
      var calls = new AtomicInteger();
      // searching for the first element never ends, so a regression hangs: hence the separate thread
      var stuck = Source.<Void, Void, Integer>iterate(0, x -> x + 1)
          .filter(_ -> {
            calls.incrementAndGet();
            return false;
          });

      var result = Source.zip(Source.<Void, Void, Integer>empty(), stuck).toList();

      assertThat(result.evalOrElseThrow()).isEmpty();
      assertThat(calls).hasValue(0);
    }

    @Test
    void zipDoesNotEvaluateTheRightSideOnceTheLeftEnds() {
      var calls = new AtomicInteger();
      var right = Source.<Void, Void, Integer>iterate(calls::incrementAndGet);

      var result = Source.zip(Source.<Void, Void, Integer>of(1, 2), right).toList();

      assertThat(result.evalOrElseThrow())
        .isEqualTo(List.of(tuple(1, 1), tuple(2, 2)));
      assertThat(calls).hasValue(2);
    }
  }

  @Nested
  class Interleave {

    @Test
    void interleaveSameSize() {
      var stream1 = Source.of(1, 2, 3);
      var stream2 = Source.of(4, 5, 6);

      var list = Source.interleave(stream1, stream2).toList();

      assertThat(list.evalOrElseThrow()).isEqualTo(List.of(1, 4, 2, 5, 3, 6));
    }

    @Test
    void interleaveWithEmptyList() {
      var stream1 = Source.of(1, 2, 3, 4, 5, 6);

      var list1 = Source.interleave(stream1, Source.empty()).toList();
      var list2 = Source.interleave(Source.empty(), stream1).toList();

      assertThat(list1.evalOrElseThrow()).isEqualTo(List.of(1, 2, 3, 4, 5, 6));
      assertThat(list2.evalOrElseThrow()).isEqualTo(List.of(1, 2, 3, 4, 5, 6));
    }

    @Test
    void interleaveSecondLarge() {
      var stream1 = Source.of(1, 2, 3);
      var stream2 = Source.of(4, 5, 6, 7);

      var list = Source.interleave(stream1, stream2).toList();

      assertThat(list.evalOrElseThrow()).isEqualTo(List.of(1, 4, 2, 5, 3, 6, 7));
    }

    @Test
    void interleaveFirstLarge() {
      var stream1 = Source.of(1, 2, 3);
      var stream2 = Source.of(4, 5, 6, 7);

      var list = Source.interleave(stream2, stream1).toList();

      assertThat(list.evalOrElseThrow()).isEqualTo(List.of(4, 1, 5, 2, 6, 3, 7));
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void interleaveEmitsTheFirstElementWithoutWaitingForTheSecondSource() {
      var stuck = Source.<Void, Void, Integer>iterate(0, x -> x + 1)
          .filter(_ -> false);

      var result = Source.interleave(Source.<Void, Void, Integer>of(1, 2), stuck)
          .take(1).toList();

      assertThat(result.evalOrElseThrow()).isEqualTo(List.of(1));
    }
  }

  @Nested
  class From {

    @Test
    void fromAsksForANewIteratorOnEachTraversal() {
      var calls = new AtomicInteger();
      Iterable<Integer> iterable = () -> {
        calls.incrementAndGet();
        return List.of(1, 2).iterator();
      };
      var program = Source.<Void, Void, Integer>from(iterable).toList();

      assertThat(program.evalOrElseThrow()).isEqualTo(List.of(1, 2));
      assertThat(program.evalOrElseThrow()).isEqualTo(List.of(1, 2));
      assertThat(calls).hasValue(2);
    }

    @Test
    void fromIteratorCallsTheSupplierWhenTraversed() {
      var calls = new AtomicInteger();
      var program = Source.<Void, Void, Integer>fromIterator(() -> {
        calls.incrementAndGet();
        return List.of(1, 2).iterator();
      }).toList();

      assertThat(calls).hasValue(0);
      assertThat(program.evalOrElseThrow()).isEqualTo(List.of(1, 2));
      assertThat(program.evalOrElseThrow()).isEqualTo(List.of(1, 2));
      assertThat(calls).hasValue(2);
    }

    @Test
    void tailOfFromCanBeTraversedMoreThanOnce() {
      var source = Source.<Void, Void, Integer>from(List.of(1, 2, 3));
      var tail = source.split().evalOrElseThrow().orElseThrow().second();

      assertThat(tail.toList().evalOrElseThrow()).isEqualTo(List.of(2, 3));
      assertThat(tail.toList().evalOrElseThrow()).isEqualTo(List.of(2, 3));
    }

    @Test
    void fromIteratorCanBeConsumedMoreThanOnceWhenSupplierCreatesNewIterator() {
      var source = Source.fromIterator(
          () -> List.of(1, 2, 3).iterator());

      assertThat(source.toList().evalOrElseThrow())
          .containsExactly(1, 2, 3);

      assertThat(source.toList().evalOrElseThrow())
          .containsExactly(1, 2, 3);
    }

    @Test
    void fromIteratorCanReuseTheSameIteratorInstance() {
      var iterator = List.of(1, 2, 3).iterator();
      var source = Source.fromIterator(() -> iterator);

      assertThat(source.toList().evalOrElseThrow())
          .containsExactly(1, 2, 3);

      assertThat(source.toList().evalOrElseThrow())
          .isEmpty();
    }

    @Test
    void readFile() {
      assertEquals(
          impureReadFile("LICENSE"),
          readFileLazy("LICENSE").evalOrElseThrow());
    }

    private Program<Void, String, String> readFileLazy(String file) {
      return bracket(
          reader(file),
          reader -> Source.<Void, String, String>fromStream(reader::lines)
            .foldLeft("", (a, b) -> a + '\n' + b)
        ).catchAll(_ -> failure("--- file not found ---"));
    }

    private Program<Void, String, BufferedReader> reader(String file) {
      return supply(() -> {
        try {
          return Files.newBufferedReader(Paths.get(file));
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      });
    }

    private String impureReadFile(String file) {
      try (var reader = Files.newBufferedReader(Paths.get(file))) {
        var content = "";
        while (true) {
          var line = reader.readLine();
          if (line == null) {
            break;
          }
          content = content + '\n' + line;
        }
        return content;
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  @Nested
  class Empty {

    @Test
    void operationsOnAnEmptySourceAreEmpty() {
      var empty = Source.<Void, Void, Integer>empty();

      assertThat(empty.map(x -> x + 1).filter(x -> x > 0).take(3).drop(1).toList().evalOrElseThrow()).isEmpty();
      assertThat(empty.flatMap(x -> Source.of(x)).mapEval(Program::<Void, Void, Integer>success).toList().evalOrElseThrow())
        .isEmpty();
      assertThat(empty.foldLeft(0, Integer::sum).evalOrElseThrow()).isZero();
    }
  }

  @Nested
  class Printing {

    @Test
    void toStringShowsTheTypesOfTheParts() {
      var source = Source.<Void, Void, Integer>of(1).prepend(success(0));

      assertThat(source).hasToString("NonEmpty(head=Pure, tail=NonEmpty)");
      assertThat(source.concat(Source.of(2))).hasToString("Concat(left=NonEmpty, right=NonEmpty)");
    }

    @Test
    void toStringOfALongSourceDoesNotOverflowTheStack() {
      Source<Void, Void, Integer> prepended = Source.empty();
      Source<Void, Void, Integer> concatenated = Source.empty();
      for (int i = 0; i < 100_000; i++) {
        prepended = prepended.prepend(success(i));
        concatenated = concatenated.concat(Source.of(i));
      }

      assertThat(prepended).hasToString("NonEmpty(head=Pure, tail=NonEmpty)");
      assertThat(concatenated).hasToString("Concat(left=Concat, right=NonEmpty)");
    }
  }

  @Nested
  class Errors {

    @Test
    @SuppressWarnings("NullAway") // NullAway already rejects it at compile time, but not every caller uses it
    void ofRejectsANullElement() {
      assertThatThrownBy(() -> Source.<Void, Void, String>of((String) null))
        .isInstanceOf(NullPointerException.class);
    }

    @Test
    @SuppressWarnings("NullAway") // NullAway already rejects it at compile time, but not every caller uses it
    void aNullElementFromAnIteratorFailsWhenItIsTraversed() {
      var source = Source.<Void, Void, String>fromIterator(() -> Arrays.asList("a", null).iterator());

      assertThatThrownBy(() -> source.toList().evalOrElseThrow()).isInstanceOf(NullPointerException.class);
    }

    @Test
    @SuppressWarnings("NullAway") // NullAway already rejects it at compile time, but not every caller uses it
    void aNullElementFromAProgramFailsWhenItIsTraversed() {
      var source = Source.<Void, Void, String>eval(supply(() -> null));

      assertThatThrownBy(() -> source.toList().evalOrElseThrow()).isInstanceOf(NullPointerException.class);
    }

    @Test
    void catchAllRecoversFromAnElementThatThrows() {
      var source = Source.<Void, Void, Integer>nonEmpty(
          Program.raise(IllegalStateException::new), Source.of(2));

      var result = source.toList().catchAll(_ -> success(List.of(-1)));

      assertThat(result.evalOrElseThrow()).isEqualTo(List.of(-1));
    }

    @Test
    void catchAllRecoversFromAnIteratorThatThrows() {
      var calls = new AtomicInteger();
      Iterator<Integer> iterator = new Iterator<>() {
        @Override
        public boolean hasNext() {
          return true;
        }

        @Override
        public Integer next() {
          if (calls.incrementAndGet() == 3) {
            throw new IllegalStateException("broken");
          }
          return calls.get();
        }
      };

      var result = Source.<Void, Void, Integer>fromIterator(() -> iterator).toList()
          .catchAll(_ -> success(List.of(-1)));

      assertThat(result.evalOrElseThrow()).isEqualTo(List.of(-1));
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void timeoutStopsTheTraversalOfAnInfiniteSource() {
      var program = Source.<Void, Void, Long>iterate(0L, x -> x + 1)
          .foldLeft(0L, Long::sum)
          .timeout(Duration.ofMillis(100));

      assertThatThrownBy(program::eval).isInstanceOf(TimeoutException.class);
    }
  }
}
