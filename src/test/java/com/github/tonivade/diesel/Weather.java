/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Console.writeLine;
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
  default Result<Error, T> eval(Service service) {
    var result = (T) switch (this) {
      case ReadConfig _ -> service.readConfig();
      case GetForecast(City city) -> service.getForecast(city);
      case SetForecast(City city, Forecast forecast) -> {
        service.setForecast(city, forecast);
        yield null;
      }
      case HottestCity _ -> service.hottestCity();
    };
    return Result.success(result);
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
        .foldMap(error -> printError(error), _ -> loop());
  }

  static Program<Context, Error, Void> askAndFetchAndPrint() {
    return askCity()
        .flatMap(Weather::fetchForecast)
        .flatMap(Weather::printForecastAndPersist);
  }

  static Program<Context, Error, Void> printHottestCity() {
    return Weather.<Context, Error>hottestCity().
        flatMap(city -> writeLine("Hottest city so far: " + city.map(City::name).orElse("None")));
  }

  static Program<Context, Error, Void> printError(Error error) {
    return Console.<Context, Error>writeLine("Error: " + error).andThen(loop());
  }

  static Program<Context, Error, City> askCity() {
    return Console.<Context, Error>prompt("What city?")
        .flatMap(Weather::cityByName);
  }

  static Program<Context, Error, CityForecast> fetchForecast(City city) {
    return Weather.<Context, Error>getForecast(city)
      .flatMap(forecast -> forecast
          .map(Program::<Context, Error, Forecast>success)
          .orElseGet(Weather::forecast))
      .map(forecast -> new CityForecast(city, forecast));
  }

  static Program<Context, Error, Void> printForecastAndPersist(CityForecast cityForecast) {
    return Console.<Context, Error>writeLine("Forecast for city " + cityForecast.city() + " is " + cityForecast.forecast())
      .andThen(setForecast(cityForecast.city(), cityForecast.forecast()));
  }

  static Program<Context, Error, Forecast> forecast() {
    return Random.<Context, Error>nextInt(30).map(Forecast::new);
  }

  static Program<Context, Error, City> cityByName(String name) {
    return switch (name) {
      case "Madrid", "Getafe", "Elche" -> Program.success(new City(name));
      default -> Program.failure(new UnknownCity(name));
    };
  }

  static Program<Context, Error, Void> hostAndPort() {
    return Weather.<Context, Error>readConfig()
        .flatMap(config -> writeLine("conecting to " + config));
  }

  final class Context implements Service, Console.Service, Random.Service {

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
  }
}
