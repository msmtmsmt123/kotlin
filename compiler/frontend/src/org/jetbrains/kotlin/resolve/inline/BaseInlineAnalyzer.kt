/*
 * Copyright 2010-2016 JetBrains s.r.o.
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

package org.jetbrains.kotlin.resolve.inline

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingTrace

open class BaseInlineAnalyzer {
    protected fun checkModalityAndOverrides(
            functionOrPropertyDescriptor: CallableMemberDescriptor,
            functionOrProperty: KtCallableDeclaration,
            trace: BindingTrace) {
        if (functionOrPropertyDescriptor.containingDeclaration is PackageFragmentDescriptor) {
            return
        }

        if (Visibilities.isPrivate(functionOrPropertyDescriptor.visibility)) {
            return
        }

        val overridesAnything = functionOrPropertyDescriptor.overriddenDescriptors.isNotEmpty()

        if (overridesAnything) {
            val ktTypeParameters = functionOrProperty.typeParameters
            for (typeParameter in functionOrPropertyDescriptor.typeParameters) {
                if (typeParameter.isReified) {
                    val ktTypeParameter = ktTypeParameters[typeParameter.index]
                    val reportOn = ktTypeParameter.modifierList?.getModifier(KtTokens.REIFIED_KEYWORD) ?: ktTypeParameter
                    trace.report(Errors.REIFIED_TYPE_PARAMETER_IN_OVERRIDE.on(reportOn))
                }
            }
        }

        if (functionOrPropertyDescriptor.isEffectivelyFinal()) {
            if (overridesAnything) {
                trace.report(Errors.OVERRIDE_BY_INLINE.on(functionOrProperty))
            }
            return
        }

        trace.report(Errors.DECLARATION_CANT_BE_INLINED.on(functionOrProperty))
    }

    private fun CallableMemberDescriptor.isEffectivelyFinal(): Boolean =
            modality == Modality.FINAL ||
            containingDeclaration.let { containingDeclaration ->
                containingDeclaration is ClassDescriptor && containingDeclaration.modality == Modality.FINAL
            }

    protected fun notSupportedInInlineCheck(descriptor: CallableMemberDescriptor, functionOrProperty: KtCallableDeclaration, trace: BindingTrace) {
        val visitor = object : KtVisitorVoid() {
            override fun visitKtElement(element: KtElement) {
                super.visitKtElement(element)
                element.acceptChildren(this)
            }

            override fun visitClass(klass: KtClass) {
                trace.report(Errors.NOT_YET_SUPPORTED_IN_INLINE.on(klass, klass, descriptor))
            }

            override fun visitNamedFunction(function: KtNamedFunction) {
                if (function.parent.parent is KtObjectDeclaration) {
                    super.visitNamedFunction(function)
                }
                else {
                    trace.report(Errors.NOT_YET_SUPPORTED_IN_INLINE.on(function, function, descriptor))
                }
            }
        }

        functionOrProperty.acceptChildren(visitor)
    }
}

