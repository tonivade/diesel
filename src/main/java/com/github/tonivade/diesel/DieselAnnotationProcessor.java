/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static java.util.stream.Collectors.joining;

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
import com.palantir.javapoet.WildcardTypeName;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

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
import javax.lang.model.type.WildcardType;
import javax.tools.Diagnostic.Kind;

/**
 * Annotation processor for the {@link Diesel} annotation.
 */
@SupportedAnnotationTypes("com.github.tonivade.diesel.Diesel")
public class DieselAnnotationProcessor extends AbstractProcessor {

  private static final String RAWTYPES = "\"rawtypes\"";
  private static final String UNCHECKED = "\"unchecked\"";
  private static final String DSL_SUFFIX = "Dsl";
  private static final String VALUE = "value";
  private static final String ERROR_TYPE = "errorType";
  private static final String STATE_VARIABLE = "S";
  private static final String ERROR_VARIABLE = "E";

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
        // the bound of E: without errorType, E is unbounded, so any error is valid
        var errorBound = getErrorTypeFromAnnotation(diesel)
          .map(value -> (TypeMirror) value.getValue())
          .filter(type -> !hasRawType((DeclaredType) type, Void.class))
          .orElseGet(() -> typeElement(Object.class).asType());
        generate(element, dslName, errorType, errorBound);
      }
    }
    return false;
  }

  private AnnotationMirror getAnnotation(TypeElement annotation, Element element) {
    return element.getAnnotationMirrors().stream()
        .filter(am -> processingEnv.getTypeUtils().isSameType(am.getAnnotationType(), annotation.asType()))
        .findFirst().orElseThrow();
  }

  private Optional<? extends AnnotationValue> getDslNameFromAnnotation(AnnotationMirror diesel) {
    return diesel.getElementValues().entrySet().stream()
      .filter(entry -> entry.getKey().getSimpleName().toString().equals(VALUE))
      .map(Map.Entry::getValue).findFirst();
  }

  private void generate(Element element, String dslName, TypeName errorType, TypeMirror errorBound) {
    if (element.getKind() == ElementKind.INTERFACE) {
      printNote(element.getSimpleName() + " interface found");
      saveFile(generateDsl((TypeElement) element, dslName, errorType, errorBound));
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

  private JavaFile generateDsl(TypeElement element, String dslName, TypeName errorType, TypeMirror errorBound) {
    String packageName = processingEnv.getElementUtils().getPackageOf(element).getQualifiedName().toString();
    String interfaceName = element.getSimpleName().toString();

    var service = ClassName.get(packageName, interfaceName);

    var dslTypeBuilder = createDslType(dslName);

    for (Element enclosedElement : element.getEnclosedElements()) {
      if (enclosedElement.getKind() == ElementKind.METHOD) {
        var method = (ExecutableElement) enclosedElement;
        if (hasClashingTypeVariables(method)) {
          printError("type variables in method " + method.getSimpleName()
              + " clash with the type variables generated by @Diesel (S, E), rename them", method);
        } else if (!hasValidErrorType(method, errorBound)) {
          printError("the error type of method " + method.getSimpleName()
              + " is not assignable to the errorType of @Diesel: " + errorBound, method);
        } else {
          dslTypeBuilder.addMethod(createFactoryMethod(method, service, errorType));
        }
      }
    }

    return JavaFile.builder(packageName, dslTypeBuilder.build()).build();
  }

  private TypeSpec.Builder createDslType(String dslName) {
    return TypeSpec.interfaceBuilder(dslName)
        .addAnnotation(AnnotationSpec.builder(Generated.class).addMember(VALUE, "\"" + getClass().getName() + "\"").build())
        .addModifiers(Modifier.PUBLIC);
  }

  private boolean hasClashingTypeVariables(ExecutableElement method) {
    return method.getTypeParameters().stream()
        .map(param -> param.getSimpleName().toString())
        .anyMatch(name -> name.equals(STATE_VARIABLE) || name.equals(ERROR_VARIABLE));
  }

  private MethodSpec createFactoryMethod(ExecutableElement method, ClassName service, TypeName errorType) {
    var methodName = method.getSimpleName().toString();
    var program = ClassName.get(Program.class);
    var returnType = ParameterizedTypeName.get(program,
        TypeVariableName.get(STATE_VARIABLE), TypeVariableName.get(ERROR_VARIABLE), getReturnTypeFor(method));
    return MethodSpec.methodBuilder(methodName)
        .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).addMember(VALUE, UNCHECKED).addMember(VALUE, RAWTYPES).build())
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .addTypeVariables(List.of(
            TypeVariableName.get(STATE_VARIABLE, service),
            errorType.equals(TypeName.VOID.box())
                ? TypeVariableName.get(ERROR_VARIABLE) : TypeVariableName.get(ERROR_VARIABLE, errorType)))
        // the method's own type variables, with their bounds
        .addTypeVariables(method.getTypeParameters().stream().map(TypeVariableName::get).toList())
        .returns(returnType)
        .addParameters(method.getParameters().stream()
            .map(param -> ParameterSpec.builder(TypeName.get(param.asType()), param.getSimpleName().toString()).build())
            .toList())
        .addCode(createMethodBody(method))
        .build();
  }

  private CodeBlock createMethodBody(ExecutableElement method) {
    var returnType = method.getReturnType();
    var isFuture = returnType instanceof DeclaredType declaredType && hasRawType(declaredType, CompletableFuture.class);
    if (resultTypeOf(returnType).isPresent()) {
      return isFuture
          ? createStatement("return Program.accessFutureResult(state -> ($T) state.$N($L))", CompletableFuture.class, method)
          : createStatement("return Program.accessResult(state -> ($T) state.$N($L))", Result.class, method);
    }
    if (isFuture) {
      return createStatement("return Program.accessFuture(state -> state.$N($L))", method);
    }
    if (isVoid(returnType)) {
      return createStatement("return Program.inspect(state -> state.$N($L))", method);
    }
    return createStatement("return Program.access(state -> state.$N($L))", method);
  }

  private CodeBlock createStatement(String format, ExecutableElement method) {
    return CodeBlock.builder().addStatement(format, method.getSimpleName(), parameterNames(method)).build();
  }

  private CodeBlock createStatement(String format, Class<?> cast, ExecutableElement method) {
    return CodeBlock.builder().addStatement(format, cast, method.getSimpleName(), parameterNames(method)).build();
  }

  private String parameterNames(ExecutableElement method) {
    return method.getParameters().stream().map(param -> param.getSimpleName().toString()).collect(joining(","));
  }

  private TypeName getReturnTypeFor(ExecutableElement method) {
    var returnType = method.getReturnType();
    var result = resultTypeOf(returnType);
    if (result.isPresent()) {
      // the value of the Result, returned directly or in a CompletableFuture
      return lastTypeArgument(result.get());
    }
    if (returnType instanceof DeclaredType declaredType && hasRawType(declaredType, CompletableFuture.class)) {
      return lastTypeArgument(declaredType);
    }
    if (isPrimitive(returnType) || isVoid(returnType)) {
      return TypeName.get(returnType).box();
    }
    return TypeName.get(returnType);
  }

  private TypeName lastTypeArgument(DeclaredType type) {
    if (type.getTypeArguments().isEmpty()) {
      return WildcardTypeName.subtypeOf(Object.class);
    }
    return TypeName.get(type.getTypeArguments().getLast());
  }

  /**
   * The generated code casts the error of the Result returned by the service to E without any
   * check, so it has to be assignable to the bound of E.
   */
  private boolean hasValidErrorType(ExecutableElement method, TypeMirror errorBound) {
    return resultTypeOf(method.getReturnType())
        .filter(result -> !result.getTypeArguments().isEmpty())
        .map(result -> result.getTypeArguments().getFirst())
        .map(error -> error instanceof WildcardType wildcard ? wildcard.getExtendsBound() : error)
        // a raw Result, or an unknown error like Result<?, T>, can't be checked
        .map(error -> processingEnv.getTypeUtils().isAssignable(error, errorBound))
        .orElse(true);
  }

  /**
   * The Result returned by the method, directly or in a CompletableFuture.
   */
  private Optional<DeclaredType> resultTypeOf(TypeMirror returnType) {
    if (returnType instanceof DeclaredType declaredType) {
      if (hasRawType(declaredType, Result.class)) {
        return Optional.of(declaredType);
      }
      if (hasRawType(declaredType, CompletableFuture.class) && !declaredType.getTypeArguments().isEmpty()) {
        var valueType = declaredType.getTypeArguments().getLast();
        if (valueType instanceof WildcardType wildcard && wildcard.getExtendsBound() != null) {
          valueType = wildcard.getExtendsBound();
        }
        if (valueType instanceof DeclaredType valueDeclaredType && hasRawType(valueDeclaredType, Result.class)) {
          return Optional.of(valueDeclaredType);
        }
      }
    }
    return Optional.empty();
  }

  private boolean hasRawType(DeclaredType type, Class<?> expected) {
    var types = processingEnv.getTypeUtils();
    var expectedElement = typeElement(expected);

    return expectedElement != null
        && types.isSameType(
            types.erasure(type),
            types.erasure(expectedElement.asType()));
  }

  private TypeElement typeElement(Class<?> type) {
    return processingEnv.getElementUtils()
        .getTypeElement(type.getCanonicalName());
  }

  private boolean isPrimitive(TypeMirror returnType) {
    return returnType.getKind().isPrimitive();
  }

  private boolean isVoid(TypeMirror returnType) {
    return returnType.getKind() == TypeKind.VOID;
  }

  private void printNote(String msg) {
    processingEnv.getMessager().printMessage(Kind.NOTE, msg);
  }

  private void printError(String msg) {
    processingEnv.getMessager().printMessage(Kind.ERROR, msg);
  }

  private void printError(String msg, Element element) {
    processingEnv.getMessager().printMessage(Kind.ERROR, msg, element);
  }
}
