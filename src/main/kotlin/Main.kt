import java.io.File

fun main(args: Array<String>) {
    if(args.size > 1) {
        println("Usage: klox [script]")
    } else if(args.size == 1) {
        Lox.runFile(args[0])
    } else {
        Lox.runPrompt()
    }
}

object Lox {
    fun runFile(fileName: String) {
        val bytes = File(fileName).readBytes()
        val interpreter = Interpreter()
        run(String(bytes), interpreter)
    }

    fun runPrompt() {
        val interpreter = Interpreter()
        while (true) {
            print("> ")
            val line = readLine()
            run(line ?: break, interpreter)
            hadError = false
        }
    }

    private var hadRuntimeError = false
    private var hadError = false

    private fun run(source: String, interpreter: Interpreter) {
        val scanner = Scanner(source)
        val tokens = scanner.scanTokens()
        val parser = Parser(tokens)
        val expression = parser.parse()
        val resolver = Resolver(interpreter)
        resolver.resolve(expression)
        if(hadError) return
        interpreter.interpret(expression)
    }

    fun error(line: Int, message: String) {
        report(line, "", message)
    }

    fun error(token: Token, message: String) {
        if (token.type == TokenType.EOF) {
            report(token.line, " at end ", message)
        } else {
            report(token.line, " at '${token.lexeme}'", message)
        }
    }

    private fun report(line: Int, where: String, message: String) {
        println("[line $line] Error $where: $message")
        hadError = true
    }

    fun runtimeError(error: RuntimeError) {
        println("[line ${error.token.line}] ${error.message}")
        hadRuntimeError = true
    }
}