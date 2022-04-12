class Resolver(private val interpreter: Interpreter): Expr.Visitor<Unit>, Stmt.Visitor<Unit> {
    private val scopes = mutableListOf<HashMap<String, Boolean>>()
    private var currentFunction = FunctionType.None
    private var currentClass = ClassType.Class

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

    private fun resolveFunction(function: Stmt.Function, type: FunctionType) {
        val enclosingFunction = currentFunction
        currentFunction = type
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

    override fun visitClassStmt(stmt: Stmt.Class) {
        val enclosingClass = currentClass
        declare(stmt.name)
        define(stmt.name)
        currentClass = ClassType.Class
        if(stmt.superclass != null) {
            currentClass = ClassType.Subclass
            beginScope()
            scopes.last()["super"] = true
            if(stmt.superclass.name.lexeme == stmt.name.lexeme) {
                Lox.error(stmt.superclass.name, "A class can't inherit from itself.")
            }
            resolve(stmt.superclass)
        }
        beginScope()

        scopes.last()["this"] =  true

        for (method in stmt.methods) {
            var declaration = FunctionType.Method
            if(method.name.lexeme == "init")
                declaration = FunctionType.Initializer
            resolveFunction(method, declaration)
        }

        endScope()
        if(stmt.superclass != null)
            endScope()
        currentClass = enclosingClass
    }

    override fun visitSuperExpr(expr: Expr.Super) {
        if (currentClass == ClassType.None) {
            Lox.error(expr.keyword,
                "Can't use 'super' outside of a class.")
        } else if (currentClass != ClassType.Subclass) {
            Lox.error(expr.keyword,
                "Can't use 'super' in a class with no superclass.")
        }
        resolveLocal(expr, expr.keyword)
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

        resolveFunction(stmt, FunctionType.Function)
    }

    override fun visitReturnStmt(stmt: Stmt.Return) {
        if(currentFunction == FunctionType.None) {
            Lox.error(stmt.keyword, "Can't return from top-level code.")
        }
        if(stmt.value != null) {
            if(currentFunction == FunctionType.Initializer) {
                Lox.error(stmt.keyword, "Can't return a value from an initializer.")
            }
            resolve(stmt.value)
        }


    }

    override fun visitBinaryExpr(expr: Expr.Binary) {
        resolve(expr.l)
        resolve(expr.r)
    }

    override fun visitGroupingExpr(expr: Expr.Grouping) {
        resolve(expr.expr)
    }

    override fun visitLiteralExpr(expr: Expr.Literal) {}

    override fun visitThisExpr(expr: Expr.This) {
        if(currentClass == ClassType.None) {
            Lox.error(expr.keyword, "Can't use 'this' outside of a class.")
        }
        resolveLocal(expr, expr.keyword)
    }

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

    override fun visitGetExpr(expr: Expr.Get) {
        resolve(expr.obj)
    }

    override fun visitSetExpr(expr: Expr.Set) {
        resolve(expr.value)
        resolve(expr.obj)
    }
}

enum class FunctionType {
    None, Function, Method, Initializer
}

enum class ClassType {
    None, Class, Subclass
}