package vn.unicom.thi360.core.session

import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.storage.Tokens

interface MobileAuthGateway {
    suspend fun login(identifier: String, password: String): ApiResult<Tokens>
    suspend fun refresh(refreshToken: String): ApiResult<Tokens>
    suspend fun logout(refreshToken: String): ApiResult<Unit>
}
