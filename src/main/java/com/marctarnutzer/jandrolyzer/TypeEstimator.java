//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 16.11.2018.
//  Copyright © 2018 Marc Tarnutzer. All rights reserved.
//

package com.marctarnutzer.jandrolyzer;

import com.github.javaparser.ast.expr.*;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.types.ResolvedType;

import java.util.Arrays;
import java.util.HashSet;

public class TypeEstimator {

    public static String estimateTypeName(Expression expr) {
        String typeString = null;

        try {
            ResolvedType resolvedType = expr.calculateResolvedType();

            if (resolvedType.isPrimitive()) {
                typeString = resolvedType.asPrimitive().getBoxTypeQName();
            } else {
                typeString = resolvedType.asReferenceType().getQualifiedName();
            }
        } catch (UnsolvedSymbolException e) {
            typeString = e.getName();
            System.out.println("[NOT RESOLVED] was no resolved but estimated to type: " + typeString + " Exception: " + e);
        }  catch (Exception e) {
            typeString = null;

            if (expr instanceof ObjectCreationExpr) {
                typeString = ((ObjectCreationExpr) expr).getType().asString();
            }

            System.out.println("[NOT RESOLVED] Exception: " + e);
        }

        return typeString;
    }

    public static boolean extendsCollection(String typeString) {
        return collectionHashSet.contains(typeString);
    }

    // TODO: extend this set with all interfaces and classes which extend Collection
    static HashSet<String> collectionHashSet = new HashSet<>(Arrays.asList(
            "java.util.List",
            "java.util.ArrayList",
            "java.util.Set"));

}
