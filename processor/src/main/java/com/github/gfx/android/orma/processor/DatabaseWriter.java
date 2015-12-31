/*
 * Copyright (c) 2015 FUJI Goro (gfx).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.gfx.android.orma.processor;

import com.github.gfx.android.orma.annotation.OnConflict;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.Modifier;

public class DatabaseWriter extends BaseWriter {

    public static final String kClassName = "OrmaDatabase"; // TODO: let it customizable

    static final String kBuilderClassName = "Builder";

    static final Modifier[] publicStaticFinal = {
            Modifier.PUBLIC,
            Modifier.STATIC,
            Modifier.FINAL,
    };

    static final String connection = "connection";

    static final String SCHEMAS = "SCHEMAS";

    public DatabaseWriter(ProcessingContext context) {
        super(context);
    }

    public boolean isRequired() {
        return context.schemaMap.size() > 0;
    }

    public String getPackageName() {
        assert isRequired();

        return context.getPackageName();
    }

    @Override
    public TypeSpec buildTypeSpec() {
        assert isRequired();

        ClassName builderClass = ClassName.get(getPackageName(), kBuilderClassName);

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(context.OrmaDatabase.simpleName());
        classBuilder.addJavadoc("The Orma database handle class.<br><br>\n");
        classBuilder.addJavadoc("This is generated by {@code $L}\n", OrmaProcessor.class.getCanonicalName());
        classBuilder.addModifiers(Modifier.PUBLIC);

        classBuilder.addType(buildBuilderTypeSpec(builderClass));

        classBuilder.addFields(buildFieldSpecs());
        classBuilder.addMethods(buildMethodSpecs(builderClass));

        return classBuilder.build();
    }

    private TypeSpec buildBuilderTypeSpec(ClassName builderClass) {
        TypeSpec.Builder builder = TypeSpec.classBuilder(builderClass.simpleName());

        builder.addModifiers(Modifier.PUBLIC, Modifier.STATIC);

        builder.superclass(ParameterizedTypeName.get(Types.OrmaConfiguration, builderClass));

        builder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ParameterSpec.builder(Types.Context, "context")
                        .addAnnotation(Specs.buildNonNullAnnotationSpec())
                        .build())
                .addStatement("super(context)")
                .build());

        builder.addMethod(MethodSpec.methodBuilder("build")
                .addModifiers(Modifier.PUBLIC)
                .returns(ClassName.get(getPackageName(), kClassName))
                .addStatement("return new $L(new $T(this.fillDefaults(), $L))", kClassName, Types.OrmaConnection, SCHEMAS)
                .build());

        return builder.build();
    }

    public List<FieldSpec> buildFieldSpecs() {
        List<FieldSpec> fieldSpecs = new ArrayList<>();

        List<FieldSpec> schemaFields = new ArrayList<>();

        context.schemaMap.values().forEach((schema) -> {
            schemaFields.add(
                    FieldSpec.builder(schema.getSchemaClassName(),
                            "schema" + schema.getModelClassName().simpleName())
                            .addModifiers(publicStaticFinal)
                            .initializer("new $T()", schema.getSchemaClassName())
                            .build());
        });

        fieldSpecs.addAll(schemaFields);

        fieldSpecs.add(
                FieldSpec.builder(Types.getList(Types.WildcardSchema), SCHEMAS, publicStaticFinal)
                        .initializer(buildSchemasInitializer(schemaFields))
                        .build());

        fieldSpecs.add(
                FieldSpec.builder(Types.OrmaConnection, connection, Modifier.PRIVATE, Modifier.FINAL)
                        .build());

        return fieldSpecs;
    }

    private CodeBlock buildSchemasInitializer(List<FieldSpec> schemaFields) {
        CodeBlock.Builder builder = CodeBlock.builder();
        builder.add("$T.<$T>asList(\n", Types.Arrays, Types.WildcardSchema).indent();

        for (int i = 0; i < schemaFields.size(); i++) {
            builder.add("$N", schemaFields.get(i));

            if ((i + 1) != schemaFields.size()) {
                builder.add(",\n");
            } else {
                builder.add("\n");
            }
        }

        builder.unindent().add(")");
        return builder.build();
    }

    public List<MethodSpec> buildMethodSpecs(ClassName builderClass) {
        List<MethodSpec> methodSpecs = new ArrayList<>();

        methodSpecs.addAll(buildConstructorSpecs());

        methodSpecs.add(
                MethodSpec.methodBuilder("builder")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .returns(builderClass)
                        .addParameter(ParameterSpec.builder(Types.Context, "context")
                                .addAnnotation(Specs.buildNonNullAnnotationSpec())
                                .build())
                        .addStatement("return new $T(context)", builderClass)
                        .build());

        methodSpecs.add(
                MethodSpec.methodBuilder("getSchemas")
                        .addAnnotation(Specs.buildNonNullAnnotationSpec())
                        .addModifiers(Modifier.PUBLIC)
                        .returns(Types.getList(Types.WildcardSchema))
                        .addStatement("return $L", SCHEMAS)
                        .build()
        );

        methodSpecs.add(
                MethodSpec.methodBuilder("getConnection")
                        .addAnnotation(Specs.buildNonNullAnnotationSpec())
                        .addModifiers(Modifier.PUBLIC)
                        .returns(Types.OrmaConnection)
                        .addStatement("return $L", connection)
                        .build()
        );

        methodSpecs.add(
                MethodSpec.methodBuilder("transactionSync")
                        .addException(Types.TransactionAbortException)
                        .addModifiers(Modifier.PUBLIC)
                        .addAnnotation(Specs.buildWorkerThreadSpec())
                        .addParameter(
                                ParameterSpec.builder(Types.TransactionTask, "task")
                                        .addAnnotation(Specs.buildNonNullAnnotationSpec())
                                        .build())
                        .addStatement("$L.transactionSync(task)", connection)
                        .build()
        );

        methodSpecs.add(
                MethodSpec.methodBuilder("transactionAsync")
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(
                                ParameterSpec.builder(Types.TransactionTask, "task")
                                        .addAnnotation(Specs.buildNonNullAnnotationSpec())
                                        .build())
                        .addStatement("$L.transactionAsync(task)", connection)
                        .build()
        );

        methodSpecs.add(
                MethodSpec.methodBuilder("transactionNonExclusiveSync")
                        .addException(Types.TransactionAbortException)
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(
                                ParameterSpec.builder(Types.TransactionTask, "task")
                                        .addAnnotation(Specs.buildNonNullAnnotationSpec())
                                        .build())
                        .addStatement("$L.transactionNonExclusiveSync(task)", connection)
                        .build()
        );

        methodSpecs.add(
                MethodSpec.methodBuilder("transactionNonExclusiveAsync")
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(
                                ParameterSpec.builder(Types.TransactionTask, "task")
                                        .addAnnotation(Specs.buildNonNullAnnotationSpec())
                                        .build())
                        .addStatement("$L.transactionNonExclusiveAsync(task)", connection)
                        .build()
        );

        context.schemaMap.values().forEach(schema -> {
            String simpleModelName = schema.getModelClassName().simpleName();
            String schemaInstance = "schema" + simpleModelName;

            methodSpecs.add(MethodSpec.methodBuilder("load" + simpleModelName + "fromCursor")
                    .addAnnotation(Specs.buildNonNullAnnotationSpec())
                    .addModifiers(Modifier.PUBLIC)
                    .returns(schema.getModelClassName())
                    .addParameter(
                            ParameterSpec.builder(Types.Cursor, "cursor")
                                    .addAnnotation(Specs.buildNonNullAnnotationSpec())
                                    .build()
                    )
                    .addStatement("return $L.newModelFromCursor($L, cursor)", schemaInstance, connection)
                    .build());

            methodSpecs.add(MethodSpec.methodBuilder("create" + simpleModelName)
                    .addJavadoc(
                            "Inserts a model created by {@code ModelFactory<T>},"
                                    + " and retrieves it which is just inserted.\n"
                                    + " The return value has the row ID.\n")
                    .addAnnotation(Specs.buildNonNullAnnotationSpec())
                    .addModifiers(Modifier.PUBLIC)
                    .returns(schema.getModelClassName())
                    .addParameter(
                            ParameterSpec.builder(Types.getModelFactory(schema.getModelClassName()), "factory")
                                    .addAnnotation(Specs.buildNonNullAnnotationSpec())
                                    .build()
                    )
                    .addStatement("return $L.createModel($L, factory)", connection, schemaInstance)
                    .build());

            methodSpecs.add(
                    MethodSpec.methodBuilder("relationOf" + simpleModelName)
                            .addJavadoc("Creates a relation of {@code $T}, which is an entry point of all the operations.\n", schema.getModelClassName())
                            .addAnnotation(Specs.buildNonNullAnnotationSpec())
                            .addModifiers(Modifier.PUBLIC)
                            .returns(schema.getRelationClassName())
                            .addStatement("return new $T($L, $L)",
                                    schema.getRelationClassName(),
                                    connection,
                                    schemaInstance)
                            .build());

            methodSpecs.add(
                    MethodSpec.methodBuilder("selectFrom" + simpleModelName)
                            .addJavadoc("Starts building a query: {@code SELECT * FROM $T ...}.\n", schema.getModelClassName())
                            .addAnnotation(Specs.buildNonNullAnnotationSpec())
                            .addModifiers(Modifier.PUBLIC)
                            .returns(schema.getSelectorClassName())
                            .addStatement("return new $T($L, $L)",
                                    schema.getSelectorClassName(),
                                    connection,
                                    schemaInstance)
                            .build());

            methodSpecs.add(
                    MethodSpec.methodBuilder("update" + simpleModelName)
                            .addJavadoc("Starts building a query: {@code UPDATE $T ...}.\n", schema.getModelClassName())
                            .addAnnotation(Specs.buildNonNullAnnotationSpec())
                            .addModifiers(Modifier.PUBLIC)
                            .returns(schema.getUpdaterClassName())
                            .addStatement("return new $T($L, $L)",
                                    schema.getUpdaterClassName(),
                                    connection,
                                    schemaInstance)
                            .build());

            methodSpecs.add(
                    MethodSpec.methodBuilder("deleteFrom" + simpleModelName)
                            .addJavadoc("Starts building a query: {@code DELETE FROM $T ...}.\n", schema.getModelClassName())
                            .addAnnotation(Specs.buildNonNullAnnotationSpec())
                            .addModifiers(Modifier.PUBLIC)
                            .returns(schema.getDeleterClassName())
                            .addStatement("return new $T($L, $L)",
                                    schema.getDeleterClassName(),
                                    connection,
                                    schemaInstance)
                            .build());

            methodSpecs.add(
                    MethodSpec.methodBuilder("insertInto" + simpleModelName)
                            .addJavadoc("Executes a query: {@code INSERT INTO $T ...}.\n", schema.getModelClassName())
                            .addModifiers(Modifier.PUBLIC)
                            .returns(long.class)
                            .addParameter(
                                    ParameterSpec.builder(schema.getModelClassName(), "model")
                                            .addAnnotation(Specs.buildNonNullAnnotationSpec())
                                            .build()
                            )
                            .addStatement("return prepareInsertInto$L().execute(model)",
                                    simpleModelName
                            )
                            .build());

            methodSpecs.add(
                    MethodSpec.methodBuilder("prepareInsertInto" + simpleModelName)
                            .addJavadoc("Create a prepared statement for {@code INSERT INTO $T ...}.\n",
                                    schema.getModelClassName())
                            .addModifiers(Modifier.PUBLIC)
                            .returns(Types.getInserter(schema.getModelClassName()))
                            .addStatement("return prepareInsertInto$L($T.NONE)",
                                    simpleModelName,
                                    OnConflict.class
                            )
                            .build());

            methodSpecs.add(
                    MethodSpec.methodBuilder("prepareInsertInto" + simpleModelName)
                            .addJavadoc("Create a prepared statement for {@code INSERT OR ... INTO $T ...}.\n",
                                    schema.getModelClassName())
                            .addModifiers(Modifier.PUBLIC)
                            .addParameter(ParameterSpec.builder(int.class, "onConflictAlgorithm")
                                    .addAnnotation(OnConflict.class)
                                    .build())
                            .returns(Types.getInserter(schema.getModelClassName()))
                            .addStatement("return new $T($L, $L, $L.getInsertStatement(onConflictAlgorithm))",
                                    Types.getInserter(schema.getModelClassName()),
                                    connection,
                                    schemaInstance,
                                    schemaInstance
                            )
                            .build());
        });

        return methodSpecs;
    }

    public List<MethodSpec> buildConstructorSpecs() {
        List<MethodSpec> methodSpecs = new ArrayList<>();

        methodSpecs.add(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(
                        ParameterSpec.builder(Types.OrmaConnection, connection)
                                .addAnnotation(Specs.buildNonNullAnnotationSpec())
                                .build())
                .addStatement("this.$L = $L", connection, connection)
                .build());

        return methodSpecs;
    }
}
