package com.github.tonivade.diesel;

import static java.util.stream.Collectors.joining;
import static com.github.tonivade.diesel.Console.prompt;
import static com.github.tonivade.diesel.Console.readLine;
import static com.github.tonivade.diesel.Console.writeLine;
import static com.github.tonivade.diesel.Counter.increment;
import static com.github.tonivade.diesel.Program.map2;
import static com.github.tonivade.diesel.Todo.State.COMPLETED;
import static com.github.tonivade.diesel.Todo.State.NOT_COMPLETED;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

sealed interface Todo<T> extends Program.Dsl<Todo.Repository, T> {

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
  static <S extends Repository> Program<S, Void> create(TodoEntity todo) {
    return (Program<S, Void>) new Create(todo);
  }

  @SuppressWarnings("unchecked")
  static <S extends Repository> Program<S, Void> update(int id, UnaryOperator<TodoEntity> update) {
    return (Program<S, Void>) new Update(id, update);
  }

  @SuppressWarnings("unchecked")
  static <S extends Repository> Program<S, Optional<TodoEntity>> findOne(int id) {
    return (Program<S, Optional<TodoEntity>>) new FindOne(id);
  }

  @SuppressWarnings("unchecked")
  static <S extends Repository> Program<S, List<TodoEntity>> findAll() {
    return (Program<S, List<TodoEntity>>) new FindAll();
  }

  @SuppressWarnings("unchecked")
  static <S extends Repository> Program<S, Void> deleteOne(int id) {
    return (Program<S, Void>) new DeleteOne(id);
  }

  @SuppressWarnings("unchecked")
  static <S extends Repository> Program<S, Void> deleteAll() {
    return (Program<S, Void>) new DeleteAll();
  }

  @Override
  @SuppressWarnings("unchecked")
  default T eval(Repository repository) {
    return (T) switch (this) {
      case Create(TodoEntity todo) -> {
        repository.create(todo);
        yield null;
      }
      case Update(int id, UnaryOperator<TodoEntity> update) -> {
        repository.update(id, update);
        yield null;
      }
      case FindOne(int id) -> repository.find(id);
      case FindAll _ -> repository.findAll();
      case DeleteOne(int id) -> {
        repository.delete(id);
        yield null;
      }
      case DeleteAll _ -> {
        repository.deleteAll();
        yield null;
      }
    };
  }

  static Program<TodoContext, Integer> printMenu() {
    return Console.<TodoContext>writeLine("Menu")
      .andThen(writeLine("1. Create"))
      .andThen(writeLine("2. List"))
      .andThen(writeLine("3. Find"))
      .andThen(writeLine("4. Delete"))
      .andThen(writeLine("5. Clear"))
      .andThen(writeLine("6. Completed"))
      .andThen(writeLine("7. Exit"))
      .andThen(readLine())
      .map(Integer::parseInt);
  }

  static Program<TodoContext, Void> executeAction(int action) {
    return switch (action) {
      case 1 -> createTodo();
      case 2 -> findAllTodos();
      case 3 -> findTodo();
      case 4 -> deleteTodo();
      case 5 -> deleteAllTodos();
      case 6 -> markCompleted();
      case 7 -> writeLine("Bye!");
      default -> throw new IllegalArgumentException();
    };
  }

  static Program<TodoContext, String> promptTitle() {
    return prompt("Enter title");
  }

  static Program<TodoContext, Integer> promptId() {
    return Console.<TodoContext>prompt("Enter id").map(Integer::parseInt);
  }

  static Program<TodoContext, Void> findAllTodos() {
    return Todo.<TodoContext>findAll()
      .map(list -> list.stream().map(Object::toString).collect(joining("\n")))
      .flatMap(Console::writeLine)
      .andThen(loop());
  }

  static Program<TodoContext, Void> deleteAllTodos() {
    return Todo.<TodoContext>deleteAll()
      .andThen(writeLine("all todo removed"))
      .andThen(loop());
  }

  static Program<TodoContext, Void> createTodo() {
    return map2(increment(), promptTitle(),
        (id, title) -> new TodoEntity(id, title, NOT_COMPLETED))
      .flatMap(Todo::create)
      .andThen(writeLine("todo created"))
      .andThen(loop());
  }

  static Program<TodoContext, Void> deleteTodo() {
    return promptId()
      .flatMap(Todo::deleteOne)
      .andThen(writeLine("todo removed"))
      .andThen(loop());
  }

  static Program<TodoContext, Void> findTodo() {
    return promptId()
      .flatMap(Todo::findOne)
      .map(optional -> optional.map(Object::toString).orElse("not found"))
      .flatMap(Console::writeLine)
      .andThen(loop());
  }

  static Program<TodoContext, Void> markCompleted() {
    return promptId()
      .flatMap(id -> update(id, entity -> entity.withState(COMPLETED)))
      .andThen(writeLine("todo compmleted"))
      .andThen(loop());
  }

  static Program<TodoContext, Void> loop() {
    return printMenu().flatMap(Todo::executeAction);
  }

  static void main(String... args) {
    var program = Console.<TodoContext>whatsYourName()
      .flatMap(Console::sayHello)
      .andThen(loop());

    program.eval(new TodoContext());
  }

  final class TodoContext implements Todo.Repository, Console.Service, Counter.Service {

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
