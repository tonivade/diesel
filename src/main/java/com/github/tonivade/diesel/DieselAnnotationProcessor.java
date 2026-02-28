/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
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
import javax.annotation.processing.Generated;
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
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;

import com.palantir.javapoet.AnnotationSpec;
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

  private static final String UNCHECKED = "\"unchecked\"";
  private static final String DSL_SUFFIX = "Dsl";
  private static final String DIESEL_PACKAGE_NAME = "com.github.tonivade.diesel";
  private static final String RESULT = "Result";
  private static final String PROGRAM = "Program";
  private static final String VALUE = "value";
  private static final String ERROR_TYPE = "errorType";

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (TypeElement annotation : annotations) {
      for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
        var diesel = getAnnotation(annotation, element);
        var dslName = getDslNameFromAnnotation(diesel)
          .map(a -> a.getValue().toString())
          .orElse(element.getSimpleName() + DSL_SUFFIX);
        var errorType = getErrorTypeFromAnnotation(diesel)
          .map(this::toTypeName)
          .orElse(TypeName.VOID.box());
        generate(element, dslName, errorType);
      }
    }
    return false;
  }

  private AnnotationMirror getAnnotation(TypeElement annotation, Element element) {
    return element.getAnnotationMirrors().stream()
        .filter(am -> am.getAnnotationType().equals(annotation.asType()))
        .findFirst().orElseThrow();
  }

  private Optional<? extends AnnotationValue> getDslNameFromAnnotation(AnnotationMirror diesel) {
    return diesel.getElementValues().entrySet().stream()
      .filter(entry -> entry.getKey().getSimpleName().toString().equals(VALUE))
      .map(Map.Entry::getValue).findFirst();
  }

  private void generate(Element element, String dslName, TypeName errorType) {
    if (element.getKind() == ElementKind.INTERFACE) {
      printNote(element.getSimpleName() + " interface found");
      saveFile(generateDsl((TypeElement) element, dslName, errorType));
    } else {
      printError(element.getSimpleName() + " is not supported: " + element.getKind());
    }
  }

  private TypeName toTypeName(AnnotationValue value) {
    return TypeName.get((DeclaredType) value.getValue());
  }

  private Optional<? extends AnnotationValue> getErrorTypeFromAnnotation(AnnotationMirror json) {
    return json.getElementValues().entrySet().stream()
      .filter(entry -> entry.getKey().getSimpleName().toString().equals(ERROR_TYPE))
      .map(Map.Entry::getValue).findFirst();
  }

  private void saveFile(JavaFile javaFile) {
    try {
      javaFile.writeTo(processingEnv.getFiler());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private JavaFile generateDsl(TypeElement element, String dslName, TypeName errorType) {
    String packageName = processingEnv.getElementUtils().getPackageOf(element).getQualifiedName().toString();
    String interfaceName = element.getSimpleName().toString();

    var service = ClassName.get(packageName, interfaceName);
    var dsl = ClassName.get(packageName, dslName);

    var dslTypeBuilder = createDslType(dslName, service, errorType);

    for (Element enclosedElement : element.getEnclosedElements()) {
      if (enclosedElement.getKind() == ElementKind.METHOD) {
        dslTypeBuilder.addType(createRecordClass((ExecutableElement) enclosedElement, dsl));
        dslTypeBuilder.addMethod(createFactoryMethod((ExecutableElement) enclosedElement, service, errorType));
      }
    }

    dslTypeBuilder.addMethod(createHandleMethod(element, service, errorType));

    return JavaFile.builder(packageName, dslTypeBuilder.build()).build();
  }

  private TypeSpec.Builder createDslType(String dslName, ClassName service, TypeName errorType) {
    var program = ClassName.get(DIESEL_PACKAGE_NAME, PROGRAM);
    return TypeSpec.interfaceBuilder(dslName)
        .addAnnotation(AnnotationSpec.builder(Generated.class).addMember(VALUE, "\"" + getClass().getName() + "\"").build())
        .addModifiers(Modifier.PUBLIC, Modifier.SEALED)
        .addTypeVariables(List.of(TypeVariableName.get("T")))
        .addSuperinterface(ParameterizedTypeName.get(program.nestedClass(DSL_SUFFIX), service, errorType, TypeVariableName.get("T")));
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

  private MethodSpec createFactoryMethod(ExecutableElement method, ClassName service, TypeName errorType) {
    var methodName = method.getSimpleName().toString();
    var program = ClassName.get(DIESEL_PACKAGE_NAME, PROGRAM);
    var returnType = ParameterizedTypeName.get(program, TypeVariableName.get("S"), TypeVariableName.get("E"), getReturnTypeFor(method));
    return MethodSpec.methodBuilder(methodName)
        .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).addMember(VALUE, UNCHECKED).build())
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .addTypeVariables(List.of(
            TypeVariableName.get("S", service),
            errorType.equals(TypeName.VOID.box()) ? TypeVariableName.get("E") : TypeVariableName.get("E", errorType)))
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

  private MethodSpec createHandleMethod(TypeElement element, ClassName service, TypeName errorType) {
    var result = ClassName.get(DIESEL_PACKAGE_NAME, RESULT);
    return MethodSpec.methodBuilder("handle")
        .addAnnotation(Override.class)
        .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).addMember(VALUE, UNCHECKED).build())
        .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
        .returns(ParameterizedTypeName.get(result, errorType, TypeVariableName.get("T")))
        .addParameter(service, "state")
        .addCode(handleMethod(element, errorType))
        .build();
  }

  private CodeBlock handleMethod(TypeElement element, TypeName errorType) {
    return CodeBlock.builder()
        .add(createSwitch(element, errorType))
        .build();
  }

  private CodeBlock createSwitch(TypeElement element, TypeName errorType) {
    var builder = CodeBlock.builder().beginControlFlow("return switch (this)");
    for (Element enclosedElement : element.getEnclosedElements()) {
      if (enclosedElement.getKind() == ElementKind.METHOD) {
        builder.add(createCase((ExecutableElement) enclosedElement, errorType));
      }
    }
    // a switch expression must end in a semicolon, don't know how to do it with javapoet
    return builder.unindent().addStatement("}").build();
  }

  private CodeBlock createCase(ExecutableElement method, TypeName errorType) {
    var methodName = method.getSimpleName().toString();
    var returnType = method.getReturnType();
    if (returnType.getKind() == TypeKind.VOID) {
      return CodeBlock.builder()
          .beginControlFlow("case $N -> ", buildPattern(method))
          .addStatement("state.$N($L)", methodName, buildParams(method))
          .addStatement("yield Result.<$T, T>success((T) null)", errorType)
          .endControlFlow()
          .build();
    }
    if (returnType.getKind().isPrimitive()) {
      return CodeBlock.builder()
          .addStatement("case $N -> Result.<$T, T>success((T) ($T) state.$N($L))",
              buildPattern(method), errorType, TypeName.get(returnType).box(), methodName, buildParams(method))
          .build();
    }
    if (returnType instanceof DeclaredType declared && declared.toString().startsWith(DIESEL_PACKAGE_NAME + "." + RESULT)) {
      return CodeBlock.builder()
          .addStatement("case $N -> (Result<$T, T>) state.$N($L)", buildPattern(method), errorType, methodName, buildParams(method))
          .build();
    }
    return CodeBlock.builder()
        .addStatement("case $N -> Result.<$T, T>success((T) state.$N($L))", buildPattern(method), errorType, methodName, buildParams(method))
        .build();
  }

  private String buildPattern(ExecutableElement method) {
    String methodName = method.getSimpleName().toString();
    return methodName.substring(0, 1).toUpperCase() + methodName.substring(1) +
        method.getParameters().stream()
          .map(param -> "var " + param.getSimpleName().toString())
          .collect(joining(",", "(", ")"));
  }

  private String buildParams(ExecutableElement method) {
    return method.getParameters().stream()
        .map(param -> param.getSimpleName().toString())
        .collect(joining(","));
  }

  private TypeName getReturnTypeFor(ExecutableElement method) {
    var returnType = method.getReturnType();
    if (returnType instanceof DeclaredType declared && declared.toString().startsWith(DIESEL_PACKAGE_NAME + "." + RESULT)) {
      return TypeName.get(declared.getTypeArguments().getLast());
    }
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
