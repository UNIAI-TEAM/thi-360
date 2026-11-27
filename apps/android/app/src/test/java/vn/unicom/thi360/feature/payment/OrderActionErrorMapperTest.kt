package vn.unicom.thi360.feature.payment

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test
import vn.unicom.thi360.core.network.ApiError
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.feature.payment.domain.OrderActionErrorKey
import vn.unicom.thi360.feature.payment.domain.OrderActionErrorMapper

class OrderActionErrorMapperTest {

    @Test
    fun mapsAllBranchesIdenticallyToWeb() {
        assertEquals(OrderActionErrorKey.RATE_LIMITED, OrderActionErrorMapper.map(429, null))
        assertEquals(OrderActionErrorKey.RATE_LIMITED, OrderActionErrorMapper.map(400, "RATE_LIMITED"))
        assertEquals(OrderActionErrorKey.PRODUCT_DISCONTINUED, OrderActionErrorMapper.map(410, null))
        assertEquals(OrderActionErrorKey.PRODUCT_DISCONTINUED, OrderActionErrorMapper.map(409, "PRODUCT_DISCONTINUED"))
        assertEquals(OrderActionErrorKey.PRODUCT_NOT_FOUND, OrderActionErrorMapper.map(404, "PRODUCT_NOT_FOUND"))
        assertEquals(OrderActionErrorKey.ORDER_NOT_FOUND, OrderActionErrorMapper.map(404, "ORDER_NOT_FOUND"))
        assertEquals(OrderActionErrorKey.ORDER_NOT_FOUND, OrderActionErrorMapper.map(404, "OTHER_404"))
        assertEquals(OrderActionErrorKey.ORDER_NOT_FOUND, OrderActionErrorMapper.map(404, null))
        assertEquals(OrderActionErrorKey.ORDER_NOT_FOUND, OrderActionErrorMapper.map(400, "ORDER_NOT_FOUND"))
        assertEquals(OrderActionErrorKey.ORDER_EXPIRED, OrderActionErrorMapper.map(409, "ORDER_EXPIRED"))
        assertEquals(OrderActionErrorKey.ORDER_NOT_PENDING, OrderActionErrorMapper.map(409, "ORDER_NOT_PENDING"))
        assertEquals(OrderActionErrorKey.PAYMENT_ATTEMPTS_EXCEEDED, OrderActionErrorMapper.map(422, "PAYMENT_ATTEMPTS_EXCEEDED"))
        assertEquals(OrderActionErrorKey.GENERIC, OrderActionErrorMapper.map(500, "INTERNAL_ERROR"))
        assertEquals(OrderActionErrorKey.GENERIC, OrderActionErrorMapper.map(400, "UNKNOWN_CODE"))
        assertEquals(
            OrderActionErrorKey.GENERIC,
            OrderActionErrorMapper.mapFailure(ApiResult.NetworkFailure(IOException("offline"))),
        )
        assertEquals(
            OrderActionErrorKey.ORDER_EXPIRED,
            OrderActionErrorMapper.mapFailure(ApiResult.HttpFailure(409, ApiError(code = "ORDER_EXPIRED"))),
        )
    }
}
