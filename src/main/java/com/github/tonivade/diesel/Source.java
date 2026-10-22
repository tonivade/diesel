/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Program.success;
import static com.github.tonivade.diesel.Tuple2.tuple;

import com.github.tonivade.diesel.function.Finisher2;
import com.github.tonivade.purefun.Kind;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * A sequence of programs, evaluated lazily, one element at a time, when it's traversed.
 *
 * <p>It's cold: nothing is memoized between traversals, so each traversal, like {@link #foldLeft}
 * or {@link #toList}, evaluates the programs again, including their effects.
 *
 * <p>Elements can't be {@code null}: {@link #of(Object)} rejects one at once, and an iterator or
 * a program that produces one fails with a {@link NullPointerException} when it's traversed.
 *
 * @param <S> the state type
 * @param <E> the error type
 * @param <T> the element type
 */
public sealed interface Source<S, E, T> extends Kind<Source<S, E, ?>, T> {

  /**
   * The empty source, shared by every type: see {@link #empty()}.
   */
  Source<?, ?, ?> EMPTY = new Empty<>();

  /**
   * Converts a higher-kinded value to a {@code Source}.
   *
   * @param value the value to convert
   * @param <S> the state type
   * @param <E> the error type
   * @param <T> the element type
   * @return the value as a {@code Source}
   */
  @SuppressWarnings("unchecked")
  static <S, E, T> Source<S, E, T> toSource(Kind<Source<S, E, ?>, ? extends T> value) {
    return (Source<S, E, T>) value;
  }

  /**
   * Creates an empty source.
   *
   * @param <S> the state type
   * @param <E> the error type
   * @param <T> the element type
   * @return an empty source
   */
  @SuppressWarnings("unchecked")
  static <S, E, T> Source<S, E, T> empty() {
    return (Source<S, E, T>) EMPTY;
  }

  /**
   * Creates a source containing one successful value.
   *
   * @param value the value to emit
   * @param <S> the state type
   * @param <E> the error type
   * @param <T> the element type
   * @return a source containing the value
   */
  static <S, E, T> Source<S, E, T> of(T value) {
    return eval(success(value));
  }

  /**
   * Creates a source from the given values.
   *
   * @param values the values to emit in order
   * @param <S> the state type
   * @param <E> the error type
   * @param <T> the element type
   * @return a source containing the values
   */
  @SafeVarargs
  static <S, E, T> Source<S, E, T> of(T... values) {
    return from(List.of(values));
  }

  /**
   * Creates a source containing one program.
   *
   * @param value the program to emit
   * @param <S> the state type
   * @param <E> the error type
   * @param <T> the element type
   * @return a source containing the program
   */
  static <S, E, T> Source<S, E, T> eval(Program<S, E, T> value) {
    return nonEmpty(value, empty());
  }

  /**
   * Creates a source with a head program and a tail source.
   *
   * @param head the first program
   * @param tail the remaining source
   * @param <S> the state type
   * @param <E> the error type
   * @param <T> the element type
   * @return a non-empty source
   */
  static <S, E, T> Source<S, E, T> nonEmpty(Program<S, E, T> head, Source<S, E, T> tail) {
    return new NonEmpty<>(head, tail);
  }

  /**
   * Defers obtaining a program until the source is traversed.
   *
   * @param supplier the supplier of the program
   * @param <S> the state type
   * @param <E> the error type
   * @param <T> the element type
   * @return a source that obtains its program lazily
   */
  static <S, E, T> Source<S, E, T> supply(Supplier<Program<S, E, T>> supplier) {
    return suspend(() -> eval(supplier.get()));
  }

  /**
   * Creates an infinite source by repeatedly applying a generator to the previous value.
   *
   * @param seed the first value
   * @param generator the function that produces each subsequent value
   * @param <S> the state type
   * @param <E> the error type
   * @param <T> the element type
   * @return an infinite source beginning with {@code seed}
   */
  static <S, E, T> Source<S, E, T> iterate(T seed, UnaryOperator<T> generator) {
    return nonEmpty(success(seed), suspend(() -> iterate(generator.apply(seed), generator)));
  }

  /**
   * Creates an infinite source by invoking a supplier for each value.
   *
   * @param generator the supplier of each value
   * @param <S> the state type
   * @param <E> the error type
   * @param <T> the element type
   * @return an infinite source generated lazily
   */
  static <S, E, T> Source<S, E, T> iterate(Supplier<T> generator) {
    return suspend(() -> nonEmpty(success(generator.get()), iterate(generator)));
  }

  /**
   * Defers creation of a source until it is traversed.
   *
   * @param lazy the supplier of the source
   * @param <S> the state type
   * @param <E> the error type
   * @param <T> the element type
   * @return a suspended source
   */
  static <S, E, T> Source<S, E, T> suspend(Supplier<? extends Source<S, E, T>> lazy) {
    return new Suspend<>(Program.supply(() -> lazy.get()));
  }

  /**
   * Creates a source from an iterable, obtaining its iterator lazily.
   *
   * <p>The source is cold at its root: each traversal calls {@link Iterable#iterator()} again. Inside
   * a traversal the elements are memoized, so a tail obtained with {@link #split()} can be
   * traversed more than once without advancing the iterator again.
   *
   * <p>Because of that memoization, a tail kept after it's traversed, like one obtained with
   * {@link #split()}, keeps in memory every element read after it. A traversal that doesn't keep
   * any, like {@link #foldLeft} or {@link #toList}, doesn't retain the elements it has read.
   *
   * <p>The same tail must not be traversed concurrently, like from two forks: the iterator isn't
   * thread-safe, and an element could be read twice.
   *
   * @param iterable the source of elements
   * @param <S> the state type
   * @param <E> the error type
   * @param <T> the element type
   * @return a source that traverses the iterable
   */
  static <S, E, T> Source<S, E, T> from(Iterable<? extends T> iterable) {
    return fromIterator(iterable::iterator);
  }

  /**
   * Creates a source from a stream obtained on each traversal.
   *
   * <p>A stream can be traversed only once, so the supplier is called inside the evaluation, not
   * when the source is built: for example {@code () -> Files.lines(path)}. See
   * {@link #from(Iterable)} for the memoization of the elements.
   *
   * <p>The stream is never closed by the source. A stream that holds a resource, like the file of
   * {@code Files.lines}, must be closed by the caller, for example by acquiring it with
   * {@link Program#bracket} around the program that traverses the source.
   *
   * @param stream the supplier of the stream of elements
   * @param <S> the state type
   * @param <E> the error type
   * @param <T> the element type
   * @return a source that traverses the stream
   */
  static <S, E, T> Source<S, E, T> fromStream(Supplier<? extends Stream<? extends T>> stream) {
    return fromIterator(() -> stream.get().iterator());
  }

  /**
   * Creates a source from an iterator obtained on each traversal.
   *
   * <p>Use it when the iterator comes from a resource that can be traversed only once, like
   * {@code reader.lines().iterator()}: the supplier is called inside the evaluation, not when the
   * source is built. See {@link #from(Iterable)} for the memoization of the elements.
   *
   * @param iterator the source of elements
   * @param <S> the state type
   * @param <E> the error type
   * @param <T> the element type
   * @return a source that traverses the iterator
   */
  static <S, E, T> Source<S, E, T> fromIterator(Supplier<? extends Iterator<? extends T>> iterator) {
    return suspend(() -> step(iterator.get()));
  }

  /**
   * Combines corresponding elements from two sources into tuples.
   *
   * @param s1 the first source
   * @param s2 the second source
   * @param <S> the state type
   * @param <E> the error type
   * @param <A> the first element type
   * @param <B> the second element type
   * @return a source of pairs, ending when either source is exhausted
   */
  static <S, E, A, B> Source<S, E, Tuple2<A, B>> zip(Source<S, E, A> s1, Source<S, E, B> s2) {
    return zipWith(s1, s2, Tuple2::tuple);
  }

  /**
   * Pairs each element with its zero-based index.
   *
   * @param stream the source to index
   * @param <S> the state type
   * @param <E> the error type
   * @param <A> the element type
   * @return a source of elements paired with their indices
   */
  static <S, E, A> Source<S, E, Tuple2<A, Integer>> zipWithIndex(Source<S, E, A> stream) {
    return zip(stream, iterate(0, x -> x + 1));
  }

  /**
   * Combines corresponding elements from two sources using the given function.
   *
   * @param s1 the first source
   * @param s2 the second source
   * @param combinator the function that combines a pair of elements
   * @param <S> the state type
   * @param <E> the error type
   * @param <A> the first element type
   * @param <B> the second element type
   * @param <R> the result element type
   * @return a source of combined values, ending when either source is exhausted
   */
  static <S, E, A, B, R> Source<S, E, R> zipWith(Source<S, E, A> s1, Source<S, E, B> s2,
      Finisher2<A, B, R> combinator) {
    return new Suspend<>(s1.split().flatMap(
        op1 -> op1.map(t1 -> s2.split().map(
            op2 -> op2.map(t2 -> nonEmpty(
                Combine.zip(t1.first(), t2.first(), combinator),
                zipWith(t1.second(), t2.second(), combinator)))
            .orElseGet(Source::<S, E, R>empty)))
        .orElseGet(() -> Program.<S, E, Source<S, E, R>>success(empty()))));
  }

  /**
   * Alternates elements from two sources while preserving the order within each source.
   *
   * @param s1 the first source
   * @param s2 the second source
   * @param <S> the state type
   * @param <E> the error type
   * @param <A> the element type
   * @return a source containing the elements interleaved
   */
  static <S, E, A> Source<S, E, A> interleave(Source<S, E, A> s1, Source<S, E, A> s2) {
    return new Suspend<>(s1.split().map(
        op1 -> op1.map(t1 -> nonEmpty(t1.first(), interleave(s2, t1.second())))
        .orElse(s2)));
  }

  /**
   * The source with no elements.
   *
   * @param <S> the state type
   * @param <E> the error type
   * @param <T> the element type
   */
  final class Empty<S, E, T> implements Source<S, E, T> {

    private Empty() {}

    @Override
    public Program<S, E, T> head() {
      return Program.raise(NoSuchElementException::new);
    }

    @Override
    public Source<S, E, T> tail() {
      return this;
    }

    @Override
    public Program<S, E, Optional<T>> headOption() {
      return success(Optional.empty());
    }

    @Override
    public Program<S, E, Optional<Tuple2<Program<S, E, T>, Source<S, E, T>>>> split() {
      return success(Optional.empty());
    }

    @Override
    public Source<S, E, T> concat(Source<S, E, T> other) {
      return other;
    }

    @Override
    public Source<S, E, T> append(Program<S, E, T> other) {
      return nonEmpty(other, this);
    }

    @Override
    public Source<S, E, T> prepend(Program<S, E, T> other) {
      return append(other);
    }

    @Override
    public Source<S, E, T> take(int n) {
      return this;
    }

    @Override
    public Source<S, E, T> drop(int n) {
      return this;
    }

    @Override
    public Source<S, E, T> filter(Predicate<? super T> matcher) {
      return this;
    }

    @Override
    public Source<S, E, T> takeWhile(Predicate<? super T> matcher) {
      return this;
    }

    @Override
    public Source<S, E, T> dropWhile(Predicate<? super T> matcher) {
      return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> Source<S, E, R> map(Function<? super T, ? extends R> mapper) {
      return (Source<S, E, R>) this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> Source<S, E, R> mapEval(Function<? super T, ? extends Program<S, E, R>> mapper) {
      return (Source<S, E, R>) this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> Source<S, E, R> flatMap(Function<? super T, ? extends Source<S, E, R>> mapper) {
      return (Source<S, E, R>) this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> Source<S, E, R> collect(Predicate<? super T> isDefined, Function<? super T, ? extends R> mapper) {
      return (Source<S, E, R>) this;
    }

    @Override
    public <R> Program<S, E, R> foldLeft(R begin, Finisher2<? super R, ? super T, ? extends R> combinator) {
      return success(begin);
    }

    @Override
    public <R> Program<S, E, R> foldRight(Program<S, E, R> begin,
        Finisher2<? super T, ? super Program<S, E, R>, ? extends Program<S, E, R>> combinator) {
      return begin;
    }

    @Override
    public String toString() {
      return "Empty";
    }
  }

  /**
   * A source with a first element and the source that follows it.
   *
   * @param <S> the state type
   * @param <E> the error type
   * @param <T> the element type
   */
  final class NonEmpty<S, E, T> implements Source<S, E, T> {

    private final Program<S, E, T> head;
    private final Source<S, E, T> tail;

    /**
     * Creates a non empty source with head and tail.
     *
     * @param head the program of the first element
     * @param tail the source after the first element
     */
    public NonEmpty(Program<S, E, T> head, Source<S, E, T> tail) {
      this.head = head;
      this.tail = tail;
    }

    @Override
    public Program<S, E, T> head() {
      return head;
    }

    @Override
    public Source<S, E, T> tail() {
      // like drop(1): the discarded head is evaluated, so its effects and failures happen
      return drop(1);
    }

    @Override
    public Program<S, E, Optional<T>> headOption() {
      return head.map(Optional::of);
    }

    @Override
    public Program<S, E, Optional<Tuple2<Program<S, E, T>, Source<S, E, T>>>> split() {
      return success(Optional.of(Tuple2.tuple(head, tail)));
    }

    @Override
    public Source<S, E, T> concat(Source<S, E, T> other) {
      if (other instanceof Empty) {
        return this;
      }
      return new Concat<>(this, other);
    }

    @Override
    public Source<S, E, T> append(Program<S, E, T> other) {
      return concat(eval(other));
    }

    @Override
    public Source<S, E, T> prepend(Program<S, E, T> other) {
      return nonEmpty(other, this);
    }

    @Override
    public Source<S, E, T> take(int n) {
      if (n < 1) {
        return empty();
      }
      return suspend(() -> nonEmpty(head, tail.take(n - 1)));
    }

    @Override
    public Source<S, E, T> drop(int n) {
      if (n < 1) {
        return this;
      }
      // the discarded head is evaluated, like in filter and dropWhile, so its effects and failures happen
      return defer(() -> head.map(_ -> tail.drop(n - 1)));
    }

    @Override
    public Source<S, E, T> filter(Predicate<? super T> matcher) {
      return defer(() ->
        head.map(t -> matcher.test(t)
            ? nonEmpty(success(t), tail.filter(matcher))
            : tail.filter(matcher))
      );
    }

    @Override
    public Source<S, E, T> takeWhile(Predicate<? super T> matcher) {
      return defer(() ->
        head.map(t -> matcher.test(t)
            ? nonEmpty(success(t), tail.takeWhile(matcher))
            : empty())
      );
    }

    @Override
    public Source<S, E, T> dropWhile(Predicate<? super T> matcher) {
      return defer(() ->
        head.map(t -> matcher.test(t)
            ? tail.dropWhile(matcher)
            : nonEmpty(success(t), tail))
      );
    }

    @Override
    public <R> Source<S, E, R> map(Function<? super T, ? extends R> mapper) {
      return mapEval(mapper.andThen(Program::success));
    }

    @Override
    public <R> Source<S, E, R> mapEval(Function<? super T, ? extends Program<S, E, R>> mapper) {
      return suspend(
          () -> nonEmpty(head.flatMap(mapper), tail.mapEval(mapper)));
    }

    @Override
    public <R> Source<S, E, R> flatMap(Function<? super T, ? extends Source<S, E, R>> mapper) {
      return defer(
          () -> head.map(mapper).map(s -> s.concat(tail.flatMap(mapper))));
    }

    @Override
    public <R> Source<S, E, R> collect(Predicate<? super T> isDefined, Function<? super T, ? extends R> mapper) {
      return defer(() ->
        head.map(t -> isDefined.test(t)
            ? nonEmpty(success(mapper.apply(t)), tail.collect(isDefined, mapper))
            : tail.collect(isDefined, mapper)));
    }

    @Override
    public <R> Program<S, E, R> foldLeft(R begin, Finisher2<? super R, ? super T, ? extends R> combinator) {
      return head.flatMap(h -> tail.foldLeft(combinator.apply(begin, h), combinator));
    }

    @Override
    public <R> Program<S, E, R> foldRight(Program<S, E, R> begin,
        Finisher2<? super T, ? super Program<S, E, R>, ? extends Program<S, E, R>> combinator) {
      return head.flatMap(
          h -> combinator.apply(
              h, Program.suspend(() -> tail.foldRight(begin, combinator))));
    }

    @Override
    public String toString() {
      // only the types of the parts: printing them would go through the whole source, and the
      // program of the head, recursively, and a long one would overflow the stack
      return "NonEmpty(head=" + head.getClass().getSimpleName() + ", tail=" + tail.getClass().getSimpleName() + ")";
    }
  }

  /**
   * The elements of a source followed by the elements of another one, see {@link #concat}.
   *
   * @param <S> the state type
   * @param <E> the error type
   * @param <T> the element type
   */
  final class Concat<S, E, T> implements Source<S, E, T> {

    private final Source<S, E, T> left;
    private final Source<S, E, T> right;

    /**
     * Creates an instance of Concat with a left and right instances
     *
     * @param left the source whose elements come first
     * @param right the source whose elements come after
     */
    public Concat(Source<S, E, T> left, Source<S, E, T> right) {
      this.left = left;
      this.right = right;
    }

    @Override
    public Program<S, E, T> head() {
      return force().head();
    }

    @Override
    public Source<S, E, T> tail() {
      return force().tail();
    }

    @Override
    public Program<S, E, Optional<T>> headOption() {
      return force().headOption();
    }

    @Override
    public Program<S, E, Optional<Tuple2<Program<S, E, T>, Source<S, E, T>>>> split() {
      return Program.suspend(() -> {
        var concat = reassociate();
        return concat.left.split().flatMap(split -> split
            .map(t -> Program.<S, E, Optional<Tuple2<Program<S, E, T>, Source<S, E, T>>>>success(
                Optional.of(tuple(t.first(), t.second().concat(concat.right)))))
            .orElseGet(concat.right::split));
      });
    }

    @Override
    public Source<S, E, T> concat(Source<S, E, T> other) {
      if (other instanceof Empty) {
        return this;
      }
      return new Concat<>(this, other);
    }

    @Override
    public Source<S, E, T> append(Program<S, E, T> other) {
      return concat(eval(other));
    }

    @Override
    public Source<S, E, T> prepend(Program<S, E, T> other) {
      return nonEmpty(other, this);
    }

    @Override
    public Source<S, E, T> take(int n) {
      // without evaluating anything: otherwise the element after the last one taken would be
      // produced, and a filter with no more matches would search for it forever
      if (n < 1) {
        return empty();
      }
      return force().take(n);
    }

    @Override
    public Source<S, E, T> drop(int n) {
      if (n < 1) {
        return this;
      }
      return force().drop(n);
    }

    @Override
    public Source<S, E, T> filter(Predicate<? super T> matcher) {
      return force().filter(matcher);
    }

    @Override
    public Source<S, E, T> takeWhile(Predicate<? super T> matcher) {
      return force().takeWhile(matcher);
    }

    @Override
    public Source<S, E, T> dropWhile(Predicate<? super T> matcher) {
      return force().dropWhile(matcher);
    }

    @Override
    public <R> Source<S, E, R> map(Function<? super T, ? extends R> mapper) {
      return force().map(mapper);
    }

    @Override
    public <R> Source<S, E, R> mapEval(Function<? super T, ? extends Program<S, E, R>> mapper) {
      return force().mapEval(mapper);
    }

    @Override
    public <R> Source<S, E, R> flatMap(Function<? super T, ? extends Source<S, E, R>> mapper) {
      return force().flatMap(mapper);
    }

    @Override
    public <R> Source<S, E, R> collect(Predicate<? super T> isDefined, Function<? super T, ? extends R> mapper) {
      return force().collect(isDefined, mapper);
    }

    @Override
    public <R> Program<S, E, R> foldLeft(R begin, Finisher2<? super R, ? super T, ? extends R> combinator) {
      return force().foldLeft(begin, combinator);
    }

    @Override
    public <R> Program<S, E, R> foldRight(Program<S, E, R> begin,
        Finisher2<? super T, ? super Program<S, E, R>, ? extends Program<S, E, R>> combinator) {
      return force().foldRight(begin, combinator);
    }

    // turns ((a ++ b) ++ c) into a ++ (b ++ c), until the left side isn't a concat: otherwise every
    // element of a list built with concat in a loop would go through all the concats on its left
    private Concat<S, E, T> reassociate() {
      var first = left;
      var rest = right;
      while (first instanceof Concat<S, E, T> concat) {
        rest = new Concat<>(concat.right, rest);
        first = concat.left;
      }
      return new Concat<>(first, rest);
    }

    // the same list as a suspended head and tail, so the other operations don't need to know about
    // concat: the tail is a concat again, which split reassociates when it's reached
    private Source<S, E, T> force() {
      return new Suspend<>(split().map(split -> split
          .map(t -> nonEmpty(t.first(), t.second()))
          .orElseGet(Source::empty)));
    }

    @Override
    public String toString() {
      // only the types of the parts, like NonEmpty: printing them would recurse through the source
      return "Concat(left=" + left.getClass().getSimpleName() + ", right=" + right.getClass().getSimpleName() + ")";
    }
  }

  /**
   * A source that is only known when a program is evaluated, see {@link #suspend}.
   *
   * @param <S> the state type
   * @param <E> the error type
   * @param <T> the element type
   */
  final class Suspend<S, E, T> implements Source<S, E, T> {

    private final Program<S, E, Source<S, E, T>> program;

    /**
     * Creates an instance of a suspended source.
     *
     * @param program the program that returns the source
     */
    public Suspend(Program<S, E, Source<S, E, T>> program) {
      this.program = program;
    }

    @Override
    public Program<S, E, T> head() {
      return program.flatMap(Source::head);
    }

    @Override
    public Source<S, E, T> tail() {
      return lazyMap(Source::tail);
    }

    @Override
    public Program<S, E, Optional<T>> headOption() {
      return program.flatMap(Source::headOption);
    }

    @Override
    public Program<S, E, Optional<Tuple2<Program<S, E, T>, Source<S, E, T>>>> split() {
      return program.flatMap(Source::split);
    }

    @Override
    public Source<S, E, T> concat(Source<S, E, T> other) {
      if (other instanceof Empty) {
        return this;
      }
      return new Concat<>(this, other);
    }

    @Override
    public Source<S, E, T> append(Program<S, E, T> other) {
      return concat(eval(other));
    }

    @Override
    public Source<S, E, T> prepend(Program<S, E, T> other) {
      // without evaluating the program: the prepended element comes first, so it doesn't need it
      return nonEmpty(other, this);
    }

    @Override
    public Source<S, E, T> take(int n) {
      // without evaluating anything: otherwise the element after the last one taken would be
      // produced, and a filter with no more matches would search for it forever
      if (n < 1) {
        return empty();
      }
      return lazyMap(s -> s.take(n));
    }

    @Override
    public Source<S, E, T> drop(int n) {
      if (n < 1) {
        return this;
      }
      return lazyMap(s -> s.drop(n));
    }

    @Override
    public Source<S, E, T> filter(Predicate<? super T> matcher) {
      return lazyMap(s -> s.filter(matcher));
    }

    @Override
    public Source<S, E, T> takeWhile(Predicate<? super T> matcher) {
      return lazyMap(s -> s.takeWhile(matcher));
    }

    @Override
    public Source<S, E, T> dropWhile(Predicate<? super T> matcher) {
      return lazyMap(s -> s.dropWhile(matcher));
    }

    @Override
    public <R> Source<S, E, R> map(Function<? super T, ? extends R> mapper) {
      return lazyMap(s -> s.map(mapper));
    }

    @Override
    public <R> Source<S, E, R> mapEval(Function<? super T, ? extends Program<S, E, R>> mapper) {
      return lazyMap(s -> s.mapEval(mapper));
    }

    @Override
    public <R> Source<S, E, R> flatMap(Function<? super T, ? extends Source<S, E, R>> mapper) {
      return lazyMap(s -> s.flatMap(mapper));
    }

    @Override
    public <R> Source<S, E, R> collect(Predicate<? super T> isDefined, Function<? super T, ? extends R> mapper) {
      return lazyMap(s -> s.collect(isDefined, mapper));
    }

    @Override
    public <R> Program<S, E, R> foldLeft(R begin, Finisher2<? super R, ? super T, ? extends R> combinator) {
      return program.flatMap(s -> s.foldLeft(begin, combinator));
    }

    @Override
    public <R> Program<S, E, R> foldRight(Program<S, E, R> begin,
        Finisher2<? super T, ? super Program<S, E, R>, ? extends Program<S, E, R>> combinator) {
      return program.flatMap(s -> s.foldRight(begin, combinator));
    }

    private <R> Source<S, E, R> lazyMap(Function<Source<S, E, T>, Source<S, E, R>> mapper) {
      return defer(() -> program.flatMap(Source::unsuspend).map(mapper));
    }

    @Override
    public String toString() {
      return "Suspend(" + program + ")";
    }
  }

  /**
   * Returns a program that evaluates the first element.
   *
   * <p>If the source is empty, the program raises a {@link java.util.NoSuchElementException}: it's
   * thrown, so it can be handled with {@code catchAll}, but it's not a failure of type {@code E}.
   * Use {@link #headOption()} or {@link #split()} to handle an empty source without an exception.
   *
   * @return a program yielding the first element, or raising an exception if the source is empty
   */
  Program<S, E, T> head();

  /**
   * Returns the source without its first element.
   *
   * <p>The first element is evaluated and discarded when the returned source is traversed, like
   * {@link #drop(int) drop(1)}: its effects happen and its failure is propagated. Use
   * {@link #split()} to get the remaining source without evaluating the head.
   *
   * @return the remaining source, or an empty source when this source is empty
   */
  Source<S, E, T> tail();

  /**
   * Returns the first element as an optional value.
   *
   * @return a program yielding the first element, or an empty optional if the source is empty
   */
  Program<S, E, Optional<T>> headOption();

  /**
   * Splits the source into its head program and remaining source without evaluating the head.
   *
   * @return a program yielding the head and tail, or an empty optional for an empty source
   */
  Program<S, E, Optional<Tuple2<Program<S, E, T>, Source<S, E, T>>>> split();

  /**
   * Appends another source after this source.
   *
   * @param other the source to append
   * @return a source containing this source's elements followed by {@code other}'s elements
   */
  Source<S, E, T> concat(Source<S, E, T> other);

  /**
   * Appends a program as the final element of this source.
   *
   * @param other the program to append
   * @return a source with {@code other} after this source's elements
   */
  Source<S, E, T> append(Program<S, E, T> other);

  /**
   * Prepends a program as the first element of this source.
   *
   * @param other the program to prepend
   * @return a source with {@code other} before this source's elements
   */
  Source<S, E, T> prepend(Program<S, E, T> other);

  /**
   * Returns at most the first {@code n} elements.
   *
   * @param n the maximum number of elements to retain
   * @return a source containing at most {@code n} elements, or empty when {@code n} is less than one
   */
  Source<S, E, T> take(int n);

  /**
   * Skips up to the first {@code n} elements.
   *
   * <p>The skipped elements are evaluated and discarded, so their effects happen and their
   * failures are propagated.
   *
   * @param n the number of elements to skip
   * @return the source after skipping up to {@code n} elements
   */
  Source<S, E, T> drop(int n);

  /**
   * Retains only elements that match the predicate.
   *
   * @param matcher the predicate to test each element
   * @return a source containing matching elements
   */
  Source<S, E, T> filter(Predicate<? super T> matcher);

  /**
   * Retains elements from the beginning while they match the predicate.
   *
   * @param matcher the predicate to test each element
   * @return a source containing the initial matching elements
   */
  Source<S, E, T> takeWhile(Predicate<? super T> matcher);

  /**
   * Skips elements from the beginning while they match the predicate.
   *
   * @param matcher the predicate to test each element
   * @return a source beginning with the first non-matching element, if any
   */
  Source<S, E, T> dropWhile(Predicate<? super T> matcher);

  /**
   * Retains only elements that do not match the predicate.
   *
   * @param matcher the predicate to test each element
   * @return a source containing non-matching elements
   */
  default Source<S, E, T> filterNot(Predicate<? super T> matcher) {
    return filter(matcher.negate());
  }

  /**
   * Transforms each element using a pure mapping function.
   *
   * @param mapper the function applied to each element
   * @param <R> the result element type
   * @return a source of transformed elements
   */
  <R> Source<S, E, R> map(Function<? super T, ? extends R> mapper);

  /**
   * Transforms each element using a function that returns a program.
   *
   * @param mapper the effectful mapping function
   * @param <R> the result element type
   * @return a source of values produced by the mapped programs
   */
  <R> Source<S, E, R> mapEval(Function<? super T, ? extends Program<S, E, R>> mapper);

  /**
   * Maps each element to a source and concatenates the resulting sources.
   *
   * @param mapper the function that produces a source for each element
   * @param <R> the result element type
   * @return the concatenation of the mapped sources
   */
  <R> Source<S, E, R> flatMap(Function<? super T, ? extends Source<S, E, R>> mapper);

  /**
   * Replaces each element with the same following source.
   *
   * @param next the source to concatenate after each element is discarded
   * @param <R> the result element type
   * @return the repeated concatenation of {@code next}
   */
  default <R> Source<S, E, R> andThen(Source<S, E, R> next) {
    return flatMap(_ -> next);
  }

  /**
   * Keeps elements accepted by the predicate and transforms them.
   *
   * @param isDefined the predicate that selects elements
   * @param mapper the function that transforms selected elements
   * @param <R> the result element type
   * @return a source of transformed elements accepted by the predicate
   */
  <R> Source<S, E, R> collect(Predicate<? super T> isDefined, Function<? super T, ? extends R> mapper);

  /**
   * Folds the elements from left to right into a single value.
   *
   * <p>The same {@code begin} is used by every evaluation of the returned program, so it must not be
   * mutable: an accumulator that the combinator modifies, like a list it adds to, keeps the elements
   * of the previous evaluations. Create it inside the evaluation instead, for example with
   * {@code Program.suspend(() -> source.foldLeft(new ArrayList<>(), ...))}, like {@link #toList()}.
   *
   * @param begin the initial accumulator
   * @param combinator the function combining the accumulator with each element
   * @param <R> the accumulator and result type
   * @return a program yielding the final accumulator
   */
  <R> Program<S, E, R> foldLeft(R begin, Finisher2<? super R, ? super T, ? extends R> combinator);

  /**
   * Folds the elements from right to left using a program as the initial result.
   *
   * @param begin the program returned for an empty source
   * @param combinator the function combining an element with the folded remainder
   * @param <R> the result type
   * @return a program yielding the folded result
   */
  <R> Program<S, E, R> foldRight(Program<S, E, R> begin,
      Finisher2<? super T, ? super Program<S, E, R>, ? extends Program<S, E, R>> combinator);

  /**
   * Evaluates the source and collects its elements into a new list.
   *
   * @return a program yielding a list of all elements
   */
  default Program<S, E, List<T>> toList() {
    // a new list for each evaluation, otherwise every evaluation would add to the same list
    return Program.suspend(() -> foldLeft(new ArrayList<>(), (acc, t) -> {
      acc.add(t);
      return acc;
    }));
  }

  // evaluates the suspended sources until it reaches one that isn't, so an operation applied to
  // the result doesn't wrap another suspension that would have to be evaluated again by each layer
  private static <S, E, T> Program<S, E, Source<S, E, T>> unsuspend(Source<S, E, T> source) {
    if (source instanceof Suspend<S, E, T> suspend) {
      return suspend.program.flatMap(Source::unsuspend);
    }
    return success(source);
  }

  // like suspend, but the source is returned by a program, which is built when the source is
  // traversed and evaluated then
  private static <S, E, T> Source<S, E, T> defer(Supplier<Program<S, E, Source<S, E, T>>> program) {
    return new Suspend<>(Program.suspend(program));
  }

  private static <S, E, T> Source<S, E, T> step(Iterator<? extends T> iterator) {
    // memoized: the iterator advances once per cell, however many times the cell is evaluated
    return new Suspend<>(Program.<S, E, Source<S, E, T>>supply(() -> {
      if (iterator.hasNext()) {
        return nonEmpty(success(iterator.next()), step(iterator));
      }
      return empty();
    }).memoized());
  }
}
