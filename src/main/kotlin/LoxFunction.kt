class LoxFunction(private val declaration: Stmt.Function, private val closure: Environment,
private  val isInstance: Boolean): LoxCallable {
    override fun invoke(interpreter: Interpreter, arguments: List<Any?>): Any? {
        val environment = Environment(closure)
        for((i, param) in declaration.params.withIndex()) {
            environment.define(param.lexeme, arguments[i])
        }
        try {
            interpreter.executeBlock(declaration.body, environment)
        } catch (returnValue: Return) {
            if(isInstance)
                return closure.getAt(0, "this")
            return returnValue.value
        }
        if(isInstance) {
            return closure.getAt(0, "this")
        }
        return null
    }
    override val arity = declaration.params.size
    override fun toString() = "<fn ${declaration.name.lexeme}>"
    fun bind(instance: LoxInstance): LoxFunction {
        val environment = Environment(closure)
        environment.define("this", instance)
        return LoxFunction(declaration, environment, isInstance)
    }
}