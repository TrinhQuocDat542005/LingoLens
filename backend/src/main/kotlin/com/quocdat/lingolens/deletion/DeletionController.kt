package com.quocdat.lingolens.deletion

import com.quocdat.lingolens.common.ApiResponse
import com.quocdat.lingolens.user.User
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

data class DeleteAccountRequest(val password:String,val code:String)
data class ReceiptStatusRequest(val code:String)

@RestController
class DeletionController(private val service:DeletionService) {
    @PostMapping("/api/v1/users/me/deletion")
    fun delete(@AuthenticationPrincipal user:User,@RequestBody body:DeleteAccountRequest)=
        ApiResponse.success("Deletion confirmed",service.delete(requireNotNull(user.id),body.password,body.code))
    @PostMapping("/api/v1/account-deletion/status")
    fun status(@RequestBody body:ReceiptStatusRequest)=ApiResponse.success("Deletion confirmation status",service.status(body.code))
}
