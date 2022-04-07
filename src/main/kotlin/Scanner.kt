class Scanner(private val source: String) {
    private var start = 0
    private var current = 0
    private var line = 1
    private val tokens = mutableListOf<Token>()

    private val keywords = mapOf(
        "and" to TokenType.And,
        "class" to TokenType.Class,
        "else" to TokenType.Else,
        "false" to TokenType.False,
        "for" to TokenType.For,
        "fun" to TokenType.Fun,
        "if" to TokenType.If,
        "nil" to TokenType.Nil,
        "or" to TokenType.Or,
        "print" to TokenType.Print,
        "return" to TokenType.Return,
        "super" to TokenType.Super,
        "this" to TokenType.This,
        "true" to TokenType.True,
        "var" to TokenType.Var,
        "while" to TokenType.While
    )

    fun scanTokens(): List<Token> {
        while(!isAtEnd()) {
            start = current
            scanToken()
        }
        addToken(TokenType.EOF)
        return tokens
    }

    private fun scanToken() = when(val c = advance()) {
        '(' -> addToken(TokenType.LParen)
        ')' -> addToken(TokenType.RParen)
        '{' -> addToken(TokenType.LBrace)
        '}' -> addToken(TokenType.RBrace)
        ',' -> addToken(TokenType.Comma)
        '.' -> addToken(TokenType.Dot)
        '-' -> addToken(TokenType.Minus)
        '+' -> addToken(TokenType.Plus)
        ';' -> addToken(TokenType.Semicolon)
        '*' -> addToken(TokenType.Star)
        '!' -> addToken(if(match('=')) TokenType.BangEq else TokenType.Bang)
        '=' -> addToken(if(match('=')) TokenType.EqEq else TokenType.Eq)
        '<' -> addToken(if(match('=')) TokenType.LessEq else TokenType.Less)
        '>' -> addToken(if(match('=')) TokenType.GreaterEq else TokenType.Greater)

        '/' -> {
            if (match('/')) {
                while (peek() != '\n' && !isAtEnd()) advance()
            } else {
                addToken(TokenType.Slash)
            }
        }

        in setOf(' ' , '\r', '\t') -> {}// Ignore whitespace.
        '\n' -> line += 1


        '"' -> string()

        in '0'..'9' -> number()

        else -> {
            if(c.isAlpha()) identifier()
            else
                Lox.error(line, "Unexpected character ${source[current-1]}")
        }
    }

    private fun string() {
        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n') line += 1
            advance()
        }

        if (isAtEnd()) Lox.error(line, "Unterminated string.")
        advance()

        val value = source.substring(start + 1, current - 1)
        addToken(TokenType.String, value)
    }

    private fun number() {
        while (peek() in '0'..'9') advance()
        if(peek() == '.' && peekNext() in '0'..'9') {
            advance()
            while (peek() in '0'..'9') advance()
        }
        val value = source.substring(start, current).toDoubleOrNull() ?:
            Lox.error(line, "Invalid number.")
        addToken(TokenType.Number, value)
    }

    private fun identifier() {
        while (peek().isAlphaNumeric()) advance()
        val value = source.substring(start, current)
        val type = keywords[value] ?: TokenType.Identifier
        addToken(type)
    }

    private fun Char.isAlpha() = this in 'a'..'z' || this in 'A'..'Z' || this == '_'
    private fun Char.isAlphaNumeric() = isAlpha() || this in '0'..'9'


    private fun addToken(type: TokenType, literal: Any? = null) {
        val text = source.substring(start, current)
        tokens.add(Token(type, text, literal, line))
    }

    private fun advance() = source[current++]

    private fun match(expected: Char): Boolean {
        if(isAtEnd() || source[current] != expected) {
            return false
        }
        current++
        return true
    }

    private fun peek(): Char {
        if(isAtEnd()) return '\u0000'
        return source[current]
    }

    private fun peekNext(): Char {
        if(current + 1 >= source.length) return '\u0000'
        return source[current + 1]
    }

    private fun isAtEnd() = current == source.length
}

enum class TokenType {
    LParen, RParen, LBrace, RBrace, Comma, Dot, Minus, Plus, Semicolon, Slash, Star,

    Bang, BangEq, Eq, EqEq, Greater, GreaterEq, Less, LessEq,

    Identifier, String, Number,

    And, Class, Else, False, Fun, For, If, Nil, Or, Print, Return, Super, This, True, Var, While,

    EOF
    ;
}

data class Token(val type: TokenType, val lexeme: String, val literal: Any?, val line: Int)