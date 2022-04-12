import kotlin.RuntimeException

class Parser(private val tokens: List<Token>) {
    private var index = 0

    fun parse() = ArrayList<Stmt>().apply {
        while(!isAtEnd()) {
            val stmt = declaration()
            if (stmt != null) {
                add(stmt)
            }
        }
    }

    private fun declaration(): Stmt? {
        try {
            if (match(TokenType.Var)) {
                return varDeclaration()
            }
            if (match(TokenType.Fun)) {
                return function("function")
            }
            if (match(TokenType.Class)) {
                return classDeclaration()
            }
            return statement()
        } catch (_: ParseError) {
            synchronize()
            return null
        }
    }

    private fun varDeclaration(): Stmt {
        consume(TokenType.Identifier, "Expect variable name.")
        val name = previous()
        var initializer: Expr? = null
        if (match(TokenType.Eq)) {
            initializer = expression()
        }
        consume(TokenType.Semicolon, "Expect ';' after variable declaration.")
        return Stmt.Var(name, initializer)
    }

    private fun classDeclaration(): Stmt {
        val name = consume(TokenType.Identifier, "Expect class name.")
        var superclass: Expr.Variable? = null
        if(match(TokenType.Less)) {
            consume(TokenType.Identifier, "Expect superclass name.")
            superclass = Expr.Variable(previous())
        }
        consume(TokenType.LBrace, "Expect '{' before class body.")

        val methods = mutableListOf<Stmt.Function>()
        while(!check(TokenType.RBrace) && !isAtEnd()) {
            methods.add(function("method"))
        }

        consume(TokenType.RBrace, "Expect '}' after class body.")

        return Stmt.Class(name, methods, superclass)
    }

    private fun function(kind: String): Stmt.Function {
        val name = consume(TokenType.Identifier, "Expect $kind name.")
        consume(TokenType.LParen, "Expect '(' after $kind name.")
        val parameters = arrayListOf<Token>()
        if(!check(TokenType.RParen)) {
            do {
                if (parameters.size >= 255) {
                    error(peek(), "Can't have more than 255 parameters.")
                }
                parameters.add(consume(TokenType.Identifier, "Expect parameter name."))
            } while(match(TokenType.Comma))
        }
        consume(TokenType.RParen, "Expect ')' after parameters.")

        consume(TokenType.LBrace, "Expect '{' before $kind body.")
        val body = block()
        return Stmt.Function(name, parameters, body)
    }


    private fun statement() =
        if (match(TokenType.Print)) {
            printStatement()
        } else if(match(TokenType.Return)){
            returnStatement()
        } else if (match(TokenType.LBrace)) {
            Stmt.Block(block())
        } else if(match(TokenType.If)) {
            ifStatement()
        } else if(match(TokenType.While)){
            whileStatement()
        } else if(match(TokenType.For)){
            forStatement()
        } else {
            expressionStatement()
        }

    private fun expressionStatement(): Stmt {
        val expr = expression()
        consume(TokenType.Semicolon, "Expect ';' after expression.")
        return Stmt.Expression(expr)
    }

    private fun returnStatement(): Stmt {
        val keyword = previous()
        var value: Expr? = null
        if(!check(TokenType.Semicolon)) {
            value = expression()
        }
        consume(TokenType.Semicolon, "Expect ';' after return statement.")
        return Stmt.Return(keyword, value)
    }

    private fun forStatement(): Stmt {
        consume(TokenType.LParen, "Expect '(' after for")

        var initializer: Stmt? = null
        if (!match(TokenType.Semicolon)) {
            if(match(TokenType.Var)) {
                initializer = varDeclaration()
            } else {
                initializer = expressionStatement()
            }
        }

        var condition: Expr? = null
        if(!check(TokenType.Semicolon)) {
            condition = expression()
        }
        consume(TokenType.Semicolon, "Expect ';' after condition.")


        var increment: Expr? = null
        if(!check(TokenType.RParen)) {
            increment = expression()
        }
        consume(TokenType.RParen, "Expect ')' after for clauses.")

        var body = statement()
        if(increment != null) {
            body = Stmt.Block(listOf(body, Stmt.Expression(increment)))
        }
        if(condition == null) {
            condition = Expr.Literal(true)
        }
        var statement: Stmt = Stmt.While(condition, body)
        if(initializer != null) {
            statement = Stmt.Block(listOf(initializer, statement))
        }
        return statement
    }

