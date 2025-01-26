/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;

@SupportedAnnotationTypes("com.github.tonivade.diesel.Diesel")
public class DieselAnnotationProcessor extends AbstractProcessor {

  private static final String VALUE = "value";

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (TypeElement annotation : annotations) {
      for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
        getAdapterFromAnnotation(getAnnotation(annotation, element))
          .ifPresentOrElse(
              adapter -> adapterAlreadyExists(element, adapter), () -> generateAdapter(element));
      }
    }
    return true;
  }

  private AnnotationMirror getAnnotation(TypeElement annotation, Element element) {
    return element.getAnnotationMirrors().stream()
        .filter(am -> am.getAnnotationType().equals(annotation.asType()))
        .findFirst().orElseThrow();
  }

  private Optional<? extends AnnotationValue> getAdapterFromAnnotation(AnnotationMirror json) {
    return json.getElementValues().entrySet().stream()
      .filter(entry -> entry.getKey().getSimpleName().toString().equals(VALUE))
      .map(Map.Entry::getValue).findFirst();
  }

  private void adapterAlreadyExists(Element element, AnnotationValue adapter) {
    printNote(element.getSimpleName() + " pojo found with adapter: " + adapter.getValue());
  }

  private void generateAdapter(Element element) {
    if (element.getKind().name().equals("INTERFACE")) {
      printNote(element.getSimpleName() + " record found");
//      saveFile(modelForRecord((TypeElement) element));
    } else {
      printError(element.getSimpleName() + " is not supported: " + element.getKind());
    }
  }

  private void printNote(String msg) {
    processingEnv.getMessager().printMessage(Kind.NOTE, msg);
  }

  private void printError(String msg) {
    processingEnv.getMessager().printMessage(Kind.ERROR, msg);
  }
}
