/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.transformers.irToJs

import org.jetbrains.kotlin.ir.backend.js.JsIntrinsics
import org.jetbrains.kotlin.ir.backend.js.utils.JsGenerationContext
import org.jetbrains.kotlin.ir.backend.js.utils.Namer
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.js.backend.ast.*

typealias IrCallTransformer = (IrCall, context: JsGenerationContext) -> JsExpression

class JsIntrinsicTransformers(intrinsics: JsIntrinsics) {
    private val transformers: Map<IrSymbol, IrCallTransformer>

    init {
        transformers = mutableMapOf()

        transformers.apply {
            binOp(intrinsics.jsEqeqeq, JsBinaryOperator.REF_EQ)
            binOp(intrinsics.jsNotEqeq, JsBinaryOperator.REF_NEQ)
            binOp(intrinsics.jsEqeq, JsBinaryOperator.EQ)
            binOp(intrinsics.jsNotEq, JsBinaryOperator.NEQ)

            binOp(intrinsics.jsGt, JsBinaryOperator.GT)
            binOp(intrinsics.jsGtEq, JsBinaryOperator.GTE)
            binOp(intrinsics.jsLt, JsBinaryOperator.LT)
            binOp(intrinsics.jsLtEq, JsBinaryOperator.LTE)

            prefixOp(intrinsics.jsNot, JsUnaryOperator.NOT)

            prefixOp(intrinsics.jsUnaryPlus, JsUnaryOperator.POS)
            prefixOp(intrinsics.jsUnaryMinus, JsUnaryOperator.NEG)

            prefixOp(intrinsics.jsPrefixInc, JsUnaryOperator.INC)
            postfixOp(intrinsics.jsPostfixInc, JsUnaryOperator.INC)
            prefixOp(intrinsics.jsPrefixDec, JsUnaryOperator.DEC)
            postfixOp(intrinsics.jsPostfixDec, JsUnaryOperator.DEC)

            binOp(intrinsics.jsPlus, JsBinaryOperator.ADD)
            binOp(intrinsics.jsMinus, JsBinaryOperator.SUB)
            binOp(intrinsics.jsMult, JsBinaryOperator.MUL)
            binOp(intrinsics.jsDiv, JsBinaryOperator.DIV)
            binOp(intrinsics.jsMod, JsBinaryOperator.MOD)

            binOp(intrinsics.jsBitAnd, JsBinaryOperator.BIT_AND)
            binOp(intrinsics.jsBitOr, JsBinaryOperator.BIT_OR)
            binOp(intrinsics.jsBitXor, JsBinaryOperator.BIT_XOR)
            prefixOp(intrinsics.jsBitNot, JsUnaryOperator.BIT_NOT)

            binOp(intrinsics.jsBitShiftR, JsBinaryOperator.SHR)
            binOp(intrinsics.jsBitShiftRU, JsBinaryOperator.SHRU)
            binOp(intrinsics.jsBitShiftL, JsBinaryOperator.SHL)

            binOp(intrinsics.jsInstanceOf, JsBinaryOperator.INSTANCEOF)

            add(intrinsics.jsObjectCreate) { call, _ ->
                val classToCreate = call.getTypeArgument(0)!!
                val prototype = prototypeOf(classToCreate.constructor.declarationDescriptor!!.name.toJsName().makeRef())
                JsInvocation(Namer.JS_OBJECT_CREATE_FUNCTION, prototype)

            }

            addIfNotNull(intrinsics.jsCode) { call, context ->
                val jsCode = translateJsCode(call, context.currentScope)

                when (jsCode) {
                    is JsExpression -> jsCode
                    // TODO don't generate function for this case
                    else -> JsInvocation(JsFunction(context.currentScope, jsCode as? JsBlock ?: JsBlock(jsCode as JsStatement), ""))
                }
            }
        }
    }

    operator fun get(symbol: IrSymbol): IrCallTransformer? = transformers[symbol]
}

private fun MutableMap<IrSymbol, IrCallTransformer>.add(function: IrFunction, t: IrCallTransformer) {
    put(function.symbol, t)
}

private fun MutableMap<IrSymbol, IrCallTransformer>.addIfNotNull(symbol: IrSymbol?, t: IrCallTransformer) {
    if (symbol == null) return
    put(symbol, t)
}

private fun MutableMap<IrSymbol, IrCallTransformer>.binOp(function: IrFunction, op: JsBinaryOperator) {
    withTranslatedArgs(function) { JsBinaryOperation(op, it[0], it[1]) }
}

private fun MutableMap<IrSymbol, IrCallTransformer>.prefixOp(function: IrFunction, op: JsUnaryOperator) {
    withTranslatedArgs(function) { JsPrefixOperation(op, it[0]) }
}

private fun MutableMap<IrSymbol, IrCallTransformer>.postfixOp(function: IrFunction, op: JsUnaryOperator) {
    withTranslatedArgs(function) { JsPostfixOperation(op, it[0]) }
}

private inline fun MutableMap<IrSymbol, IrCallTransformer>.withTranslatedArgs(
    function: IrFunction,
    crossinline t: (List<JsExpression>) -> JsExpression
) {
    put(function.symbol) { call, context -> t(translateCallArguments(call, context)) }
}
