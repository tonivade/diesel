/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Console.prompt;
import static com.github.tonivade.diesel.Console.readLine;
import static com.github.tonivade.diesel.Console.writeLine;
import static com.github.tonivade.diesel.Counter.increment;
import static com.github.tonivade.diesel.Program.failure;
import static com.github.tonivade.diesel.Program.pipe;
import static com.github.tonivade.diesel.Program.success;
import static com.github.tonivade.diesel.Program.zip;
import static com.github.tonivade.diesel.Todo.State.COMPLETED;
import static com.github.tonivade.diesel.Todo.State.NOT_COMPLETED;
import static java.util.stream.Collectors.joining;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

sealed interface Todo<T> extends Program.Dsl<Todo.Repository, Todo.Error, T> {

  sealed interface Error {}

  record NumberFormatError(String input) implements Error {}
  record InvalidOption(int action) implements Error {}

  interface Repository {
    void create(TodoEntity todo);
    void update(int id, UnaryOperator<TodoEntity> update);
    Optional<TodoEntity> find(int id);
    List<TodoEntity> findAll();
    void delete(int id);
    void deleteAll();
  }

  enum State {
    NOT_COMPLETED, COMPLETED
  }

  record TodoEntity(int id, String title, State state) {
    TodoEntity withState(State state) {
      return new TodoEntity(id, title, state);
    }
  }

  record Create(TodoEntity todo) implements Todo<Void> {}
  record Update(int id, UnaryOperator<TodoEntity> update) implements Todo<Void> {}
  record FindOne(int id) implements Todo<Optional<TodoEntity>> {}
  record FindAll() implements Todo<List<TodoEntity>> {}
  record DeleteOne(int id) implements Todo<Void> {}
  record DeleteAll() implements Todo<Void> {}

  @SuppressWarnings("unchecked")
  static <S extends Repository, E extends Error> Program<S, E, Void> create(TodoEntity todo) {
    return (Program<S, E, Void>) new Create(todo);
  }

  @SuppressWarnings("unchecked")
  static <S extends Repository, E extends Error> Program<S, E, Void> update(int id, UnaryOperator<TodoEntity> update) {
    return (Program<S, E, Void>) new Update(id, update);
  }

  @SuppressWarnings("unchecked")
  static <S extends Repository, E extends Error> Program<S, E, Optional<TodoEntity>> findOne(int id) {
    return (Program<S, E, Optional<TodoEntity>>) new FindOne(id);
  }

  @SuppressWarnings("unchecked")
  static <S extends Repository, E extends Error> Program<S, E, List<TodoEntity>> findAll() {
    return (Program<S, E, List<TodoEntity>>) new FindAll();
  }

  @SuppressWarnings("unchecked")
  static <S extends Repository, E extends Error> Program<S, E, Void> deleteOne(int id) {
    return (Program<S, E, Void>) new DeleteOne(id);
  }

  @SuppressWarnings("unchecked")
  static <S extends Repository, E extends Error> Program<S, E, Void> deleteAll() {
    return (Program<S, E, Void>) new DeleteAll();
  }

  @Override
  @SuppressWarnings("unchecked")
  default Result<Error, T> handle(Repository repository) {
    return Result.success((T) switch (this) {
      case Create(TodoEntity todo) -> {
        repository.create(todo);
        yield null;
      }
      case Update(int id, UnaryOperator<TodoEntity> update) -> {
        repository.update(id, update);
        yield null;
      }
      case FindOne(int id) -> repository.find(id);
      case FindAll() -> repository.findAll();
      case DeleteOne(int id) -> {
        repository.delete(id);
        yield null;
      }
      case DeleteAll() -> {
        repository.deleteAll();
        yield null;
      }
    });
  }

  static Program<Context, Error, Void> program() {
    return printMenu().flatMap(Todo::executeAction);
  }

  static Program<Context, Error, Integer> printMenu() {
    return Console.<Context, Error>writeLine("Menu")
      .andThen(writeLine("1. Create"))
      .andThen(writeLine("2. List"))
      .andThen(writeLine("3. Find"))
      .andThen(writeLine("4. Delete"))
      .andThen(writeLine("5. Clear"))
      .andThen(writeLine("6. Completed"))
      .andThen(writeLine("7. Exit"))
      .andThen(readLine())
      .flatMap(Todo::parseInt)
      .recover(__ -> printMenu());
  }

  static Program<Context, Error, Void> executeAction(int action) {
    return switch (action) {
      case 1 -> createTodo();
      case 2 -> findAllTodos();
      case 3 -> findTodo();
      case 4 -> deleteTodo();
      case 5 -> deleteAllTodos();
      case 6 -> markCompleted();
      case 7 -> writeLine("Bye!");
      default -> failure(new InvalidOption(action));
    };
  }

  static Program<Context, Error, Void> findAllTodos() {
    return pipe(
        findAll(),
        list -> success(list.stream().map(Object::toString).collect(joining("\n"))),
        Console::writeLine,
        __ -> program());
  }

  static Program<Context, Error, Void> deleteAllTodos() {
    return pipe(
        deleteAll(),
        __ -> writeLine("all todo removed"),
        __ -> program());
  }

  static Program<Context, Error, Void> createTodo() {
    return pipe(
        zip(increment(), prompt("Enter title"), (id, title) -> new TodoEntity(id, title, NOT_COMPLETED)),
        Todo::create,
        __ -> writeLine("todo created"),
        __ -> program());
  }

  static Program<Context, Error, Void> deleteTodo() {
    return pipe(
        promptId(),
        Todo::deleteOne,
        __ -> writeLine("todo removed"),
        __ -> program());
  }

  static Program<Context, Error, Void> findTodo() {
    return pipe(
        promptId(),
        Todo::findOne,
        optional -> success(optional.map(Object::toString).orElse("not found")),
        Console::writeLine,
        __ -> program());
  }

  static Program<Context, Error, Void> markCompleted() {
    return pipe(
        promptId(),
        id -> update(id, entity -> entity.withState(COMPLETED)),
        __ -> writeLine("todo completed"),
        __ -> program());
  }

  static Program<Context, Error, Integer> promptId() {
    return pipe(
        prompt("Enter id"),
        Todo::parseInt,
        __ -> promptId()).recover(__ -> promptId());
  }

  static Program<Context, Error, Integer> parseInt(String value) {
    try {
      return success(Integer.parseInt(value));
    } catch (NumberFormatException e) {
      return failure(new NumberFormatError(value));
    }
  }

  static void main(String... args) {
    program().eval(new Context(){});
  }

  abstract class Context implements Todo.Repository, Console.Service, Counter.Service<Integer> {

    private final AtomicInteger counter = new AtomicInteger();
    private final Map<Integer, TodoEntity> repository = new HashMap<>();

    @Override
    public Integer increment() {
      return counter.incrementAndGet();
    }

    @Override
    public Integer decrement() {
      return counter.decrementAndGet();
    }

    @Override
    public void create(TodoEntity todo) {
      repository.put(todo.id(), todo);
    }

    @Override
    public void update(int id, UnaryOperator<TodoEntity> update) {
      var todo = repository.get(id);
      repository.put(id, update.apply(todo));
    }

    @Override
    public Optional<TodoEntity> find(int id) {
      return Optional.ofNullable(repository.get(id));
    }

    @Override
    public List<TodoEntity> findAll() {
      return List.copyOf(repository.values());
    }

    @Override
    public void delete(int id) {
      repository.remove(id);
    }

    @Override
    public void deleteAll() {
      repository.clear();
    }
  }
}
