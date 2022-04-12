class LoxClass(private val name: String, val methods: HashMap<String, LoxFunction>, val superclass: LoxClass? = null): LoxCallable {
    override fun invoke(interpreter: Interpreter, arguments: List<Any?>): LoxInstance {
        val instance = LoxInstance(this)
        val initializer = methods["init"]
        if(initializer != null) {
            initializer.bind(instance)(interpreter, arguments)
        }
        return instance
    }

    fun findMethod(name: Token): LoxFunction? {
        if(methods[name.lexeme] != null) {
            return methods[name.lexeme]
        }
        if(superclass != null) {
            return superclass.findMethod(name)
        }
        return null
    }
        override val arity = methods["init"]?.arity ?: 0
        override fun toString(): String = name
}
