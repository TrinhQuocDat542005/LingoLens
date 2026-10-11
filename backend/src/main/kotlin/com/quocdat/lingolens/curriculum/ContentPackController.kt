package com.quocdat.lingolens.curriculum

import com.quocdat.lingolens.common.ApiResponse
import com.quocdat.lingolens.user.User
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/curriculum")
class ContentPackController(private val packs: ContentPackService) {
    @GetMapping("/courses/{id}/manifest") fun manifest(@AuthenticationPrincipal user:User,@PathVariable id:UUID):ApiResponse<com.quocdat.lingolens.assessment.core.PackManifest> {
        requireNotNull(user.id); return ApiResponse.success("Pack manifest loaded",packs.manifest(id))
    }
    @GetMapping("/revisions/{id}/pack",produces=["application/json"])
    fun pack(@AuthenticationPrincipal user:User,@PathVariable id:UUID):ByteArray { requireNotNull(user.id); return packs.pack(id) }
}
