/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Program.failure;
import static com.github.tonivade.diesel.Program.supply;
import static com.github.tonivade.diesel.Result.success;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class LazyTest {

  @Test
  void readFileIO() {
    var file = readFile("LICENSE");

    assertEquals(success(impureReadFile("LICENSE")), file.eval());
  }

  private Program<Void, String, String> readFile(String file) {
    return Lazy.supply(() -> reader(file))
      .flatMap(reader -> Lazy.iterate(() -> Optional.ofNullable(readLine(reader))))
      .takeWhile(Optional::isPresent)
      .map(Optional::orElseThrow)
      .foldLeft("", (a, b) -> a + '\n' + b)
      .catchAll(_ -> failure("--- file not found ---"));
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

  private String readLine(BufferedReader reader) {
    try {
      return reader.readLine();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private String impureReadFile(String file) {
    String content = "";
    try (BufferedReader reader = Files.newBufferedReader(Paths.get(file))) {
      while (true) {
        String line = readLine(reader);
        if (line != null) {
          content = content + "\n" + line;
        } else {
          break;
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return content;
  }
}
