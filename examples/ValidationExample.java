///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS com.github.tonivade:diesel:0.14-SNAPSHOT

/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package examples;

import static com.github.tonivade.diesel.Program.validate;
import static com.github.tonivade.diesel.Validation.valid;
import static com.github.tonivade.diesel.Validation.invalid;

import com.github.tonivade.diesel.Program;
import com.github.tonivade.diesel.Validation;
import com.github.tonivade.diesel.Validator;

import java.util.Collection;

public class ValidationExample {

  public static void main(String... args) {
    var form = new RegistrationForm(
        args.length > 0 ? args[0] : "",
        args.length > 1 ? args[1] : "invalid-email",
        args.length > 2 ? args[2] : "123"
    );

    var result = validateForm(form).eval();

    IO.println("Registration result: " + result);
  }

  /**
   * Validates all form fields and accumulates every validation error.
   */
  static Program<Void, Collection<String>, RegistrationForm> validateForm(
      RegistrationForm form) {
    return validate(
        form,
        Validator.of(RegistrationForm::name, ValidationExample::validateName),
        Validator.of(RegistrationForm::email, ValidationExample::validateEmail),
        Validator.of(RegistrationForm::password, ValidationExample::validatePassword)
      );
  }

  private static Validation<String> validateName(String name) {
    if (name == null || name.isBlank()) {
      return invalid("Name is required");
    }
    return valid();
  }

  private static Validation<String> validateEmail(String email) {
    if (email == null || !email.contains("@")) {
      return invalid("Email address is invalid");
    }
    return valid();
  }

  private static Validation<String> validatePassword(String password) {
    if (password == null || password.length() < 8) {
      return invalid("Password must contain at least 8 characters");
    }
    return valid();
  }

  private record RegistrationForm(
      String name,
      String email,
      String password
  ) {}
}
