package com.quocdat.lingolens.assessment

import com.quocdat.lingolens.common.ApiResponse
import com.quocdat.lingolens.user.User
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class LessonController(private val lessons: LessonService) {
    @GetMapping("/curriculum") fun catalog(@AuthenticationPrincipal user: User) = ApiResponse.success("Curriculum loaded",lessons.catalog(user.id!!))
    @PostMapping("/curriculum/courses/{id}/enroll") fun enroll(@AuthenticationPrincipal user: User,@PathVariable id: UUID): ApiResponse<Unit> {
        lessons.enroll(user.id!!,id); return ApiResponse.success("Course enrolled")
    }
    @GetMapping("/curriculum/lessons/{id}") fun lesson(@AuthenticationPrincipal user: User,@PathVariable id: UUID) = ApiResponse.success("Lesson loaded",lessons.lesson(user.id!!,id))
    @PostMapping("/lesson-attempts") fun submit(@AuthenticationPrincipal user: User,@Valid @RequestBody request: SubmitLessonRequest) = ApiResponse.success("Attempt confirmed",lessons.submit(user.id!!,request))
    @GetMapping("/lesson-attempts/{id}") fun result(@AuthenticationPrincipal user: User,@PathVariable id: UUID) = ApiResponse.success("Attempt loaded",lessons.result(user.id!!,id))
}
