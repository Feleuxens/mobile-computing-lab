package de.feleuxens.calculator

import android.util.Log
import androidx.collection.MutableIntList

fun evaluate(expression: String): Number {
    val literals = parseLiterals(expression.replace("\\s".toRegex(), ""))
    Log.i("literals: ", literals.toString())
    val parser = LiteralParser(literals)
    val tree = parser.parse()

    return evaluateExpression(tree)
}

fun evaluateExpression(tree: Expr): Number = when (tree) {
    is Expr.Num -> tree.value
    is Expr.UnaryOp -> {
        val value = evaluateExpression(tree.expr)
        when (tree.op) {
            '+' -> value
            '-' -> -value.toDouble()
            else -> throw IllegalArgumentException("Unkown unary operator: ${tree.op}")
        }
    }
    is Expr.BinaryOp -> {
        val left = evaluateExpression(tree.left)
        val right = evaluateExpression(tree.right)
        if (right.toDouble() == 0.0 && tree.op == '/')
            throw IllegalArgumentException("Division by zero.")
        when (tree.op) {
            '+' -> left.toDouble() + right.toDouble()
            '-' -> left.toDouble() - right.toDouble()
            '*' -> left.toDouble() * right.toDouble()
            '/' -> left.toDouble() / right.toDouble()
            else -> throw IllegalArgumentException("Unknown binary operator: ${tree.op}")
        }
    }
}

class LiteralParser(val tokens: List<Literal>) {
    private var pos = 0

    private fun current(): Literal? = tokens.getOrNull(pos)
    private fun advance() { pos++ }

    fun parse(): Expr {
        return parseExpression()
    }

    fun parseExpression(): Expr {
        return parseAddition()
    }

    private fun parseAddition(): Expr {
        var expr = parseMultiplication()
        while (true) {
            val op = (current() as? Literal.Lit)?.value
            if (op == '+' || op == '-') {
                advance()
                val right = parseMultiplication()
                expr = Expr.BinaryOp(op, expr, right)
            } else break
        }
        return expr
    }

    private fun parseMultiplication(): Expr {
        var expr = parseUnary()
        while (true) {
            val op = (current() as? Literal.Lit)?.value
            if (op == '*' || op == '/') {
                advance()
                val right = parseUnary()
                expr = Expr.BinaryOp(op, expr, right)
            } else break
        }
        return expr
    }

    private fun parseUnary(): Expr {
        val token = current()
        if (token is Literal.Lit && (token.value == '+' || token.value == '-')) {
            advance()
            return Expr.UnaryOp(token.value, parseUnary())
        }
        return parsePrimary()
    }

    private fun parsePrimary(): Expr {
        return when (val token = current()) {
            is Literal.Num -> {
                advance()
                Expr.Num(token.value)
            }
            is Literal.OpenParentheses -> {
                advance()
                val expr = parseExpression()
                val closing = current()
                if (closing !is Literal.CloseParentheses) {
                    throw IllegalArgumentException("Expected ')'")
                }
                advance()
                expr
            }
            else -> throw IllegalStateException("Unexpected token at pos $pos: $token")
        }
    }
}

private class Helpers {
    companion object {
        val nums: Array<Char> = arrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '.')
    }
}

sealed class Expr {
    data class Num(val value: Number) : Expr()
    data class UnaryOp(val op: Char, val expr: Expr) : Expr()
    data class BinaryOp(val op: Char, val left: Expr, val right: Expr): Expr()
}

sealed class Literal {
    data class Num(val value: Number) : Literal()
    data class Lit(val value: Char) : Literal()
    data class OpenParentheses(val value : Unit) : Literal()
    data class CloseParentheses(val value : Unit) : Literal()
}

fun parseLiterals(rawExpression: String): MutableList<Literal> {
    // replace , with . because toDouble fails with ,
    val expression = rawExpression.replace(',', '.').plus(' ')
    val literals: MutableList<Literal> = ArrayList()
    val skip = MutableIntList()
    for (i in expression.indices) {
        if (i in skip) {
            continue
        }
        if (expression[i] in Helpers.nums) {
            for (j in i+1..<expression.length) {
                skip.add(j-1)
                if (expression[j] !in Helpers.nums) {
                    val substring = expression.substring(i, j)
                    if (substring.contains('.')) {
                        literals.add(Literal.Num(substring.toDouble()))
                        break
                    } else {
                        literals.add(Literal.Num(substring.toInt()))
                        break
                    }
                }
            }
        } else if (expression[i] == ' ') {
            continue
        } else if (expression[i] == '(') {
            literals.add(Literal.OpenParentheses(Unit))
        } else if (expression[i] == ')') {
            literals.add(Literal.CloseParentheses(Unit))
        }
        else {
            literals.add(Literal.Lit(expression[i]))
        }
    }
    return literals
}

