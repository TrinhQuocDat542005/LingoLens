package com.quocdat.lingolens.topic

import com.quocdat.lingolens.common.ApiResponse
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*

data class PublishedTopic(val id:String,val slug:String,val title:String,val version:Int,val words:List<String>)

@Service
class TopicService(private val jdbc:JdbcTemplate) {
    @Transactional(readOnly=true)
    fun published():List<PublishedTopic> = jdbc.query(
        "SELECT id,slug,title FROM topics WHERE is_published=TRUE AND slug='objects' ORDER BY display_order,id",
        {row,_->PublishedTopic(row.getString("id"),row.getString("slug"),row.getString("title"),1,
            jdbc.queryForList("SELECT v.word FROM topic_vocabularies tv JOIN vocabularies v ON v.id=tv.vocabulary_id WHERE tv.topic_id=? AND v.is_published=TRUE ORDER BY tv.display_order,v.word",String::class.java,row.getObject("id")))})
}

@RestController
@RequestMapping("/api/v1/topics")
class TopicController(private val service:TopicService) {
    @GetMapping fun published()=ApiResponse.success("Published topic mapping loaded",service.published())
}
