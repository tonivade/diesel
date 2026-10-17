/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Program.success;
import static com.github.tonivade.diesel.Tuple2.tuple;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import com.github.tonivade.purefun.Kind;

public sealed interface Lazy<S, E, T> extends Kind<Lazy<S, E, ?>, T> {

  Lazy<?, ?, ?> EMPTY = new Empty<>();

  @SuppressWarnings("unchecked")
  static <S, E, T> Lazy<S, E, T> toLazy(Kind<Lazy<S, E, ?>, ? extends T> value) {
    return (Lazy<S, E, T>) value;
  }

  @SuppressWarnings("unchecked")
  static <S, E, T> Lazy<S, E, T> empty() {
    return (Lazy<S, E, T>) EMPTY;
  }

  static <S, E, T> Lazy<S, E, T> of(T value) {
    return of(success(value));
  }

  static <S, E, T> Lazy<S, E, T> of(Program<S, E, T> value) {
    return nonEmpty(value, empty());
  }

  static <S, E, T> Lazy<S, E, T> nonEmpty(Program<S, E, T> head, Lazy<S, E, T> tail) {
    return new NonEmpty<>(head, tail);
  }

  static <S, E, T> Lazy<S, E, T> supply(Supplier<Program<S, E, T>> supplier) {
    return suspend(() -> of(supplier.get()));
  }

  static <S, E, T> Lazy<S, E, T> iterate(T seed, UnaryOperator<T> generator) {
    return nonEmpty(success(seed), suspend(() -> iterate(generator.apply(seed), generator)));
  }

  static <S, E, T> Lazy<S, E, T> iterate(Supplier<T> generator) {
    return unfold(null, _ -> Optional.of(tuple(generator.get(), null)));
  }

  static <S, E, T> Lazy<S, E, T> suspend(Supplier<? extends Lazy<S, E, T>> lazy) {
    return new Suspend<>(Program.supply(() -> lazy.get()));
  }

  static <S, E, T> Lazy<S, E, T> from(Iterable<T> iterable) {
    return from(iterable.iterator());
  }

  static <S, E, T> Lazy<S, E, T> from(Stream<T> stream) {
    return from(stream.iterator());
  }

  static <S, E, T> Lazy<S, E, T> from(Iterator<T> iterator) {
    return unfold(null, _ -> {
      if (iterator.hasNext()) {
        return Optional.of(tuple(iterator.next(), null));
      }
      return Optional.empty();
    });
  }

  final class Empty<S, E, T> implements Lazy<S, E, T> {

    private Empty() {}

    @Override
    public Program<S, E, T> head() {
      throw new NoSuchElementException();
    }

    @Override
    public Lazy<S, E, T> tail() {
      return this;
    }

    @Override
    public Program<S, E, Optional<T>> headOption() {
      return success(Optional.empty());
    }

    @Override
    public Program<S, E, Optional<Tuple2<Program<S, E, T>, Lazy<S, E, T>>>> split() {
      return success(Optional.empty());
    }

    @Override
    public Lazy<S, E, T> concat(Lazy<S, E, T> other) {
      return other;
    }

    @Override
    public Lazy<S, E, T> append(Program<S, E, T> other) {
      return nonEmpty(other, this);
    }

    @Override
    public Lazy<S, E, T> prepend(Program<S, E, T> other) {
      return append(other);
    }

    @Override
    public Lazy<S, E, T> take(int n) {
      return this;
    }

    @Override
    public Lazy<S, E, T> drop(int n) {
      return this;
    }

    @Override
    public Lazy<S, E, T> filter(Predicate<? super T> matcher) {
      return this;
    }

    @Override
    public Lazy<S, E, T> takeWhile(Predicate<? super T> matcher) {
      return this;
    }

