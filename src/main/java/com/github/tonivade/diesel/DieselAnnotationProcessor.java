/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static java.util.stream.Collectors.joining;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
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
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import com.palantir.javapoet.TypeVariableName;

@SupportedAnnotationTypes("com.github.tonivade.diesel.Diesel")
public class DieselAnnotationProcessor extends AbstractProcessor {

  private static final String DIESEL_PACKAGE_NAME = "com.github.tonivade.diesel";
  private static final String RESULT = "Result";
  private static final String PROGRAM = "Program";
  private static final String VALUE = "value";

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (TypeElement annotation : annotations) {
      for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
        getDslFromAnnotation(getAnnotation(annotation, element))
          .ifPresentOrElse(
              dsl -> dslAlreadyExists(element, dsl), () -> generate(element));
      }
    }
    return true;
  }

  private AnnotationMirror getAnnotation(TypeElement annotation, Element element) {
    return element.getAnnotationMirrors().stream()
        .filter(am -> am.getAnnotationType().equals(annotation.asType()))
        .findFirst().orElseThrow();
  }

  private Optional<? extends AnnotationValue> getDslFromAnnotation(AnnotationMirror diesel) {
    return diesel.getElementValues().entrySet().stream()
      .filter(entry -> entry.getKey().getSimpleName().toString().equals(VALUE))
      .map(Map.Entry::getValue).findFirst();
  }

  private void dslAlreadyExists(Element element, AnnotationValue dsl) {
    printNote(element.getSimpleName() + " found with dsl: " + dsl.getValue());
  }

  private void generate(Element element) {
    if (element.getKind() == ElementKind.INTERFACE) {
      printNote(element.getSimpleName() + " interface found");
      saveFile(generateDsl((TypeElement) element));
    } else {
      printError(element.getSimpleName() + " is not supported: " + element.getKind());
    }
  }

  private void saveFile(JavaFile javaFile) {
    try {
      javaFile.writeTo(processingEnv.getFiler());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private JavaFile generateDsl(TypeElement element) {
    String packageName = processingEnv.getElementUtils().getPackageOf(element).getQualifiedName().toString();
    String interfaceName = element.getSimpleName().toString();
    String dslName = interfaceName + "Dsl";

    var service = ClassName.get(packageName, interfaceName);
    var dsl = ClassName.get(packageName, dslName);

    var dslTypeBuilder = createDslType(dslName, service);

    for (Element enclosedElement : element.getEnclosedElements()) {
      if (enclosedElement.getKind() == ElementKind.METHOD) {
        dslTypeBuilder.addType(createRecordClass((ExecutableElement) enclosedElement, dsl));
        dslTypeBuilder.addMethod(createFactoryMethod((ExecutableElement) enclosedElement, service));
      }
    }

    dslTypeBuilder.addMethod(createDslEvalMethod(element, service));

    return JavaFile.builder(packageName, dslTypeBuilder.build())
        .build();
  }

  private TypeSpec.Builder createDslType(String dslName, ClassName service) {
    var program = ClassName.get(DIESEL_PACKAGE_NAME, PROGRAM);
    return TypeSpec.interfaceBuilder(dslName)
        .addModifiers(Modifier.PUBLIC, Modifier.SEALED)
        .addTypeVariables(List.of(TypeVariableName.get("T")))
        .addSuperinterface(ParameterizedTypeName.get(program.nestedClass("Dsl"), service, TypeName.VOID.box(), TypeVariableName.get("T")));
  }

  private TypeSpec createRecordClass(ExecutableElement method, ClassName dsl) {
    var methodName = method.getSimpleName().toString();
    return TypeSpec.recordBuilder(toClassName(methodName))
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .addSuperinterface(ParameterizedTypeName.get(dsl, getReturnTypeFor(method)))
        .recordConstructor(MethodSpec.constructorBuilder()
            .addParameters(method.getParameters().stream()
                .map(param -> ParameterSpec.builder(TypeName.get(param.asType()), param.getSimpleName().toString()).build())
                .toList())
            .build())
        .build();
  }

  private MethodSpec createFactoryMethod(ExecutableElement method, ClassName service) {
    var methodName = method.getSimpleName().toString();
    var program = ClassName.get(DIESEL_PACKAGE_NAME, PROGRAM);
    var returnType = ParameterizedTypeName.get(program, TypeVariableName.get("S"), TypeVariableName.get("E"), getReturnTypeFor(method));
    return MethodSpec.methodBuilder(methodName)
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .addTypeVariables(List.of(TypeVariableName.get("S", service), TypeVariableName.get("E")))
        .returns(returnType)
        .addParameters(method.getParameters().stream()
            .map(param -> ParameterSpec.builder(TypeName.get(param.asType()), param.getSimpleName().toString()).build())
            .toList())
        .addCode(CodeBlock.builder()
            .addStatement("return ($T) new $N($L)",
                returnType,
                toClassName(methodName),
                method.getParameters().stream().map(param -> param.getSimpleName().toString()).collect(joining(",")))
            .build())
        .build();
  }

  private MethodSpec createDslEvalMethod(TypeElement element, ClassName service) {
    var result = ClassName.get(DIESEL_PACKAGE_NAME, RESULT);
    return MethodSpec.methodBuilder("dslEval")
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
        .returns(ParameterizedTypeName.get(result, TypeName.VOID.box(), TypeVariableName.get("T")))
        .addParameter(service, "state")
        .addCode(dslEvalMethod(element))
        .build();
  }

  private CodeBlock dslEvalMethod(TypeElement element) {
    return CodeBlock.builder()
        .add(createSwitch(element))
        .addStatement("return Result.success(result)")
        .build();
  }

  private CodeBlock createSwitch(TypeElement element) {
    var builder = CodeBlock.builder().beginControlFlow("var result = (T) switch (this)");
    for (Element enclosedElement : element.getEnclosedElements()) {
      if (enclosedElement.getKind() == ElementKind.METHOD) {
        builder.add(createCase((ExecutableElement) enclosedElement));
      }
    }
    // a switch expression must end in a semicolon, don't know how to do it with javapoet
    return builder.unindent().addStatement("}").build();
  }

  private CodeBlock createCase(ExecutableElement method) {
    var methodName = method.getSimpleName().toString();
    if (method.getReturnType().getKind() == TypeKind.VOID) {
      return CodeBlock.builder()
          .beginControlFlow("case $N -> ", buildPattern(method))
          .addStatement("state.$N($L)", methodName, builderParams(method))
          .addStatement("yield null")
          .endControlFlow()
          .build();
    }
    return CodeBlock.builder()
        .addStatement("case $N -> state.$N($L)", buildPattern(method), methodName, builderParams(method))
        .build();
  }

  private String buildPattern(ExecutableElement method) {
    String methodName = method.getSimpleName().toString();
    return methodName.substring(0, 1).toUpperCase() + methodName.substring(1) +
        method.getParameters().stream()
          .map(param -> "var " + param.getSimpleName().toString())
          .collect(joining(",", "(", ")"));
  }

  private String builderParams(ExecutableElement method) {
    return method.getParameters().stream()
        .map(param -> param.getSimpleName().toString())
        .collect(joining(","));
  }

  private TypeName getReturnTypeFor(ExecutableElement method) {
    var returnType = method.getReturnType();
    return isPrimitiveOrVoid(returnType) ?
        TypeName.get(returnType).box() : TypeName.get(returnType);
  }

  private boolean isPrimitiveOrVoid(TypeMirror returnType) {
    return returnType.getKind().isPrimitive() || returnType.getKind() == TypeKind.VOID;
  }

  private String toClassName(String methodName) {
    return methodName.substring(0, 1).toUpperCase() + methodName.substring(1);
  }

  private void printNote(String msg) {
    processingEnv.getMessager().printMessage(Kind.NOTE, msg);
  }

  private void printError(String msg) {
    processingEnv.getMessager().printMessage(Kind.ERROR, msg);
  }
}
