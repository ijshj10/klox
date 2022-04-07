class Resolver(private val interpreter: Interpreter): Expr.Visitor<Unit>, Stmt.Visitor<Unit> {
    private val scopes = mutableListOf<HashMap<String, Boolean>>()
    private var currentFunction = FunctionType.None

    private fun resolve(expr: Expr) = expr.accept(this)
    private fun resolve(stmt: Stmt) = stmt.accept(this)
    fun resolve(statements: List<Stmt>) {
        for(statement in statements) {
            resolve(statement)
        }
    }

    private fun beginScope() = scopes.add(HashMap())
    private fun endScope() = scopes.removeLast()

    private fun declare(name: Token) {
        if(scopes.isEmpty()) return
        val scope = scopes.last()
        if(name.lexeme in scope) {
            Lox.error(name, "Already a variable exists with this name in this scope.")
        }
        scope[name.lexeme] = false
    }

    private fun define(name: Token) {
        if(scopes.isEmpty()) return

        val scope = scopes.last()
        scope[name.lexeme] = true
    }

    private fun resolveLocal(expr: Expr, name: Token) {
        for((i, scope) in scopes.reversed().withIndex()) {
            if(name.lexeme in scope) {
                interpreter.resolve(expr, i)
                return
            }
        }
    }

    private fun resolveFunction(function: Stmt.Function) {
        val enclosingFunction = currentFunction
        currentFunction = FunctionType.Function
        beginScope()
        for (param in function.params) {
            declare(param)
            define(param)
        }
        resolve(function.body)
        endScope()
        currentFunction = enclosingFunction
    }

    override fun visitExpressionStmt(stmt: Stmt.Expression) = resolve(stmt.expr)
    override fun visitPrintStmt(stmt: Stmt.Print) = resolve(stmt.expr)

    override fun visitVarStmt(stmt: Stmt.Var) {
        declare(stmt.name)
        if(stmt.initializer != null) {
            resolve(stmt.initializer)
        }
        define(stmt.name)
    }

    override fun visitBlockStmt(stmt: Stmt.Block) {
        beginScope()
        resolve(stmt.statements)
        endScope()
    }

    override fun visitIfStmt(stmt: Stmt.If) {
        resolve(stmt.condition)
        resolve(stmt.ifBranch)
        if(stmt.elseBranch != null)
            resolve(stmt.elseBranch)
    }

    override fun visitWhileStmt(stmt: Stmt.While) {
        resolve(stmt.condition)
        resolve(stmt.body)
    }

    override fun visitFunctionStmt(stmt: Stmt.Function) {
        declare(stmt.name)
        define(stmt.name)

        resolveFunction(stmt)
    }

    override fun visitReturnStmt(stmt: Stmt.Return) {
        if(currentFunction == FunctionType.None) {
            Lox.error(stmt.keyword, "Can't return from top-level code.")
        }
        if(stmt.value != null)
            resolve(stmt.value)
    }

    override fun visitBinaryExpr(expr: Expr.Binary) {
        resolve(expr.l)
        resolve(expr.r)
    }

    override fun visitGroupingExpr(expr: Expr.Grouping) {
        resolve(expr.expr)
    }

    override fun visitLiteralExpr(expr: Expr.Literal) {}

    override fun visitUnaryExpr(expr: Expr.Unary) {
        resolve(expr.right)
    }

    override fun visitVariableExpr(expr: Expr.Variable) {
        if(scopes.isNotEmpty() && scopes.last()[expr.name.lexeme] == false) {
            Lox.error(expr.name, "Can't read local variable in its own initializer.")
        }
        resolveLocal(expr, expr.name)
    }

    override fun visitAssignExpr(expr: Expr.Assign) {
        resolve(expr.value)
        resolveLocal(expr, expr.name)
    }

    override fun visitLogicalExpr(expr: Expr.Logical) {
        resolve(expr.l)
        resolve(expr.r)
    }

    override fun visitCallExpr(expr: Expr.Call) {
        resolve(expr.callee)
        for(argument in expr.arguments) {
            resolve(argument)
        }
    }
}

enum class FunctionType {
    None, Function
}