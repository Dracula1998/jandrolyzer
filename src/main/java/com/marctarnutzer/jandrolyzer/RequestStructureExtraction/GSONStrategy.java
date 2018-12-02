//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 19.11.2018.
//  Copyright © 2018 Marc Tarnutzer. All rights reserved.
//

package com.marctarnutzer.jandrolyzer.RequestStructureExtraction;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserFieldDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserSymbolDeclaration;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.marctarnutzer.jandrolyzer.JSONDataType;
import com.marctarnutzer.jandrolyzer.JSONObject;
import com.marctarnutzer.jandrolyzer.JSONRoot;
import com.marctarnutzer.jandrolyzer.TypeEstimator;

import java.util.Map;
import java.util.Set;

public class GSONStrategy {

    CombinedTypeSolver combinedTypeSolver;

    public void extract(Node node, String path, Map<String, JSONRoot> jsonModels, CombinedTypeSolver combinedTypeSolver) {
        this.combinedTypeSolver = combinedTypeSolver;

        if (node instanceof MethodCallExpr) {
            if (((MethodCallExpr) node).getName().asString().equals("addProperty")) {
                jsonObjectExtraction(node, path, jsonModels);
            } else if (((MethodCallExpr) node).getName().asString().equals("toJson")) {
                pojoToJsonExtraction(node, path, jsonModels);
            }
        }
    }

    private void jsonObjectExtraction(Node node, String path, Map<String, JSONRoot> jsonModels) {
        System.out.println("Found a addProperty at path: " + path);
        // No example found as of now...
    }

    private void pojoToJsonExtraction(Node node, String path, Map<String, JSONRoot> jsonModels) {
        Expression scopeExpression = ((MethodCallExpr) node).getScope().orElse(null);
        if (scopeExpression != null) {
            String estimatedType = TypeEstimator.estimateTypeName(scopeExpression);

            if (estimatedType != null) {
                if (estimatedType.equals("com.google.gson.Gson") || estimatedType.equals("Gson") ||
                        estimatedType.equals("com.google.gson.GsonBuilder") || estimatedType.equals("GsonBuilder")) {

                    System.out.println("Found GSON / GsonBuilder node: " + node + " at path: " + path);

                    NodeList<Expression> arguments = ((MethodCallExpr) node).getArguments();
                    if (arguments.size() == 1) {
                        String argumentType = TypeEstimator.estimateTypeName(arguments.get(0));

                        System.out.println("Estimated argument type: " + argumentType);

                        if (argumentType != null) {
                            if (arguments.get(0) instanceof NameExpr) {
                                try {
                                    ResolvedValueDeclaration resolvedValueDeclaration =
                                            ((NameExpr)arguments.get(0)).resolve();

                                    Node declarationNode = null;

                                    if (resolvedValueDeclaration instanceof JavaParserSymbolDeclaration) {
                                        declarationNode =
                                                ((JavaParserSymbolDeclaration) resolvedValueDeclaration).getWrappedNode();
                                    } else if (resolvedValueDeclaration instanceof JavaParserFieldDeclaration) {
                                        declarationNode =
                                                ((JavaParserFieldDeclaration) resolvedValueDeclaration).getWrappedNode();
                                    }

                                    System.out.println("Declaring node: " + declarationNode.toString() + " class: " +
                                            declarationNode.getClass());

                                    if (declarationNode instanceof VariableDeclarator ||
                                            declarationNode instanceof FieldDeclaration) {
                                        Type declaratorType;

                                        if (declarationNode instanceof VariableDeclarator) {
                                            declaratorType = ((VariableDeclarator) declarationNode).getType();
                                        } else {
                                            declaratorType = ((FieldDeclaration) declarationNode).getElementType();
                                        }

                                        if (declaratorType.isClassOrInterfaceType()) {
                                            String modelPath = node.findCompilationUnit().get().getStorage().get()
                                                    .getSourceRoot() + "/" + declaratorType.asClassOrInterfaceType()
                                                    .resolve().getTypeDeclaration().getQualifiedName()
                                                    .replace(".", "/") + ".java";

                                            analyzeFields(declaratorType.asClassOrInterfaceType().resolve()
                                                    .getDeclaredFields(), modelPath,
                                                    declaratorType.asClassOrInterfaceType().getName().asString(),
                                                    jsonModels, null, null);
                                        }
                                    } else {
                                        System.out.println("Node is not a VariableDeclarator nor FieldDeclaration" +
                                                declarationNode.getClass());
                                    }
                                } catch (Exception e) {
                                    System.out.println("Exception: " + e);
                                }
                            } else {
                                System.out.println("Not a name expr: " + arguments.get(0).getClass());
                            }
                        }
                    }
                } else {
                    System.out.println("Wrong scope type: " + estimatedType + ", node: " + node + ", path: " + path);
                }
            } else {
                System.out.println("Unable to resolve scope expression type: " + scopeExpression.toString() + ", path: " + path);
            }
        }
    }

