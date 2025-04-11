package de.feleuxens.calculator

import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ParserTest {
    @Test
    fun parseLiterals_correct1() {
        val result = parseLiterals("223,4")
        println("Result: $result")
        assertEquals(listOf(Literal.Num(223.4)), result)
    }

    @Test
    fun parseLiterals_correct2() {
        val result = parseLiterals("223,4+4")
        println("Result: $result")
        assertEquals(listOf(Literal.Num(223.4), Literal.Lit('+'), Literal.Num(4)), result)
    }

    @Test
    fun parseLiterals_correct3() {
        val result = parseLiterals("223,4+4*2(3-21.1231)")
        println("Result: $result")
        assertEquals(listOf(
            Literal.Num(223.4),
            Literal.Lit('+'),
            Literal.Num(4),
            Literal.Lit('*'),
            Literal.Num(2),
            Literal.Lit('('),
            Literal.Num(3),
            Literal.Lit('-'),
            Literal.Num(21.1231),
            Literal.Lit(')')
        ), result)
    }

    @Test
    fun parseExpression_basicExpression() {
        val parser = LiteralParser(mutableListOf(
            Literal.Num(123),
            Literal.Lit('+'),
            Literal.Num(1.2)
        ))
        val result = parser.parse()
        println(result)
        assertEquals(Expr.BinaryOp('+', Expr.Num(123.0), Expr.Num(1.2)), result)
    }

    @Test
    fun parseExpression_testPrecedence() {
        val parser = LiteralParser(mutableListOf(
            Literal.Num(123),
            Literal.Lit('+'),
            Literal.Num(1),
            Literal.Lit('*'),
            Literal.Num(3)
        ))
        val result = parser.parse()
        println(result)
        assertEquals(Expr.BinaryOp('+', Expr.Num(123), Expr.BinaryOp('*', Expr.Num(1), Expr.Num(3))), result)
    }

    @Test
    fun parseExpression_testPrecedenceParentheses() {
        val parser = LiteralParser(mutableListOf(
            Literal.Lit('('),
            Literal.Num(123),
            Literal.Lit('+'),
            Literal.Num(1),
            Literal.Lit(')'),
            Literal.Lit('*'),
            Literal.Num(3)
        ))
        val result = parser.parse()
        println(result)
        assertEquals(Expr.BinaryOp('*', Expr.BinaryOp('+', Expr.Num(123), Expr.Num(1)), Expr.Num(3)), result)
    }

    @Test
    fun parseExpression_testPrecedenceUnary() {
        val parser = LiteralParser(mutableListOf(
            Literal.Num(123),
            Literal.Lit('+'),
            Literal.Num(1),
            Literal.Lit('*'),
            Literal.Lit('-'),
            Literal.Num(3)
        ))
        val result = parser.parse()
        println(result)
        assertEquals(Expr.BinaryOp('+', Expr.Num(123), Expr.BinaryOp('*', Expr.Num(1), Expr.UnaryOp('-', Expr.Num(3)))), result)
    }

    @Test
    fun parseExpression_wholeEvaluationBasic() {
        val literals = parseLiterals("1+3-2*24+(43-2)")
        val parser = LiteralParser(literals)
        val tree = parser.parse()
        val result = evaluateExpression(tree)
        println(tree)
        println("Result: $result")
    }
}