import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

class Interpreter: Expr.Visitor<Any?>, Stmt.Visitor<Unit> {
    private val globals = Environment()
    private var env = globals
    private var locals = HashMap<Expr, Int>()

    init {
        globals.define("print", object: LoxCallable {
            override val arity = 1
            override fun invoke(interpreter: Interpreter, arguments: List<Any?>): Any? {
                println(interpreter.stringify(arguments[0]))
                return null
            }
            override fun toString() = "<native fn>"
        })
        globals.define("clock", object: LoxCallable {
            override val arity = 0
            override fun invoke(interpreter: Interpreter, arguments: List<Any?>): Any {
                return System.currentTimeMillis().toDouble() / 1000.0
            }
            override fun toString() = "<native fn>"
        })
    }

    fun interpret(statements: List<Stmt>) {
        try {
            for(stmt in statements) {
                execute(stmt)
            }
        } catch (error: RuntimeError) {
            Lox.runtimeError(error)
        }
    }

    private fun execute(stmt: Stmt) {
        stmt.accept(this)
    }

    internal fun executeBlock(statements: List<Stmt>, env: Environment) {
        val previous = this.env
        try {
            this.env = env
            for(stmt in statements) {
                execute(stmt)
            }
        } finally {
            this.env = previous
        }
    }

    override fun visitBinaryExpr(expr: Expr.Binary): Any? {
        val l = evaluate(expr.l)
        val r = evaluate(expr.r)
        return when(expr.op.type) {
            TokenType.Plus -> {
                if(l is Double && r is Double) {
                    l + r
                } else if(l is String && r is String) {
                    l + r
                } else {
                    throw RuntimeError(expr.op,
                        "Operands must be two numbers or two strings.")
                }
            }
            TokenType.Minus -> {
                checkNumberOperands(expr.op, l, r)
                l -r
            }
            TokenType.Slash -> {
                checkNumberOperands(expr.op, l, r)
                l / r
            }
            TokenType.Star -> {
                checkNumberOperands(expr.op, l, r)
                l * r
            }
            TokenType.Greater -> {
                checkNumberOperands(expr.op, l, r)
                l > r
            }
            TokenType.GreaterEq -> {
                checkNumberOperands(expr.op, l, r)
                l >= r
            }
            TokenType.Less -> {
                checkNumberOperands(expr.op, l, r)
                l < r
            }
            TokenType.LessEq -> {
                checkNumberOperands(expr.op, l, r)
                l <= r
            }
            TokenType.EqEq ->
                isEqual(l, r)
            TokenType.BangEq ->
                !isEqual(l, r)
            else -> null
        }
    }

    override fun visitGroupingExpr(expr: Expr.Grouping) = evaluate(expr.expr)

    override fun visitLiteralExpr(expr: Expr.Literal) = expr.value

    override fun visitUnaryExpr(expr: Expr.Unary): Any? {
        val right = evaluate(expr.right)
        return when(expr.op.type) {
            TokenType.Minus -> {
                checkNumberOperand(expr.op, right)
                -right
            }
            TokenType.Bang -> !isTruthy(right)
            else -> null
        }
    }

    override fun visitAssignExpr(expr: Expr.Assign): Any? {
        val value = evaluate(expr.value)
        val distance = locals[expr]
        if (distance != null) {
            env.assignAt(distance, expr.name, value)
        } else {
            globals.assign(expr.name, value)
        }
        return value
    }

    override fun visitLogicalExpr(expr: Expr.Logical): Any? {
        val left = evaluate(expr.l)
        if(expr.op.type == TokenType.Or) {
            if(isTruthy(left))
                return left
        } else {
            if(!isTruthy(left))
                return left
        }
        return evaluate(expr.r)
    }


    @OptIn(ExperimentalContracts::class)
    private fun checkNumberOperand(operator: Token, operand: Any?) {
        contract { returns() implies (operand is Double) }
        if(operand !is Double) {
            throw RuntimeError(operator, "Operand must be a number.")
        }
    }

    @OptIn(ExperimentalContracts::class)
    private fun checkNumberOperands(operator: Token, left: Any?, right: Any?) {
        contract {
            returns() implies (left is Double && right is Double)
        }
        if(left is Double && right is Double) {
            return
        }
        throw RuntimeError(operator, "Operands must be numbers.")
    }

    override fun visitVariableExpr(expr: Expr.Variable) = lookUpVariable(expr.name, expr)


    private fun lookUpVariable(name: Token, expr: Expr): Any? {
        val distance = locals[expr]
        return if(distance != null) {
            env.getAt(distance, name)
        } else {
            globals.get(name)
        }
    }

    override fun visitCallExpr(expr: Expr.Call): Any? {
        val callee = evaluate(expr.callee)
        val arguments = arrayListOf<Any?>()
        for(argument in expr.arguments) {
            arguments.add(evaluate(argument))
        }
        if(callee !is LoxCallable) {
            throw RuntimeError(expr.paren, "'${stringify(callee)}' is not callable.")
        }
        if(arguments.size != callee.arity) {
            throw RuntimeError(expr.paren,
                "Expected ${callee.arity} arguments but got ${arguments.size} arguments.")
        }
        return callee(this, arguments)
    }

    private fun evaluate(expr: Expr) = expr.accept(this)

    /*
    * False and Null is false
     */
    private fun isTruthy(value: Any?) =
        if(value is Any) {
            if(value is Boolean) value
            else true
        } else {
            false
        }

    private fun isEqual(a: Any?, b: Any?): Boolean {
        if(a == null && b == null) return true
        if(a == null) return false
        return a == b
    }

    fun stringify(value: Any?): String {
        return when(value) {
            is Boolean -> if(value) "true" else "false"
            null -> "nil"
            else -> {
                val text = value.toString()
                if(text.endsWith(".0")) {
                    text.substring(0, text.length - 2)
                } else {
                    text
                }
            }
        }
    }

    override fun visitExpressionStmt(stmt: Stmt.Expression) {
        evaluate(stmt.expr)
    }

    override fun visitPrintStmt(stmt: Stmt.Print) {
        val value = evaluate(stmt.expr)
        println(stringify(value))
    }

    override fun visitFunctionStmt(stmt: Stmt.Function) {
        globals.define(stmt.name.lexeme, LoxFunction(stmt, env))
    }

    override fun visitReturnStmt(stmt: Stmt.Return) {
        var value: Any? = null
        if(stmt.value != null) {
            value = evaluate(stmt.value)
        }
        throw Return(value)
    }

    override fun visitWhileStmt(stmt: Stmt.While) {
        while(isTruthy(evaluate(stmt.condition))) {
            execute(stmt.body)
        }
    }

    override fun visitIfStmt(stmt: Stmt.If) {
        val condtion = evaluate(stmt.condition)
        if(isTruthy(condtion)) {
            execute(stmt.ifBranch)
        } else if(stmt.elseBranch != null) {
            execute(stmt.elseBranch)
        }
    }

    override fun visitBlockStmt(stmt: Stmt.Block) {
        executeBlock(stmt.statements, Environment(env))
    }

    override fun visitVarStmt(stmt: Stmt.Var) {
        val value = if (stmt.initializer != null)
                        evaluate(stmt.initializer)
                        else null
        env.define(stmt.name.lexeme, value)
    }

    fun resolve(expr: Expr, depth: Int) {
        locals[expr] = depth
    }
}