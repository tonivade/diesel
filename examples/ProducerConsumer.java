///usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS com.github.tonivade:diesel:0.14-SNAPSHOT

/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package examples;

import static com.github.tonivade.diesel.Combine.pipe;
import static com.github.tonivade.diesel.Combine.traverse;
import static com.github.tonivade.diesel.Concurrent.parAll;
import static com.github.tonivade.diesel.Program.success;
import static com.github.tonivade.diesel.Program.suspend;
import static com.github.tonivade.diesel.Program.task;
import static com.github.tonivade.diesel.Tuple2.tuple;
import static com.github.tonivade.diesel.Unit.unit;

import com.github.tonivade.diesel.Deferred;
import com.github.tonivade.diesel.Program;
import com.github.tonivade.diesel.Ref;
import com.github.tonivade.diesel.Unit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ProducerConsumer {

  // ---------------------------------------------------------------------------
  // Queue
  // ---------------------------------------------------------------------------

  static final class SignalQueue<S, E, A> {

    private record State<S, E, A>(List<A> values, Deferred<S, E, Unit> changed) {}
    private record Poll<S, E, A>(Optional<A> value, Deferred<S, E, Unit> changed) {}

    private final Ref<S, E, State<S, E, A>> state;

    private SignalQueue(Ref<S, E, State<S, E, A>> state) {
      this.state = state;
    }

    static <S, E, A> Program<S, E, SignalQueue<S, E, A>> make() {
      return suspend(() ->
          pipe(
              Ref.<S, E, State<S, E, A>>make(
                  new State<>(
                      List.of(),
                      Deferred.<S, E, Unit>create()
                  )
              ),
              success(state -> new SignalQueue<>(state))
          )
      );
    }

    Program<S, E, Unit> offer(A value) {
      Objects.requireNonNull(value);

      return suspend(() -> {
        var nextSignal = Deferred.<S, E, Unit>create();

        return pipe(
            state.modify(current -> {
              var values = new ArrayList<>(current.values());
              values.add(value);

              return tuple(
                  current.changed(),
                  new State<>(List.copyOf(values), nextSignal)
              );
            }),
            previous -> previous.complete(unit()).discard()
        );
      });
    }

    Program<S, E, A> take() {
      return pipe(
          state.modify(current -> {
            if (current.values().isEmpty()) {
              return tuple(
                  new Poll<S, E, A>(
                      Optional.empty(),
                      current.changed()
                  ),
                  current
              );
            }

            A value = current.values().getFirst();

            return tuple(
                new Poll<>(
                    Optional.of(value),
                    current.changed()
                ),
                new State<>(
                    List.copyOf(current.values().subList(
                        1, current.values().size()
                    )),
                    current.changed()
                )
            );
          }),
          poll -> poll.value()
              .map(Program::<S, E, A>success)
              .orElseGet(() -> poll.changed().get().andThen(suspend(this::take)))
      );
    }
  }

  // ---------------------------------------------------------------------------
  // Messages
  // ---------------------------------------------------------------------------

  record Message(int value, boolean poison) {

    static Message data(int value) {
      return new Message(value, false);
    }

    static Message stop() {
      return new Message(0, true);
    }
  }

  // ---------------------------------------------------------------------------
  // Producers
  // ---------------------------------------------------------------------------

  static Program<Void, Void, Unit> produce(
      SignalQueue<Void, Void, Message> queue,
      List<Integer> values
  ) {
    return traverse(
        value -> queue.offer(Message.data(value)),
        values
    ).discard();
  }

  // ---------------------------------------------------------------------------
  // Consumers
  // ---------------------------------------------------------------------------

  static Program<Void, Void, Unit> consume(
      int id,
      SignalQueue<Void, Void, Message> queue,
      Ref<Void, Void, Integer> processed
  ) {
    return suspend(() ->
        pipe(
            queue.take(),
            message -> {
              if (message.poison()) {
                return task(() -> System.out.printf("Consumer %d stopped%n", id));
              }

              return pipe(
                  task(() ->
                      System.out.printf(
                          "Consumer %d processed %d%n",
                          id,
                          message.value()
                      )
                  ),
                  _ -> processed.updateAndGet(n -> n + 1),
                  _ -> suspend(() -> consume(id, queue, processed))
              );
            }
        )
    );
  }

  // ---------------------------------------------------------------------------
  // Shutdown
  // ---------------------------------------------------------------------------

  static Program<Void, Void, Unit> stopWorkers(SignalQueue<Void, Void, Message> queue, int count) {
    if (count == 0) {
      return Program.unit();
    }
    return pipe(
        queue.offer(Message.stop()),
        _ -> suspend(() -> stopWorkers(queue, count - 1))
    );
  }

  // ---------------------------------------------------------------------------
  // Program
  // ---------------------------------------------------------------------------

  static Program<Void, Void, Integer> program() {
    return pipe(
        SignalQueue.<Void, Void, Message>make(),
        queue -> pipe(
            Ref.make(0),
            processed -> pipe(
                parAll(
                    pipe(
                        parAll(
                            produce(queue, List.of(1, 2, 3, 4, 5)),
                            produce(queue, List.of(6, 7, 8, 9, 10))
                        ),
                        _ -> stopWorkers(queue, 3)
                    ),
                    consume(1, queue, processed),
                    consume(2, queue, processed),
                    consume(3, queue, processed)
                ),
                _ -> processed.get()
            )
        )
    );
  }

  // ---------------------------------------------------------------------------
  // Main
  // ---------------------------------------------------------------------------

  public static void main(String[] args) {
    int total = program().evalOrElseThrow();

    System.out.println();
    System.out.println("================================");
    System.out.println("Processed messages: " + total);
    System.out.println("================================");

    if (total != 10) {
      throw new AssertionError(
          "Expected 10 processed messages, but got " + total
      );
    }
  }
}