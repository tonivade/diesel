/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

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
    if (element.getKind().name().equals("INTERFACE")) {
      printNote(element.getSimpleName() + " interface found");
      save(generateDsl((TypeElement) element));
    } else {
      printError(element.getSimpleName() + " is not supported: " + element.getKind());
    }
  }

  private void save(JavaFile javaFile) {
    try {
      javaFile.writeTo(processingEnv.getFiler());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private JavaFile generateDsl(TypeElement typeElement) {
    String packageName = processingEnv.getElementUtils().getPackageOf(typeElement).getQualifiedName().toString();
    String interfaceName = typeElement.getSimpleName().toString();
    String dslName = interfaceName + "Dsl";

    var program = ClassName.get("com.github.tonivade.diesel", "Program");
    var result = ClassName.get("com.github.tonivade.diesel", "Result");
    var service = ClassName.get(packageName, interfaceName);
    var dsl = ClassName.get(packageName, dslName);

    var dslTypeBuilder = TypeSpec.interfaceBuilder(dslName)
        .addModifiers(Modifier.PUBLIC)
        .addTypeVariables(List.of(TypeVariableName.get("T")))
        .addSuperinterface(ParameterizedTypeName.get(program.nestedClass("Dsl"), service, TypeName.VOID.box(), TypeVariableName.get("T")));

    for (Element enclosedElement : typeElement.getEnclosedElements()) {
      if (enclosedElement.getKind() == ElementKind.METHOD) {
        dslTypeBuilder.addType(createRecordClass((ExecutableElement) enclosedElement, dsl));
      }
    }

    dslTypeBuilder.addMethod(createDslEvalMethod(result, service));

    return JavaFile.builder(packageName, dslTypeBuilder.build()).build();
  }

  private TypeSpec createRecordClass(ExecutableElement method, ClassName dsl) {
    var methodName = method.getSimpleName().toString();
    var returnType = method.getReturnType();
    var returnTypeParameter = isPrimitiveOrVoid(returnType) ? TypeName.get(returnType).box() : TypeName.get(returnType);
    var superInterface = ParameterizedTypeName.get(dsl, returnTypeParameter);
    var constructor = MethodSpec.constructorBuilder()
        .addParameters(method.getParameters().stream()
            .map(param -> ParameterSpec.builder(TypeName.get(param.asType()), param.getSimpleName().toString()).build())
            .toList())
        .build();
    return TypeSpec.recordBuilder(methodName.substring(0, 1).toUpperCase() + methodName.substring(1))
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .addSuperinterface(superInterface)
        .recordConstructor(constructor)
        .build();
  }

  private MethodSpec createDslEvalMethod(ClassName result, ClassName service) {
    return MethodSpec.methodBuilder("dslEval")
        .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
        .returns(ParameterizedTypeName.get(result, TypeName.VOID.box(), TypeVariableName.get("T")))
        .addParameter(service, "state")
        .addCode(CodeBlock.builder().addStatement("return null").build())
        .build();
  }

  private boolean isPrimitiveOrVoid(TypeMirror returnType) {
    return returnType.getKind().isPrimitive() || returnType.getKind() == TypeKind.VOID;
  }

  private void printNote(String msg) {
    processingEnv.getMessager().printMessage(Kind.NOTE, msg);
  }

  private void printError(String msg) {
    processingEnv.getMessager().printMessage(Kind.ERROR, msg);
  }
}
