package com.quocdat.lingolens.analytics

import com.quocdat.lingolens.common.ApiResponse
import com.quocdat.lingolens.user.User
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController @RequestMapping("/api/v1")
class AnalyticsController(private val analytics:AnalyticsService) {
    @GetMapping("/analytics/consent") fun consent(@AuthenticationPrincipal user:User,@RequestHeader(value="X-LingoLens-Account",required=false) account:Long?):ApiResponse<ConsentState>{
        fence(user,account);return ApiResponse.success("Analytics consent loaded",analytics.consent(user.id!!))
    }
    @PutMapping("/analytics/consent") fun update(@AuthenticationPrincipal user:User,@Valid @RequestBody request:ConsentRequest,@RequestHeader(value="X-LingoLens-Account",required=false) account:Long?):ApiResponse<ConsentState>{
        fence(user,account);return ApiResponse.success("Analytics consent updated",analytics.updateConsent(user.id!!,request))
    }
    @PostMapping("/analytics/events/batch") fun batch(@AuthenticationPrincipal user:User,@Valid @RequestBody request:BatchRequest,@RequestHeader(value="X-LingoLens-Account",required=false) account:Long?):ApiResponse<BatchResult>{
        fence(user,account);return ApiResponse.success("Analytics batch processed",analytics.batch(user.id!!,request))
    }
    @GetMapping("/admin/analytics/retention") fun retention()=ApiResponse.success("Observed 90-day retention",analytics.retention())
    private fun fence(user:User,expected:Long?){if(expected!=null && expected!=user.id)throw AnalyticsRequestException(HttpStatus.FORBIDDEN,"ACCOUNT_MISMATCH","Account does not match authenticated session")}
}
