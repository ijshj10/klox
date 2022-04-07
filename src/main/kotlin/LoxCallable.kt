interface LoxCallable {
    operator fun invoke(interpreter: Interpreter, arguments: List<Any?>): Any?
    val arity: Int
}

