package vn.unicom.thi360.core.storage

import kotlinx.serialization.Serializable

@Serializable
data class Tokens(
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresIn: Int,
    val refreshExpiresIn: Int,
    val mustChangePassword: Boolean,
    val username: String? = null,
    val accountId: String? = null,
) {
    override fun toString(): String =
        "Tokens(accessToken=[REDACTED], refreshToken=[REDACTED], accessExpiresIn=$accessExpiresIn, " +
            "refreshExpiresIn=$refreshExpiresIn, mustChangePassword=$mustChangePassword, " +
            "username=$username, accountId=$accountId)"
}