    private void analyzeFields(Set<ResolvedFieldDeclaration> resolvedFieldDeclarations, String modelPath,
                               String className, Map<String, JSONRoot> jsonModels, JSONRoot jsonRoot,
                               JSONObject jsonObject) {
        System.out.println("Analyzing fields in: " + modelPath);

        // Check if @Expose annotation is used
        boolean exposeUsed = false;
        for (ResolvedFieldDeclaration resolvedFieldDeclaration : resolvedFieldDeclarations) {
            FieldDeclaration fieldDeclaration = ((JavaParserFieldDeclaration)resolvedFieldDeclaration).getWrappedNode();
            if (fieldDeclaration.getAnnotations().size() > 0) {
                for (AnnotationExpr annotationExpr : fieldDeclaration.getAnnotations()) {
                    if (annotationExpr.getNameAsString().equals("Expose")) {
                        exposeUsed = true;
                    }
                }
            }
        }

        if (jsonObject == null && jsonRoot == null) {
            jsonRoot = new JSONRoot(modelPath, className, null, null);
        }

        for (ResolvedFieldDeclaration resolvedFieldDeclaration : resolvedFieldDeclarations) {
            System.out.println("Field: " + resolvedFieldDeclaration);
            //System.out.println("Type: " + resolvedFieldDeclaration.getType());

            FieldDeclaration fieldDeclaration = ((JavaParserFieldDeclaration)resolvedFieldDeclaration).getWrappedNode();

            // Don't include transient fields in JSON representation
            if (fieldDeclaration.isTransient()) {
                continue;
            }

            String keyString = resolvedFieldDeclaration.getName();

            // Check annotation for ignore
            boolean shouldSkip = true;
            if (fieldDeclaration.getAnnotations().size() > 0) {
                for (AnnotationExpr annotationExpr : fieldDeclaration.getAnnotations()) {
                    if (annotationExpr.getNameAsString().equals("SerializedName") && annotationExpr.isSingleMemberAnnotationExpr()) {
                        keyString = ((SingleMemberAnnotationExpr) annotationExpr).getMemberValue().asStringLiteralExpr().asString();
                        System.out.println("Annotation rule: Changed key to: " + keyString);
                    } else if (exposeUsed && annotationExpr.getNameAsString().equals("Expose")) {
                        shouldSkip = false;
                    }
                }
            }
            if (shouldSkip && exposeUsed) {
                continue;
            }

            if (jsonRoot != null && jsonRoot.jsonObject == null) {
                jsonRoot.jsonObject = new JSONObject(JSONDataType.OBJECT, null, null);
            }

            JSONObject toInsert = null;

            String fieldDeclarationTypeString = null;
            try {
                ResolvedType fieldDeclarationType = resolvedFieldDeclaration.getType();
            } catch (UnsolvedSymbolException e) {
                System.out.println("Unsolved exception: " + e);
                fieldDeclarationTypeString = e.getName();
            }

            if (fieldDeclarationTypeString != null) {
                if (fieldDeclarationTypeString.equals("String")) {
                    toInsert = new JSONObject(null, null, "java.lang.String");
                } else {
                    continue;
                }
            } else if (resolvedFieldDeclaration.getType().isPrimitive()) {
                String valueType = resolvedFieldDeclaration.getType().asPrimitive().getBoxTypeQName();

                if (valueType == null) {
                    continue;
                }

                toInsert = new JSONObject(null, null, valueType);
            } else if (resolvedFieldDeclaration.getType().isReferenceType()) {
                String resolvedReferenceTypeString = resolvedFieldDeclaration.getType().asReferenceType().
                        getQualifiedName();
                System.out.println("!TypeString" + resolvedReferenceTypeString);
                if (resolvedReferenceTypeString.equals("java.lang.String")
                        || resolvedReferenceTypeString.equals("java.lang.Double")
                        || resolvedReferenceTypeString.equals("java.lang.Long")
                        || resolvedReferenceTypeString.equals("java.lang.Integer")
                        || resolvedReferenceTypeString.equals("java.lang.Boolean")) {
                    toInsert = new JSONObject(null, null, resolvedReferenceTypeString);
                } else {
                    try {
                        Type fieldType = fieldDeclaration.getElementType();
                        System.out.println("Field declaration: " + fieldDeclaration + ", type: " + fieldType);

                        // TODO: Ask maintainers why this approach is necessary and why fieldType.resolve() doesn't work
                        ResolvedType resolvedType = JavaParserFacade.get(combinedTypeSolver).convertToUsage(fieldType);
                        System.out.println("Field type: " + resolvedType.asReferenceType());
                        System.out.println("Type fields: " + resolvedType.asReferenceType().getDeclaredFields());

                        System.out.println("Collection: " + (fieldType.asClassOrInterfaceType().getTypeArguments()));

                        if (TypeEstimator.extendsCollection(resolvedReferenceTypeString)) {
                            System.out.println("Its a collection type");
                            if (fieldType.asClassOrInterfaceType().getTypeArguments().isPresent() &&
                                    fieldType.asClassOrInterfaceType().getTypeArguments().get().size() == 1) {
                                System.out.println("Inserting JSON Array...");
                                toInsert = new JSONObject(JSONDataType.ARRAY, null, null);

                                Type typeArgumentType = fieldType.asClassOrInterfaceType().getTypeArguments().get().get(0);
                                ResolvedType resolvedTypeArgType = JavaParserFacade.get(combinedTypeSolver).convertToUsage(typeArgumentType);

                                analyzeFields(resolvedTypeArgType.asReferenceType().getDeclaredFields(), modelPath, className, jsonModels, null, toInsert);
                            }
                        } else {
                            toInsert = new JSONObject(JSONDataType.OBJECT, null, null);
                            analyzeFields(resolvedType.asReferenceType().getDeclaredFields(), modelPath, className, jsonModels
                                    , null, toInsert);
                        }
                    } catch (Exception e) {
                        System.out.println("Exception2: " + e);
                    }
                }
            }

            if (toInsert == null) {
                continue;
            }
            System.out.println("toInsert passed");

            if (jsonRoot != null) {
                System.out.println("Putting in jsonRoot... " + keyString);
                jsonRoot.jsonObject.linkedHashMap.put(keyString, toInsert);
            } else {
                System.out.println("Putting in jsonobject... " + keyString);
                jsonObject.linkedHashMap.put(keyString, toInsert);
                System.out.println("Putting in jsonobject complete.");
            }
        }

        if (jsonRoot != null && jsonRoot.jsonObject != null && !jsonRoot.jsonObject.linkedHashMap.isEmpty()) {
            jsonModels.put(jsonRoot.getIdentifier(), jsonRoot);
        }

    }

}
