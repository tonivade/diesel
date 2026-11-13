///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS com.github.tonivade:diesel:0.14

/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package examples;

import static com.github.tonivade.diesel.Combine.pipe;
import static com.github.tonivade.diesel.Program.attempt;
import static com.github.tonivade.diesel.Program.success;

import com.github.tonivade.diesel.Program;
import com.github.tonivade.diesel.Resource;
import com.github.tonivade.diesel.Source;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Demonstrates resource management by reading a file lazily with Source
 * and ensuring the reader is closed when processing completes.
 */
public class ResourceExample {
  
  public static void main(String... args) {
    Path path = args.length > 0
        ? Path.of(args[0])
        : Path.of("README.md");

    List<String> lines = readLines(path).evalOrElseThrow();

    IO.println("Read " + lines.size() + " lines:");
    lines.forEach(IO::println);
  }

  static Program<Void, Throwable, List<String>> readLines(Path path) {
    return Resource.<Void, Throwable, BufferedReader>from(
        attempt(() -> Files.newBufferedReader(path))
    ).use(reader ->
        Source.<Void, Throwable, String>fromStream(reader::lines)
            .take(10)
            .toList()
    );
  }
}
