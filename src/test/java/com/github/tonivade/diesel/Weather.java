/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Console.prompt;
import static com.github.tonivade.diesel.Console.writeLine;
import static com.github.tonivade.diesel.Logger.info;
import static com.github.tonivade.diesel.Logger.warn;
import static com.github.tonivade.diesel.Program.pipe;
import static com.github.tonivade.diesel.Program.success;
import static com.github.tonivade.diesel.Random.nextInt;
import static java.lang.System.getLogger;
import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.INFO;
import static java.lang.System.Logger.Level.WARNING;
import static java.util.Comparator.comparingInt;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

sealed interface Weather<T> extends Program.Dsl<Weather.Service, Weather.Error, T> {

  interface Service {
    Config readConfig();
    Optional<Forecast> getForecast(City city);
    void setForecast(City city, Forecast forecast);
    Optional<City> hottestCity();
  }

  sealed interface Error {}

  record UnknownCity(String name) implements Error {}

  record Forecast(int temperature) {}
  record City(String name) {}
  record CityForecast(City city, Forecast forecast) {}

  record Config(String host, int port) {}

  record ReadConfig() implements Weather<Config> {}
  record GetForecast(City city) implements Weather<Optional<Forecast>> {}
  record SetForecast(City city, Forecast forecast) implements Weather<Void> {}
  record HottestCity() implements Weather<Optional<City>> {}

  @SuppressWarnings("unchecked")
  static <S extends Service, E extends Error> Program<S, E, Config> readConfig() {
    return (Program<S, E, Config>) new ReadConfig();
  }

  @SuppressWarnings("unchecked")
  static <S extends Service, E extends Error> Program<S, E, Optional<Forecast>> getForecast(City city) {
    return (Program<S, E, Optional<Forecast>>) new GetForecast(city);
  }

  @SuppressWarnings("unchecked")
  static <S extends Service, E extends Error> Program<S, E, Void> setForecast(City city, Forecast forecast) {
    return (Program<S, E, Void>) new SetForecast(city, forecast);
  }

  @SuppressWarnings("unchecked")
  static <S extends Service, E extends Error> Program<S, E, Optional<City>> hottestCity() {
    return (Program<S, E, Optional<City>>) new HottestCity();
  }

  @Override
  @SuppressWarnings("unchecked")
  default Result<Error, T> handle(Service service) {
    return Result.success((T) switch (this) {
      case ReadConfig() -> service.readConfig();
      case GetForecast(City city) -> service.getForecast(city);
      case SetForecast(City city, Forecast forecast) -> {
        service.setForecast(city, forecast);
        yield null;
      }
      case HottestCity() -> service.hottestCity();
    });
  }

  public static void main(String[] args) {
    program().eval(new Context());
  }

  static Program<Context, Error, Void> program() {
    return hostAndPort().andThen(loop());
  }

  static Program<Context, Error, Void> loop() {
    return askAndFetchAndPrint()
        .andThen(printHottestCity())
        .foldMap(Weather::printError, _ -> loop());
  }

  static Program<Context, Error, Void> askAndFetchAndPrint() {
    return askCity()
        .peekError(error -> warn(": " + error))
        .peek(city -> info("getting city forecast: " + city))
        .flatMap(Weather::fetchForecast)
        .peek(cityForecast -> info("forecast received: " + cityForecast))
        .flatMap(Weather::printForecastAndPersist);
  }

  static Program<Context, Error, Void> printHottestCity() {
    return pipe(
        hottestCity(),
        city -> writeLine("Hottest city so far: " + city.map(City::name).orElse("None")));
  }

  static Program<Context, Error, Void> printError(Error error) {
    return pipe(
        writeLine("Error: " + error),
        _ -> loop());
  }

  static Program<Context, Error, City> askCity() {
    return pipe(prompt("What city?"), Weather::cityByName);
  }

  static Program<Context, Error, CityForecast> fetchForecast(City city) {
    return pipe(
        getForecast(city),
        forecast -> forecast.map(Program::<Context, Error, Forecast>success).orElseGet(Weather::forecast),
        forecast -> success(new CityForecast(city, forecast)));
  }

  static Program<Context, Error, Void> printForecastAndPersist(CityForecast cityForecast) {
    return pipe(
        writeLine("Forecast for city " + cityForecast.city() + " is " + cityForecast.forecast()),
        _ -> setForecast(cityForecast.city(), cityForecast.forecast()));
  }

  static Program<Context, Error, Forecast> forecast() {
    return pipe(nextInt(30), temperature -> success(new Forecast(temperature)));
  }

  static Program<Context, Error, City> cityByName(String name) {
    return switch (name) {
      case "Madrid", "Getafe", "Elche" -> Program.success(new City(name));
      default -> Program.failure(new UnknownCity(name));
    };
  }

  static Program<Context, Error, Void> hostAndPort() {
    return pipe(
        readConfig(),
        config -> writeLine("conecting to " + config));
  }

  final class Context implements Service, Console.Service, Random.Service, Logger.Service {

    private final Map<City, Forecast> map = new HashMap<>();

    @Override
    public Config readConfig() {
      return new Config("localhost", 8080);
    }

    @Override
    public Optional<Forecast> getForecast(City city) {
      return Optional.ofNullable(map.get(city));
    }

    @Override
    public void setForecast(City city, Forecast forecast) {
      map.put(city, forecast);
    }

    @Override
    public Optional<City> hottestCity() {
      return map.entrySet().stream()
        .max(comparingInt(entry -> entry.getValue().temperature()))
        .map(Map.Entry::getKey);
    }

    @Override
    public void info(String message) {
      getLogger("weather").log(INFO, message);
    }

    @Override
    public void warn(String message) {
      getLogger("weather").log(WARNING, message);
    }

    @Override
    public void error(String message, Throwable error) {
      getLogger("weather").log(ERROR, message);
    }
  }
}
