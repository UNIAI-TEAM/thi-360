package vn.unicom.thi360.feature.password

sealed interface NewPasswordMode {
    data class FromLink(val token: String) : NewPasswordMode {
        init {
            require(token.isNotBlank()) { "Reset token must not be blank" }
        }

        override fun toString(): String = "FromLink(token=[REDACTED])"
    }

    data class Forced(val username: String? = null) : NewPasswordMode

    data class Voluntary(val username: String? = null) : NewPasswordMode
}
