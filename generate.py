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

program_template = environment.from_string("""
static <S, E, {% for i in range(value) %}T{{ i }}, {% endfor %}R> Program<S, E, R> map{{ value }}(
  {% for i in range(value) %} Program<S, E, T{{ i }}> p{{ i }},
  {% endfor %} Finisher{{ value }}<{% for i in range(value) %}T{{ i }}, {% endfor %}R> finisher
  ) {
    return async((state, callback) -> {
      try {
        callback.accept(Result.map{{ value }}({% for i in range(value) %}p{{ i }}.eval(state), {% endfor %}finisher), null);
      } catch (RuntimeException e) {
        callback.accept(null, e);
      }
    });
}
""")

result_template = environment.from_string("""
static <F, {% for i in range(value) %}T{{ i }}, {% endfor %}R> Result<F, R> map{{ value }}(
  {% for i in range(value) %} Result<F, T{{ i }}> r{{ i }},
  {% endfor %} Finisher{{ value }}<{% for i in range(value) %}T{{ i }}, {% endfor %}R> finisher
  ) {
  return {% for i in range(value - 1) %}r{{ i }}.flatMap(_{{ i }} -> 
    {% endfor %}
    r{{ value - 1 }}.map(_{{ value -1 }} -> finisher.apply({% for i in range(value) %}_{{ i }}{% if i < value - 1 %}, {% endif %}{% endfor %}))
    {% for i in range(value - 1) %}){% endfor %};
}
""")

for i in range(2, 10):
  with open(f"src/main/java/com/github/tonivade/diesel/function/Finisher{i}.java", 'w') as file:
    file.write(finisher_template.render(value=i))

print(">>>> program")
for i in range(2, 10):
  print(program_template.render(value=i))

print(">>>> result")
#for i in range(2, 10):
#  print(result_template.render(value=i))