    private fun block(): List<Stmt> {
        val statements = ArrayList<Stmt>()
        while(!check(TokenType.RBrace) && !isAtEnd()) {
            val stmt = declaration()
            if (stmt != null) {
                statements.add(stmt)
            }
        }
        consume(TokenType.RBrace, "Expect '}' after block.")
        return statements
    }

    private fun whileStatement(): Stmt {
        consume(TokenType.LParen, "Expect '(' after 'while'")
        val condition = expression()
        consume(TokenType.RParen, "Expect ')' after while condition")
        val body = statement()
        return Stmt.While(condition, body)
    }

    private fun ifStatement(): Stmt {
        consume(TokenType.LParen, "Expect '(' after 'if'.")
        val condition = expression()
        consume(TokenType.RParen, "Expect ')' after if condition")
        val thenBranch = statement()
        var elseBranch: Stmt? = null
        if(match(TokenType.Else)) {
            elseBranch = statement()
        }
        return Stmt.If(condition, thenBranch, elseBranch)
    }

    private fun printStatement(): Stmt {
        val expr = expression()
        consume(TokenType.Semicolon, "Expect ';' after value.")
        return Stmt.Print(expr)
    }

    private fun expression(): Expr {
        return assignment()
    }

    private fun leftAssociativeBinaryExpr(next: () -> Expr, vararg types: TokenType): Expr {
        var expr = next()
        while(match(*types)) {
            val operator = previous()
            val right = next()
            expr = Expr.Binary(expr, operator, right)
        }
        return expr
    }

    private fun assignment(): Expr {
        var expr = or()
        if(match(TokenType.Eq)) {
            val equals = previous()
            val value = assignment()
            when (expr) {
                is Expr.Variable -> {
                    expr = Expr.Assign(expr.name, value)
                }
                is Expr.Get -> {
                    expr = Expr.Set(expr.obj, expr.name, value)
                }
                else -> {
                    error(equals, "Invalid assignment target.")
                }
            }
        }
        return expr
    }

    private fun or(): Expr {
        var expr = and()
        while(match(TokenType.Or)) {
            val op = previous()
            val right = and()
            expr = Expr.Logical(expr, op, right)
        }
        return expr
    }

    private fun and(): Expr {
        var expr = equality()
        while(match(TokenType.And)) {
            val op = previous()
            val right = equality()
            expr = Expr.Logical(expr, op, right)
        }
        return expr
    }

    private fun equality() = leftAssociativeBinaryExpr(this::comparison,
        TokenType.EqEq, TokenType.EqEq)

    private fun comparison() = leftAssociativeBinaryExpr(this::term,
        TokenType.Less, TokenType.LessEq, TokenType.Greater, TokenType.GreaterEq)

    private fun term() = leftAssociativeBinaryExpr(this::factor,
        TokenType.Plus, TokenType.Minus)

    private fun factor() = leftAssociativeBinaryExpr(this::unary,
        TokenType.Slash, TokenType.Star)

    private fun unary(): Expr {
        if(match(TokenType.Bang, TokenType.Minus)) {
            val operator = previous()
            val right = unary()
            return Expr.Unary(operator, right)
        }
        return call()
    }

