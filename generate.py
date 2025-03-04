import jinja2

environment = jinja2.Environment()

finisher_template = environment.from_string("""/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel.function;

public interface Finisher{{ value }}<{% for i in range(value) %}T{{ i }}, {% endfor %}R> {
 
  R apply({% for i in range(value) %}T{{ i }} t{{ i }}{% if i < value - 1 %}, {% endif %}{% endfor %});

}
""")

program_map_template = environment.from_string("""
static <S, E, {% for i in range(value) %}T{{ i }}, {% endfor %}R> Program<S, E, R> map{{ value }}(
  {% for i in range(value) %} Program<S, E, T{{ i }}> p{{ i }},
  {% endfor %} Finisher{{ value }}<{% for i in range(value) %}T{{ i }}, {% endfor %}R> finisher) {
    return async((state, callback) -> {
      try {
        callback.accept(Result.map{{ value }}({% for i in range(value) %}p{{ i }}.eval(state), {% endfor %}finisher), null);
      } catch (RuntimeException e) {
        callback.accept(null, e);
      }
    });
}
""")

program_parmap_template = environment.from_string("""
static <S, E, {% for i in range(value) %}T{{ i }}, {% endfor %}R> Program<S, E, R> parMap{{ value }}(
  {% for i in range(value) %} Program<S, E, T{{ i }}> p{{ i }},
  {% endfor %} Finisher{{ value }}<{% for i in range(value) %}T{{ i }}, {% endfor %}R> finisher,
  Executor executor) {
    return map{{ value }}(
      {% for i in range(value) %} p{{ i }}.fork(executor), 
      {% endfor %} ({% for i in range(value) %}f{{ i }}{% if i < value - 1 %}, {% endif %}{% endfor %}) -> Fiber.map{{ value }}({% for i in range(value) %}f{{ i }}, {% endfor %}finisher))
      .flatMap(Fiber::join);
}
""")

result_template = environment.from_string("""
static <F, {% for i in range(value) %}T{{ i }}, {% endfor %}R> Result<F, R> map{{ value }}(
  {% for i in range(value) %} Result<F, T{{ i }}> r{{ i }},
  {% endfor %} Finisher{{ value }}<{% for i in range(value) %}T{{ i }}, {% endfor %}R> finisher) {
  return {% for i in range(value - 1) %}r{{ i }}.flatMap(_{{ i }} -> 
    {% endfor %}
    r{{ value - 1 }}.map(_{{ value -1 }} -> finisher.apply({% for i in range(value) %}_{{ i }}{% if i < value - 1 %}, {% endif %}{% endfor %}))
    {% for i in range(value - 1) %}){% endfor %};
}
""")

fiber_template = environment.from_string("""
public static <E, {% for i in range(value) %}T{{ i }}, {% endfor %}R> Fiber<E, R> map{{ value }}(
  {% for i in range(value) %} Fiber<E, T{{ i }}> f{{ i }},
  {% endfor %} Finisher{{ value }}<{% for i in range(value) %}T{{ i }}, {% endfor %}R> finisher) {
  var result = {% for i in range(value - 1) %}f{{ i }}.future.thenComposeAsync(_{{ i }} -> 
    {% endfor %}
    f{{ value - 1 }}.future.thenApplyAsync(_{{ value -1 }} -> Result.map{{ value }}({% for i in range(value) %}_{{ i }}, {% endfor %}finisher))
    {% for i in range(value - 1) %}){% endfor %};
  return new Fiber<>(result);
}
""")

for i in range(2, 10):
  with open(f"src/main/java/com/github/tonivade/diesel/function/Finisher{i}.java", 'w') as file:
    file.write(finisher_template.render(value=i))

print(">>>> result")
for i in range(2, 10):
  print(result_template.render(value=i))

print(">>>> program map")
for i in range(2, 10):
  print(program_map_template.render(value=i))

print(">>>> fiber")
for i in range(2, 10):
  print(fiber_template.render(value=i))

print(">>>> program parmap")
for i in range(2, 10):
  print(program_parmap_template.render(value=i))