import java.io.File

fun main(args: Array<String>) {
    if(args.size > 1) {
        println("Usage: klox [script]")
    } else if(args.size == 1) {
        runFile(args[0])
    } else {
        runPrompt()
    }
}

fun runFile(fileName: String) {
    val bytes = File(fileName).readBytes()
    run(String(bytes))
}

fun runPrompt() {
    while (true) {
        print("> ")
        val line = readLine()
        run(line ?: break)
        hadError = false
    }
}

var hadError = false

fun run(source: String) {
    val scanner = Scanner(source)
    val tokens = scanner.scanTokens()
    for (token in tokens) {
        println(token)
    }
}

fun error(line: Int, message: String) {
    report(line, "", message)
}

fun report(line: Int, where: String, message: String) {
    println("[line $line] Error $where: $message")
    hadError = true
}
