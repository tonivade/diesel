/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static java.util.function.Predicate.not;
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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
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
          .filter(not(String::isEmpty))
          .orElse(element.getSimpleName() + DSL_SUFFIX);
        var errorType = getErrorTypeFromAnnotation(diesel)
          .map(this::toTypeName)
          .orElse(TypeName.VOID.box());
        // the bound of E: without errorType, E is unbounded, so any error is valid
        var errorBound = getErrorTypeFromAnnotation(diesel)
          .map(value -> (TypeMirror) value.getValue())
          .filter(type -> asSupertype(type, Void.class).isEmpty())
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
      .map(Map.Entry::getValue)
      .findFirst();
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
    String packageName = processingEnv.getElementUtils()
        .getPackageOf(element)
        .getQualifiedName()
        .toString();

    TypeName service = createServiceType(element);

    var dslTypeBuilder = createDslType(dslName, element.getTypeParameters());

    var site = (DeclaredType) element.asType();

    for (var method : serviceMethods(element)) {
      var resolved = (ExecutableType) processingEnv.getTypeUtils().asMemberOf(site, method);
      if (hasClashingTypeVariables(method.getTypeParameters())) {
        printError("type variables in method " + method.getSimpleName()
            + " clash with the type variables generated by @Diesel (S, E), rename them", method);
      } else if (hasClashingTypeVariables(element.getTypeParameters())) {
        printError("type variables in method " + method.getSimpleName()
            + " clash with the type variables generated by @Diesel (S, E), rename them", method);
      } else if (!hasValidErrorType(resolved.getReturnType(), errorBound)) {
        printError("the error type of method " + method.getSimpleName()
            + " is not assignable to the errorType of @Diesel: " + errorBound, method);
      } else if (!hasValidReturnType(resolved.getReturnType())) {
        printError("the return type of method " + method.getSimpleName()
            + " is not valid: cannot be Void, use Unit", method);
      } else {
        dslTypeBuilder.addMethod(
            createFactoryMethod(method, resolved, service, errorType, element.getTypeParameters()));
      }
    }

    return JavaFile.builder(packageName, dslTypeBuilder.build()).build();
  }

  private TypeName createServiceType(TypeElement element) {
    var service = ClassName.get(element);
    if (!element.getTypeParameters().isEmpty()) {
      return ParameterizedTypeName.get(
          service, element.getTypeParameters().stream().map(TypeVariableName::get).toArray(TypeName[]::new));
    }
    return service;
  }

  private List<ExecutableElement> serviceMethods(TypeElement element) {
    var elements = processingEnv.getElementUtils();
    var object = elements.getTypeElement(Object.class.getName());

    return elements.getAllMembers(element).stream()
        .filter(member -> member.getKind() == ElementKind.METHOD)
        .filter(member -> !isPrivateOrStatic(member))
        .filter(member -> !Objects.equals(member.getEnclosingElement(), object))
        .map(ExecutableElement.class::cast)
        .toList();
  }

  private boolean isPrivateOrStatic(Element enclosedElement) {
    return enclosedElement.getModifiers().contains(Modifier.STATIC)
        || enclosedElement.getModifiers().contains(Modifier.PRIVATE);
  }

  private TypeSpec.Builder createDslType(String dslName, List<? extends TypeParameterElement> list) {
    return TypeSpec.interfaceBuilder(dslName)
        .addAnnotation(AnnotationSpec.builder(Generated.class).addMember(VALUE, "\"" + getClass().getName() + "\"").build())
        .addTypeVariables(list.stream().map(TypeVariableName::get).toList())
        .addModifiers(Modifier.PUBLIC);
  }

  private boolean hasClashingTypeVariables(List<? extends TypeParameterElement> params) {
    return params.stream()
        .map(param -> param.getSimpleName().toString())
        .anyMatch(name -> name.equals(STATE_VARIABLE) || name.equals(ERROR_VARIABLE));
  }

  private MethodSpec createFactoryMethod(ExecutableElement method, ExecutableType resolved,
      TypeName service, TypeName errorType, List<? extends TypeParameterElement> classTypeParams) {
    var methodName = method.getSimpleName().toString();
    var program = ClassName.get(Program.class);
    var returnType = ParameterizedTypeName.get(program,
        TypeVariableName.get(STATE_VARIABLE), TypeVariableName.get(ERROR_VARIABLE), getReturnTypeFor(resolved.getReturnType()));
    var paramTypeMap = createParamTypeMap(method.getParameters(), resolved.getParameterTypes());
    return MethodSpec.methodBuilder(methodName)
        .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).addMember(VALUE, UNCHECKED).addMember(VALUE, RAWTYPES).build())
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .addTypeVariables(classTypeParams.stream().map(TypeVariableName::get).toList())
        .addTypeVariables(List.of(
            TypeVariableName.get(STATE_VARIABLE, service),
            errorType.equals(TypeName.VOID.box())
                ? TypeVariableName.get(ERROR_VARIABLE) : TypeVariableName.get(ERROR_VARIABLE, errorType)))
        // the method's own type variables, with their bounds
        .addTypeVariables(resolved.getTypeVariables().stream().map(TypeVariableName::get).toList())
        .returns(returnType)
        .addParameters(method.getParameters().stream()
            .map(param -> ParameterSpec.builder(TypeName.get(paramTypeMap.get(param)), param.getSimpleName().toString()).build())
            .toList())
        .addCode(createMethodBody(method, resolved.getReturnType()))
        .varargs(method.isVarArgs())
        .build();
  }

  private Map<VariableElement, TypeMirror> createParamTypeMap(
      List<? extends VariableElement> parameters, List<? extends TypeMirror> parameterTypes) {
    Map<VariableElement, TypeMirror> map = new IdentityHashMap<>();
    for (int i = 0; i < parameters.size(); i++) {
      map.put(parameters.get(i), parameterTypes.get(i));
    }
    return map;
  }

  private CodeBlock createMethodBody(ExecutableElement method, TypeMirror returnType) {
    var isFuture = asSupertype(returnType, CompletableFuture.class).isPresent();
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
    return CodeBlock.builder()
        .addStatement(format, method.getSimpleName(), parameterNames(method))
        .build();
  }

  private CodeBlock createStatement(String format, Class<?> cast, ExecutableElement method) {
    return CodeBlock.builder()
        .addStatement(format, cast, method.getSimpleName(), parameterNames(method))
        .build();
  }

  private String parameterNames(ExecutableElement method) {
    return method.getParameters().stream()
        .map(param -> param.getSimpleName().toString())
        .collect(joining(","));
  }

  private TypeName getReturnTypeFor(TypeMirror returnType) {
    var result = resultTypeOf(returnType);
    if (result.isPresent()) {
      // the value of the Result, returned directly or in a CompletableFuture
      return lastTypeArgument(result.get());
    }
    var future = asSupertype(returnType, CompletableFuture.class);
    if (future.isPresent()) {
      return lastTypeArgument(future.get());
    }
    if (isPrimitive(returnType)) {
      return TypeName.get(returnType).box();
    }
    if (isVoid(returnType)) {
      return TypeName.get(Unit.class);
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
  private boolean hasValidErrorType(TypeMirror returnType, TypeMirror errorBound) {
    return resultTypeOf(returnType)
        .filter(result -> !result.getTypeArguments().isEmpty())
        .map(result -> result.getTypeArguments().getFirst())
        .map(error -> error instanceof WildcardType wildcard ? wildcard.getExtendsBound() : error)
        // a raw Result, or an unknown error like Result<?, T>, can't be checked
        .map(error -> processingEnv.getTypeUtils().isAssignable(error, errorBound))
        .orElse(true);
  }

  private boolean hasValidReturnType(TypeMirror returnType) {
    return !processingEnv.getTypeUtils().isSameType(returnType, typeElement(Void.class).asType())
        && valueTypeOf(returnType)
          .map(value -> !processingEnv.getTypeUtils().isSameType(value, typeElement(Void.class).asType()))
          // a raw type, or an unbounded wildcard like Result<E, ?>, can't be checked
          .orElse(true);
  }

  /**
   * The type of the value returned by the method: of the Result, returned directly or in a
   * CompletableFuture, or of the CompletableFuture.
   */
  private Optional<TypeMirror> valueTypeOf(TypeMirror returnType) {
    return resultTypeOf(returnType)
        .or(() -> asSupertype(returnType, CompletableFuture.class))
        .filter(type -> !type.getTypeArguments().isEmpty())
        .map(type -> type.getTypeArguments().getLast())
        .map(value -> value instanceof WildcardType wildcard ? wildcard.getExtendsBound() : value);
  }

  /**
   * The Result returned by the method, directly or in a CompletableFuture.
   */
  private Optional<DeclaredType> resultTypeOf(TypeMirror returnType) {
    return asSupertype(returnType, Result.class)
        .or(() -> asSupertype(returnType, CompletableFuture.class)
            .filter(future -> !future.getTypeArguments().isEmpty())
            .map(future -> future.getTypeArguments().getLast())
            .map(value -> value instanceof WildcardType wildcard && wildcard.getExtendsBound() != null
                ? wildcard.getExtendsBound() : value)
            .flatMap(value -> asSupertype(value, Result.class)));
  }

  /**
   * The type viewed as the given class, with its type arguments: {@code Result.Success<E, T>} is
   * {@code Result<E, T>}, and {@code class StringFuture extends CompletableFuture<String>} is
   * {@code CompletableFuture<String>}.
   */
  private Optional<DeclaredType> asSupertype(TypeMirror type, Class<?> expected) {
    if (type instanceof DeclaredType declaredType) {
      if (declaredType.asElement().equals(typeElement(expected))) {
        return Optional.of(declaredType);
      }
      return processingEnv.getTypeUtils().directSupertypes(declaredType).stream()
          .map(supertype -> asSupertype(supertype, expected))
          .flatMap(Optional::stream)
          .findFirst();
    }
    return Optional.empty();
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