    private fun finishCall(callee: Expr): Expr {
        val arguments = arrayListOf<Expr>()
        if(!check(TokenType.RParen)) {
            do {
                if(arguments.size > 255)
                    error(peek(), "Can't have more than 255 arguments.")
                arguments.add(expression())
            }   while (match(TokenType.Comma))
        }
        val paren = consume(TokenType.RParen, "Expect ')' after arguments")
        return Expr.Call(callee, paren, arguments)
    }

    private fun call(): Expr {
        var expr = primary()
        while(true) {
            expr = if(match(TokenType.LParen)) {
                finishCall(expr)
            } else if(match(TokenType.Dot)) {
                val name = consume(TokenType.Identifier, "Expect property name after '.'")
                Expr.Get(expr, name)
            } else {
                break
            }
        }
        return expr
    }

    private fun primary(): Expr {
        if(match(TokenType.False)) return Expr.Literal(false)
        if(match(TokenType.True)) return Expr.Literal(true)
        if(match(TokenType.Nil)) return Expr.Literal(null)
        if(match(TokenType.This)) return Expr.This(previous())
        if(match(TokenType.Super)) {
            val keyword = previous()
            consume(TokenType.Dot, "Expect '.' after 'super'.")
            val method = consume(TokenType.Identifier, "Expect superclass method name.")
            return Expr.Super(keyword, method)
        }
        if(match(TokenType.Number, TokenType.String)) {
            return Expr.Literal(previous().literal!!)
        }
        if(match(TokenType.Identifier)) {
            return Expr.Variable(previous())
        }
        if(match(TokenType.LParen)) {
            val expr = expression()
            consume(TokenType.RParen, "Expect ')' after expression.")
            return Expr.Grouping(expr)
        }
        throw error(peek(), "Expect expression.")
    }

    private fun previous() = tokens[index-1]
    private fun advance() = if(index < tokens.size - 1) tokens[index++] else tokens[index]
    private fun peek() = tokens[index]
    private fun isAtEnd() = index == tokens.size - 1
    private fun check(type: TokenType) = peek().type == type
    private fun match(vararg types: TokenType): Boolean {
        val token = peek()
        for(type in types) {
            if (token.type == type) {
                advance()
                return true
            }
        }
        return false
    }
    private fun consume(type: TokenType, message: String): Token {
        if(!match(type)) {
            throw error(peek(), message)
        }
        return previous()
    }

    private class ParseError: RuntimeException()
    private fun error(token: Token, message: String): ParseError {
        Lox.error(token, message)
        return ParseError()
    }

    private fun synchronize() {
        advance()
        while(!isAtEnd()) {
            if(previous().type == TokenType.Semicolon) return
            when(peek().type) {
                TokenType.Class, TokenType.Fun, TokenType.Var,
                TokenType.For, TokenType.If, TokenType.While,
                TokenType.Print, TokenType.Return -> return
                else -> {}
            }
            advance()
        }

    }

}

sealed interface Stmt {
    fun <R> accept(visitor: Visitor<R>): R
    data class Expression(val expr: Expr): Stmt {
        override fun <R> accept(visitor: Visitor<R>): R = visitor.visitExpressionStmt(this)
    }
    data class Print(val expr: Expr): Stmt {
        override fun <R> accept(visitor: Visitor<R>): R = visitor.visitPrintStmt(this)
    }
    data class Block(val statements: List<Stmt>): Stmt {
        override fun <R> accept(visitor: Visitor<R>): R = visitor.visitBlockStmt(this)
    }

    data class Var(val name: Token, val initializer: Expr?): Stmt {
        override fun <R> accept(visitor: Visitor<R>): R = visitor.visitVarStmt(this)
    }

    data class If(val condition: Expr, val ifBranch: Stmt, val elseBranch: Stmt?): Stmt {
        override fun <R> accept(visitor: Visitor<R>): R = visitor.visitIfStmt(this)
    }

