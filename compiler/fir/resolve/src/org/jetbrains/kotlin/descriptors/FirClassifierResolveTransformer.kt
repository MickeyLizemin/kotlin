/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.descriptors

import org.jetbrains.kotlin.fir.FirDescriptorOwner
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.UnambiguousFqName
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.impl.FirResolvedClassImpl
import org.jetbrains.kotlin.fir.descriptors.ConeClassifierDescriptor
import org.jetbrains.kotlin.fir.descriptors.ConeTypeParameterDescriptor
import org.jetbrains.kotlin.fir.resolve.FirTypeResolver
import org.jetbrains.kotlin.fir.scopes.FirImportingScope
import org.jetbrains.kotlin.fir.scopes.impl.FirCompositeImportingScope
import org.jetbrains.kotlin.fir.scopes.impl.FirExplicitImportingScope
import org.jetbrains.kotlin.fir.scopes.impl.FirSelfImportingScope
import org.jetbrains.kotlin.fir.types.FirResolvedType
import org.jetbrains.kotlin.fir.types.FirType
import org.jetbrains.kotlin.fir.types.impl.FirResolvedTypeImpl
import org.jetbrains.kotlin.fir.visitors.CompositeTransformResult
import org.jetbrains.kotlin.fir.visitors.FirTransformer
import org.jetbrains.kotlin.fir.visitors.compose
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.FqNameUnsafe

class FirClassifierResolveTransformer : FirTransformer<Nothing?>() {
    override fun <E : FirElement> transformElement(element: E, data: Nothing?): CompositeTransformResult<E> {
        return element.compose()
    }

    lateinit var packageFqName: FqNameUnsafe

    override fun transformFile(file: FirFile, data: Nothing?): CompositeTransformResult<FirFile> {
        packageFqName = file.packageFqName.toUnsafe()
        return file.also { it.transformChildren(this, null) }.compose()
    }

    var className: FqName = FqName.ROOT

    override fun transformClass(klass: FirClass, data: Nothing?): CompositeTransformResult<FirDeclaration> {
        val actualClassName = className.child(klass.name)
        className = actualClassName

        klass.transformChildren(this, data)

        val superTypes = klass.superTypes.map {
            (it as FirResolvedType).type
        }

        className = className.parent()

        val typeParameters =
            klass.typeParameters.filterIsInstance<FirDescriptorOwner<*>>().mapNotNull { it.descriptor as? ConeTypeParameterDescriptor }

        val nestedClassifiers =
            klass.declarations.filterIsInstance<FirDescriptorOwner<*>>().mapNotNull { it.descriptor as? ConeClassifierDescriptor }

        val descriptor = ConeClassDescriptorImpl(
            typeParameters,
            UnambiguousFqName(packageFqName, actualClassName),
            superTypes,
            nestedClassifiers
        )

        return FirResolvedClassImpl(klass, descriptor).compose()
    }

    override fun transformResolvedClass(resolvedClass: FirResolvedClass, data: Nothing?): CompositeTransformResult<FirDeclaration> {
        return resolvedClass.compose()
    }
}