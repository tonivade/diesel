import jinja2

environment = jinja2.Environment()

finisher_template = environment.from_string("""/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel.function;

// generated code
@FunctionalInterface
public interface Finisher{{ value }}<{% for i in range(value) %}T{{ i }}, {% endfor %}R> {
 
  R apply({% for i in range(value) %}T{{ i }} t{{ i }}{% if i < value - 1 %}, {% endif %}{% endfor %});

  static <{% for i in range(value) %}T{{ i }}{% if i < value - 1 %}, {% endif %}{% endfor %}> Finisher{{ value }}<{% for i in range(value) %}T{{ i }}, {% endfor %}T0> first() {
    return (t0{% for i in range(value - 1) %}, _{% endfor %}) -> t0;
  }
  
  static <{% for i in range(value) %}T{{ i }}{% if i < value - 1 %}, {% endif %}{% endfor %}> Finisher{{ value }}<{% for i in range(value) %}T{{ i }}, {% endfor %}T{{ value -1 }}> last() {
    return ({% for i in range(value - 1) %}_, {% endfor %}t{{ value - 1}}) -> t{{ value - 1}};
  }
}
""")

result_zip_template = environment.from_string("""
/**
 * Combines the given results using the finisher function if all of them are successful.
 * <p>
 * If any of the results is a failure, the first failure by position is returned.
 *
{% for i in range(value) %} * @param r{{ i }} a result to combine
{% endfor %} * @param finisher the function used to combine the results
 * @param <F> the type of the failure
{% for i in range(value) %} * @param <T{{ i }}> the success type of {@code r{{ i }}}
{% endfor %} * @param <R> the type of the combined result
 * @return a new result with the combined result, or the first failure
 */
static <F, {% for i in range(value) %}T{{ i }}, {% endfor %}R> Result<F, R> zip(
  {% for i in range(value) %} Result<F, T{{ i }}> r{{ i }},
  {% endfor %} Finisher{{ value }}<{% for i in range(value) %}T{{ i }}, {% endfor %}R> finisher) {
  return {% for i in range(value - 1) %}r{{ i }}.flatMap(_{{ i }} -> 
    {% endfor %}
    r{{ value - 1 }}.map(_{{ value -1 }} -> finisher.apply({% for i in range(value) %}_{{ i }}{% if i < value - 1 %}, {% endif %}{% endfor %}))
    {% for i in range(value - 1) %}){% endfor %};
}
""")

program_zip_template = environment.from_string("""
/**
 * Executes the given programs sequentially and combines their results using the finisher function.
 * <p>
 * The execution stops at the first program that fails, and the resulting program fails with
 * that error. The programs after it are not executed.
 *
{% for i in range(value) %} * @param p{{ i }} a program to combine
{% endfor %} * @param finisher the function used to combine the results
 * @param <S> the type of the state
 * @param <E> the type of the error
{% for i in range(value) %} * @param <T{{ i }}> the success type of {@code p{{ i }}}
{% endfor %} * @param <R> the type of the combined result
 * @return a new program with the combined result, or the first failure
 */
static <S, E, {% for i in range(value) %}T{{ i }}, {% endfor %}R> Program<S, E, R> zip(
  {% for i in range(value) %} Program<S, E, T{{ i }}> p{{ i }},
  {% endfor %} Finisher{{ value }}<{% for i in range(value) %}T{{ i }}, {% endfor %}R> finisher) {
  return {% for i in range(value - 1) %}p{{ i }}.flatMap(_{{ i }} -> 
    {% endfor %}
    p{{ value - 1 }}.map(_{{ value - 1 }} -> finisher.apply({% for i in range(value) %}_{{ i }}{% if i < value - 1 %}, {% endif %}{% endfor %}))
    {% for i in range(value - 1) %}){% endfor %};
}
""")

program_parzip_template = environment.from_string("""
/**
 * Executes the given programs in parallel using the provided executor and combines their results
 * using the finisher function.
 *
 * <p>
 * The execution is fail-fast: as soon as any program fails, the remaining programs are
 * cancelled and the resulting program fails with that error. If several programs fail, the
 * error returned is the first one to happen in time, not the first by position. Cancelled
 * programs stop at their next step and run their finalizers, and the resulting program
 * completes once they have stopped. Cancellation is cooperative, so code that is already
 * running, like a blocking call inside {@code supply} or {@code task}, finishes first.
 *
{% for i in range(value) %} * @param p{{ i }} a program to be executed in parallel
{% endfor %} * @param finisher the function used to combine the results
 * @param executor the executor used to execute the programs in parallel
 * @param <S> the type of the state
 * @param <E> the type of the error
{% for i in range(value) %} * @param <T{{ i }}> the result type of {@code p{{ i }}}
{% endfor %} * @param <R> the type of the combined result
 * @return a new program representing the parallel computation
 */
static <S, E, {% for i in range(value) %}T{{ i }}, {% endfor %}R> Program<S, E, R> parZip(
  {% for i in range(value) %} Program<S, E, T{{ i }}> p{{ i }},
  {% endfor %} Finisher{{ value }}<{% for i in range(value) %}T{{ i }}, {% endfor %}R> finisher,
  Executor executor) {
    return zip(
      {% for i in range(value) %} p{{ i }}.fork(executor), 
      {% endfor %} ({% for i in range(value) %}f{{ i }}{% if i < value - 1 %}, {% endif %}{% endfor %}) -> {
        return parAllFailFast(List.of({% for i in range(value) %}f{{ i }}{% if i < value - 1 %}, {% endif %}{% endfor %}))
          .thenApply(result -> result.fold(
            Result::<E, R>failure,
            _ -> Result.zip({% for i in range(value) %}f{{ i }}.join(), {% endfor %}finisher)));
      })
      .flatMap(Program::from);
}
""")