    @Override
    public Lazy<S, E, T> dropWhile(Predicate<? super T> matcher) {
      return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> Lazy<S, E, R> map(Function<? super T, ? extends R> mapper) {
      return (Lazy<S, E, R>) this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> Lazy<S, E, R> mapEval(Function<? super T, ? extends Program<S, E, R>> mapper) {
      return (Lazy<S, E, R>) this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> Lazy<S, E, R> flatMap(Function<? super T, ? extends Lazy<S, E, R>> mapper) {
      return (Lazy<S, E, R>) this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> Lazy<S, E, R> collect(Predicate<? super T> isDefined, Function<? super T, ? extends R> mapper) {
      return (Lazy<S, E, R>) this;
    }

    @Override
    public <R> Program<S, E, R> foldLeft(R begin, BiFunction<? super R, ? super T, ? extends R> combinator) {
      return success(begin);
    }

    @Override
    public <R> Program<S, E, R> foldRight(Program<S, E, R> begin,
        BiFunction<? super T, ? super Program<S, E, R>, ? extends Program<S, E, R>> combinator) {
      return begin;
    }
  }

  record NonEmpty<S, E, T>(Program<S, E, T> head, Lazy<S, E, T> tail) implements Lazy<S, E, T> {

    @Override
    public Program<S, E, Optional<T>> headOption() {
      return head.map(Optional::of);
    }

    @Override
    public Program<S, E, Optional<Tuple2<Program<S, E, T>, Lazy<S, E, T>>>> split() {
      return success(Optional.of(Tuple2.tuple(head, tail)));
    }

    @Override
    public Lazy<S, E, T> concat(Lazy<S, E, T> other) {
    return supply(() -> nonEmpty(head, tail.concat(other)));
    }

    @Override
    public Lazy<S, E, T> append(Program<S, E, T> other) {
      return supply(() -> nonEmpty(head, tail.append(other)));
    }

    @Override
    public Lazy<S, E, T> prepend(Program<S, E, T> other) {
      return supply(() -> nonEmpty(other, this));
    }

    @Override
    public Lazy<S, E, T> take(int n) {
      if (n < 1) {
        return empty();
      }
      return supply(() -> nonEmpty(head, tail.take(n - 1)));
    }

    @Override
    public Lazy<S, E, T> drop(int n) {
      if (n < 0) {
        return this;
      }
      return supply(() -> tail.drop(n - 1));
    }

    @Override
    public Lazy<S, E, T> filter(Predicate<? super T> matcher) {
      return suspend(() ->
        head.map(t -> matcher.test(t)
            ? nonEmpty(head, tail.filter(matcher))
            : tail.filter(matcher))
      );
    }

    @Override
    public Lazy<S, E, T> takeWhile(Predicate<? super T> matcher) {
      return suspend(() ->
        head.map(t -> matcher.test(t)
            ? nonEmpty(head, tail.takeWhile(matcher))
            : empty())
      );
    }

    @Override
    public Lazy<S, E, T> dropWhile(Predicate<? super T> matcher) {
      return suspend(() ->
        head.map(t -> matcher.test(t)
            ? tail.dropWhile(matcher)
            : this)
      );
    }

    @Override
    public <R> Lazy<S, E, R> map(Function<? super T, ? extends R> mapper) {
      return mapEval(mapper.andThen(Program::success));
    }

    @Override
    public <R> Lazy<S, E, R> mapEval(Function<? super T, ? extends Program<S, E, R>> mapper) {
      return supply(
          () -> nonEmpty(head.flatMap(mapper), tail.mapEval(mapper)));
    }

    @Override
    public <R> Lazy<S, E, R> flatMap(Function<? super T, ? extends Lazy<S, E, R>> mapper) {
      return suspend(
          () -> head.map(mapper).map(s -> s.concat(tail.flatMap(mapper))));
    }

    @Override
    public <R> Lazy<S, E, R> collect(Predicate<? super T> isDefined, Function<? super T, ? extends R> mapper) {
      return suspend(() ->
        head.map(t -> isDefined.test(t)
            ? nonEmpty(head.map(mapper), tail.collect(isDefined, mapper))
            : tail.collect(isDefined, mapper)));
    }

    @Override
    public <R> Program<S, E, R> foldLeft(R begin, BiFunction<? super R, ? super T, ? extends R> combinator) {
      return head.flatMap(h -> tail.foldLeft(combinator.apply(begin, h), combinator));
    }

    @Override
    public <R> Program<S, E, R> foldRight(Program<S, E, R> begin,
        BiFunction<? super T, ? super Program<S, E, R>, ? extends Program<S, E, R>> combinator) {
      return head.flatMap(h -> tail.foldRight(combinator.apply(h, begin), combinator));
    }

    private <R> Lazy<S, E, R> supply(Supplier<Lazy<S, E, R>> stream) {
      return new Suspend<>(Program.supply(stream));
    }

    private <R> Lazy<S, E, R> suspend(Supplier<Program<S, E, Lazy<S, E, R>>> stream) {
      return new Suspend<>(Program.suspend(stream));
    }
  }

  record Suspend<S, E, T>(Program<S, E, Lazy<S, E, T>> program) implements Lazy<S, E, T> {

    @Override
    public Program<S, E, T> head() {
      return program.flatMap(Lazy::head);
    }

    @Override
    public Lazy<S, E, T> tail() {
      return lazyMap(s -> s.tail());
    }

    @Override
    public Program<S, E, Optional<T>> headOption() {
      return program.flatMap(Lazy::headOption);
    }

    @Override
    public Program<S, E, Optional<Tuple2<Program<S, E, T>, Lazy<S, E, T>>>> split() {
      return program.flatMap(Lazy::split);
    }

    @Override
    public Lazy<S, E, T> concat(Lazy<S, E, T> other) {
      return lazyMap(s -> s.concat(other));
    }

    @Override
    public Lazy<S, E, T> append(Program<S, E, T> other) {
      return lazyMap(s -> s.append(other));
    }

    @Override
    public Lazy<S, E, T> prepend(Program<S, E, T> other) {
      return lazyMap(s -> s.prepend(other));
    }

    @Override
    public Lazy<S, E, T> take(int n) {
      return lazyMap(s -> s.take(n));
    }

    @Override
    public Lazy<S, E, T> drop(int n) {
      return lazyMap(s -> s.drop(n));
    }

    @Override
    public Lazy<S, E, T> filter(Predicate<? super T> matcher) {
      return lazyMap(s -> s.filter(matcher));
    }

    @Override
    public Lazy<S, E, T> takeWhile(Predicate<? super T> matcher) {
      return lazyMap(s -> s.takeWhile(matcher));
    }

    @Override
    public Lazy<S, E, T> dropWhile(Predicate<? super T> matcher) {
      return lazyMap(s -> s.dropWhile(matcher));
    }

    @Override
    public <R> Lazy<S, E, R> map(Function<? super T, ? extends R> mapper) {
      return lazyMap(s -> s.map(mapper));
    }

    @Override
    public <R> Lazy<S, E, R> mapEval(Function<? super T, ? extends Program<S, E, R>> mapper) {
    return suspend(() -> program.map(s -> s.mapEval(mapper)));
    }

    @Override
    public <R> Lazy<S, E, R> flatMap(Function<? super T, ? extends Lazy<S, E, R>> mapper) {
      return lazyMap(s -> s.flatMap(mapper));
    }

    @Override
    public <R> Lazy<S, E, R> collect(Predicate<? super T> isDefined, Function<? super T, ? extends R> mapper) {
      return lazyMap(s -> s.collect(isDefined, mapper));
    }

    @Override
    public <R> Program<S, E, R> foldLeft(R begin, BiFunction<? super R, ? super T, ? extends R> combinator) {
      return program.flatMap(s -> s.foldLeft(begin, combinator));
    }

    @Override
    public <R> Program<S, E, R> foldRight(Program<S, E, R> begin,
        BiFunction<? super T, ? super Program<S, E, R>, ? extends Program<S, E, R>> combinator) {
      return program.flatMap(s -> s.foldRight(begin, combinator));
    }

    private <R> Lazy<S, E, R> lazyMap(Function<Lazy<S, E, T>, Lazy<S, E, R>> mapper) {
      return suspend(() -> program.map(mapper));
    }

    private <R> Lazy<S, E, R> suspend(Supplier<Program<S, E, Lazy<S, E, R>>> stream) {
      return new Suspend<>(Program.suspend(stream));
    }
  }

  Program<S, E, T> head();

  Lazy<S, E, T> tail();

  Program<S, E, Optional<T>> headOption();

  Program<S, E, Optional<Tuple2<Program<S, E, T>, Lazy<S, E, T>>>> split();

  Lazy<S, E, T> concat(Lazy<S, E, T> other);

  Lazy<S, E, T> append(Program<S, E, T> other);

  Lazy<S, E, T> prepend(Program<S, E, T> other);

  Lazy<S, E, T> take(int n);

  Lazy<S, E, T> drop(int n);

  Lazy<S, E, T> filter(Predicate<? super T> matcher);

  Lazy<S, E, T> takeWhile(Predicate<? super T> matcher);

  Lazy<S, E, T> dropWhile(Predicate<? super T> matcher);

  default Lazy<S, E, T> filterNot(Predicate<? super T> matcher) {
    return filter(matcher.negate());
  }

  <R> Lazy<S, E, R> map(Function<? super T, ? extends R> mapper);

  <R> Lazy<S, E, R> mapEval(Function<? super T, ? extends Program<S, E, R>> mapper);

  <R> Lazy<S, E, R> flatMap(Function<? super T, ? extends Lazy<S, E, R>> mapper);

  default <R> Lazy<S, E, R> andThen(Lazy<S, E, R> next) {
    return flatMap(_ -> next);
  }

  <R> Lazy<S, E, R> collect(Predicate<? super T> isDefined, Function<? super T, ? extends R> mapper);
  <R> Program<S, E, R> foldLeft(R begin, BiFunction<? super R, ? super T, ? extends R> combinator);
  <R> Program<S, E, R> foldRight(Program<S, E, R> begin,
      BiFunction<? super T, ? super Program<S, E, R>, ? extends Program<S, E, R>> combinator);

  private static <S, E, T, A> Lazy<S, E, T> unfold(@Nullable A seed, Function<? super A, Optional<Tuple2<T, A>>> function) {
    return suspend(() -> doUnfold(seed, function));
  }

  private static <S, E, T, A> Lazy<S, E, T> doUnfold(@Nullable A seed, Function<? super A, Optional<Tuple2<T, A>>> function) {
    return function.apply(seed)
      .<Lazy<S, E, T>>map(t -> nonEmpty(success(t.first()), suspend(() -> doUnfold(t.second(), function))))
      .orElseGet(Lazy::empty);
  }
}
