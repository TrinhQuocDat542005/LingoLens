package com.quocdat.lingolens.deletion

import androidx.room.withTransaction
import com.quocdat.lingolens.data.local.LearningDatabase

class AccountDataCleaner(private val db:LearningDatabase,private val beforeFinish:()->Unit={}) {
    suspend fun purge(account:Long){
        require(account>0)
        db.withTransaction {
            for(table in listOf("AnalyticsOutbox","AnalyticsConsentState","LessonAttemptOutbox","LessonSession","ContentPack",
                "CurriculumAccountState","OutboxOperation","ReviewEvent","ReviewSession","ReviewCard","LearningEvent","SavedWordEntity","SyncAccount"))
                db.openHelper.writableDatabase.execSQL("DELETE FROM $table WHERE accountId=?",arrayOf(account))
            beforeFinish()
        }
    }
}
