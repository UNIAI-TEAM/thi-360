package vn.unicom.thi360.core.storage

interface TokenStore {
    fun read(): Tokens?
    fun write(tokens: Tokens)
    fun clear()
}