    data class While(val condition: Expr, val body: Stmt): Stmt {
        override fun <R> accept(visitor: Visitor<R>): R = visitor.visitWhileStmt(this)
    }
    data class Function(val name: Token, val params: List<Token>, val body: List<Stmt>): Stmt {
        override fun <R> accept(visitor: Visitor<R>): R = visitor.visitFunctionStmt(this)
    }
    data class Return(val keyword: Token, val value: Expr?): Stmt {
        override fun <R> accept(visitor: Visitor<R>): R = visitor.visitReturnStmt(this)
    }
    data class Class(val name: Token, val methods: List<Function>, val superclass: Expr.Variable? = null): Stmt {
        override fun <R> accept(visitor: Visitor<R>): R = visitor.visitClassStmt(this)
    }

    interface Visitor<R> {
        fun visitExpressionStmt(stmt: Expression): R
        fun visitPrintStmt(stmt: Print): R
        fun visitVarStmt(stmt: Var): R
        fun visitBlockStmt(stmt: Block): R
        fun visitIfStmt(stmt: If): R
        fun visitWhileStmt(stmt: While): R
        fun visitFunctionStmt(stmt: Function): R
        fun visitReturnStmt(stmt: Return): R
        fun visitClassStmt(stmt: Class): R
    }
}

sealed interface Expr {
    fun <R> accept(visitor: Visitor<R>): R
    data class Binary(val l: Expr, val op: Token, val r: Expr): Expr {
        override fun <R> accept(visitor: Visitor<R>): R = visitor.visitBinaryExpr(this)
    }
    data class Grouping(val expr: Expr): Expr {
        override fun <R> accept(visitor: Visitor<R>): R = visitor.visitGroupingExpr(this)
    }
    data class Literal(val value: Any?): Expr {
        override fun <R> accept(visitor: Visitor<R>): R = visitor.visitLiteralExpr(this)
    }
    data class Unary(val op: Token, val right: Expr): Expr {
        override fun <R> accept(visitor: Visitor<R>): R = visitor.visitUnaryExpr(this)
    }
    data class Variable(val name: Token): Expr {
        override fun <R> accept(visitor: Visitor<R>): R = visitor.visitVariableExpr(this)
    }
    data class Assign(val name: Token, val value: Expr): Expr {
        override fun <R> accept(visitor: Visitor<R>): R = visitor.visitAssignExpr(this)
    }
    data class Logical(val l: Expr, val op: Token, val r: Expr): Expr {
        override fun <R> accept(visitor: Visitor<R>): R = visitor.visitLogicalExpr(this)
    }
    data class This(val keyword: Token): Expr {
        override fun <R> accept(visitor: Visitor<R>) = visitor.visitThisExpr(this)
    }
    class Call(val callee: Expr, val paren: Token, val arguments: List<Expr>) : Expr {
        override fun <R> accept(visitor: Visitor<R>): R = visitor.visitCallExpr(this)
    }

    class Get(val obj: Expr, val name: Token) : Expr {
        override fun <R> accept(visitor: Visitor<R>): R = visitor.visitGetExpr(this)
    }

    class Set(val obj: Expr, val name: Token, val value: Expr) : Expr {
        override fun <R> accept(visitor: Visitor<R>): R = visitor.visitSetExpr(this)
    }

    class Super(val keyword: Token, val method: Token) : Expr {
        override fun <R> accept(visitor: Visitor<R>) = visitor.visitSuperExpr(this)
    }

    interface Visitor<R> {
        fun visitBinaryExpr(expr: Binary): R
        fun visitGroupingExpr(expr: Grouping): R
        fun visitLiteralExpr(expr: Literal): R
        fun visitUnaryExpr(expr: Unary): R
        fun visitVariableExpr(expr: Variable): R
        fun visitAssignExpr(expr: Assign): R
        fun visitLogicalExpr(expr: Logical): R
        fun visitCallExpr(expr: Call): R
        fun visitGetExpr(expr: Get): R
        fun visitSetExpr(expr: Set): R
        fun visitThisExpr(expr: This): R
        fun visitSuperExpr(expr: Super): R
    }




}