program_parzip_forkjoin_template = environment.from_string("""
/**
 * Executes the given programs in parallel using the common fork-join pool and combines their results
 * using the finisher function.
 *
 * <p>
 * The execution is fail-fast: as soon as any program fails, the remaining programs are
 * cancelled and the resulting program fails with that error. If several programs fail, the
 * error returned is the first one to happen in time, not the first by position. Cancelled
 * programs stop at their next step and run their finalizers, and the resulting program
 * completes once they have stopped. Cancellation is cooperative, so code that is already
 * running, like a blocking call inside {@code supply} or {@code task}, finishes first.
 *
{% for i in range(value) %} * @param p{{ i }} a program to be executed in parallel
{% endfor %} * @param finisher the function used to combine the results
 * @param <S> the type of the state
 * @param <E> the type of the error
{% for i in range(value) %} * @param <T{{ i }}> the result type of {@code p{{ i }}}
{% endfor %} * @param <R> the type of the combined result
 * @return a new program representing the parallel computation
 */
static <S, E, {% for i in range(value) %}T{{ i }}, {% endfor %}R> Program<S, E, R> parZip(
  {% for i in range(value) %} Program<S, E, T{{ i }}> p{{ i }},
  {% endfor %} Finisher{{ value }}<{% for i in range(value) %}T{{ i }}, {% endfor %}R> finisher) {
    return parZip({% for i in range(value) %}p{{ i }}, {% endfor %}finisher, ForkJoinPool.commonPool());
}
""")

program_pipe_template = environment.from_string("""
/**
 * Executes the given program and passes its result to the next function, which returns
 * the next program to execute, and so on, returning the result of the last one.
 * <p>
 * Equivalent to chaining {@code flatMap} calls. The execution stops at the first program that
 * fails, and the resulting program fails with that error.
 *
 * @param p0 the program to execute first
{% for i in range(value - 1) %} * @param p{{ i + 1 }} the function that receives the result of the previous step and returns the next program
{% endfor %} * @param <S> the type of the state
 * @param <E> the type of the error
 * @param <T0> the result type of {@code p0}
{% for i in range(value - 1) %} * @param <T{{ i + 1 }}> the result type of step {@code p{{ i + 1 }}}
{% endfor %} * @return a new program with the result of the last step
 */
static <S, E, {% for i in range(value) %}T{{ i }}{% if i < value - 1 %}, {% endif %}{% endfor %}> Program<S, E, T{{ value - 1}}> pipe(
  Program<S, E, T0> p0,
  {% for i in range(value - 1) %} Function<? super T{{ i }}, ? extends Program<S, E, T{{ i + 1 }}>> p{{ i + 1 }}{% if i < value - 2 %},{% endif %}
  {% endfor %}) {
    return p0{% for i in range(value - 1) %}.flatMap(p{{ i + 1}}){% endfor %};
}
""")

program_chain_template = environment.from_string("""
/**
 * Executes the given program and transforms its result by applying the given functions
 * in order, each one receiving the result of the previous one.
 * <p>
 * Equivalent to chaining {@code map} calls. If the program fails, the functions are not applied
 * and the resulting program fails with that error.
 *
 * @param p0 the program to execute first
{% for i in range(value - 1) %} * @param p{{ i + 1 }} the function that transforms the result of the previous step
{% endfor %} * @param <S> the type of the state
 * @param <E> the type of the error
 * @param <T0> the result type of {@code p0}
{% for i in range(value - 1) %} * @param <T{{ i + 1 }}> the result type of step {@code p{{ i + 1 }}}
{% endfor %} * @return a new program with the result of the last step
 */
static <S, E, {% for i in range(value) %}T{{ i }}{% if i < value - 1 %}, {% endif %}{% endfor %}> Program<S, E, T{{ value - 1}}> chain(
  Program<S, E, T0> p0,
  {% for i in range(value - 1) %} Function<? super T{{ i }}, ? extends T{{ i + 1 }}> p{{ i + 1 }}{% if i < value - 2 %},{% endif %}
  {% endfor %}) {
    return p0{% for i in range(value - 1) %}.map(p{{ i + 1}}){% endfor %};
}
""")

for i in range(2, 10):
  with open(f"src/main/java/com/github/tonivade/diesel/function/Finisher{i}.java", 'w') as file:
    file.write(finisher_template.render(value=i))

print(">>>> result zip")
for i in range(2, 10):
  print(result_zip_template.render(value=i))

print(">>>> program zip")
for i in range(2, 10):
  print(program_zip_template.render(value=i))

print(">>>> program parzip")
for i in range(2, 10):
  print(program_parzip_template.render(value=i))

print(">>>> program parzip fork join")
for i in range(2, 10):
  print(program_parzip_forkjoin_template.render(value=i))

print(">>>> program pipe")
for i in range(2, 10):
  print(program_pipe_template.render(value=i))

print(">>>> program chain")
for i in range(2, 10):
  print(program_chain_template.render(value=i))