package com.quocdat.lingolens.lesson

import android.os.Bundle
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],manifest=Config.NONE)
class LessonScreenModelTest {
    class StateOwner : ComponentActivity()
    private val course=CourseDto("course","Demo","DEMO","lesson","revision",false)
    private class Fake : LessonGateway {
        val calls=mutableListOf<AttemptRequestDto>()
        var failures=0
        var wait: CompletableDeferred<Unit>?=null
        override suspend fun catalog()=CatalogDto(listOf(CourseDto("course","Demo","DEMO","lesson","revision",false)),30)
        override suspend fun enroll(courseId: String) {}
        override suspend fun lesson(id: String)=LessonDto("lesson","revision","First","Introduction","DEMO",listOf(PatternDto("Hello","Hello","Xin chào")),
            listOf(WordDto("hello","xin chào","/həˈloʊ/","Interjection","A greeting","Hello","A1")),
            (1..8).map { QuestionDto("q$it","Question $it",it,listOf(OptionDto("A","Correct",1),OptionDto("B","Wrong",2))) })
        override suspend fun submit(request: AttemptRequestDto): AttemptResultDto {
            calls+=request; wait?.await()
            if (failures-- > 0) throw IOException("Offline")
            return AttemptResultDto(request.id,"lesson","LESSON",8,8,100,true,30)
        }
    }
    private suspend fun answered(model: LessonScreenModel) {
        model.open(course); model.showQuestions()
        (1..8).forEach { model.choose("q$it","A") }
    }
    @Test fun `network retry freezes UUID timestamp and answers`()=runBlocking {
        val fake=Fake().apply { failures=1 }; val model=LessonScreenModel(SavedStateHandle(),fake,1)
        answered(model); model.submit()
        assertNotNull(model.state.value.error)
        model.choose("q1","B")
        model.submit()
        assertEquals(2,fake.calls.size); assertEquals(fake.calls[0],fake.calls[1])
        assertEquals("SUMMARY",model.state.value.stage)
    }
    @Test fun `double submit sends once while request is in flight`()=runBlocking {
        val fake=Fake().apply { wait=CompletableDeferred() }; val model=LessonScreenModel(SavedStateHandle(),fake,1)
        answered(model)
        val first=launch { model.submit() }; yield(); model.submit()
        assertEquals(1,fake.calls.size)
        fake.wait!!.complete(Unit); first.join()
        assertEquals(1,fake.calls.size)
    }
    private fun model(owner: StateOwner, fake: Fake): LessonScreenModel {
        return ViewModelProvider(owner,LessonScreenModel.factory(fake,1))[LessonScreenModel::class.java]
    }
    @Test fun `rotation retains attempt UUID answers and current question`()=runBlocking {
        val fake=Fake(); val controller=Robolectric.buildActivity(StateOwner::class.java).setup()
        try {
            val first=model(controller.get(),fake); first.open(course); first.showQuestions(); first.choose("q1","A"); first.next()
            val id=first.state.value.attemptId
            val config=Configuration(controller.get().resources.configuration)
            config.orientation=if (config.orientation==Configuration.ORIENTATION_LANDSCAPE) Configuration.ORIENTATION_PORTRAIT else Configuration.ORIENTATION_LANDSCAPE
            controller.configurationChange(config)
            val after=model(controller.get(),fake)
            assertEquals(id,after.state.value.attemptId); assertEquals("A",after.state.value.answers["q1"]); assertEquals(1,after.state.value.questionIndex)
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun `system saved state recreation retries identical payload without old ViewModel`()=runBlocking {
        val fake=Fake().apply { failures=1 }; val old=Robolectric.buildActivity(StateOwner::class.java).setup()
        val first=model(old.get(),fake); answered(first); first.submit()
        val sent=fake.calls.single(); val saved=Bundle()
        old.pause().saveInstanceState(saved).stop().destroy()
        val fresh=Robolectric.buildActivity(StateOwner::class.java).create(saved).start().resume()
        try {
            val restored=model(fresh.get(),fake)
            assertNotSame(first,restored); assertEquals(sent.id,restored.state.value.attemptId)
            restored.submit(); assertEquals(sent,fake.calls.last())
        } finally { fresh.pause().stop().destroy() }
    }
    @Test fun `explicit new attempt creates a new UUID`()=runBlocking {
        val fake=Fake(); val model=LessonScreenModel(SavedStateHandle(),fake,1)
        answered(model); model.submit(); val old=model.state.value.attemptId
        model.restart(); assertNotEquals(old,model.state.value.attemptId)
        assertTrue(model.state.value.answers.isEmpty()); assertEquals("LEARN",model.state.value.stage)
    }
}
