package com.quocdat.lingolens.curriculum

import com.fasterxml.jackson.databind.JsonNode
import com.quocdat.lingolens.assessment.core.LessonGrader
import java.util.Locale
import java.util.UUID

object SeedValidator {
    fun validate(root:JsonNode) {
        fields(root,"schemaVersion","packId","contentStatus","pronunciationLocale","course","topic","unit","vocabularies","lessons")
        number(root,"schemaVersion",1,1);id(root,"packId")
        require(text(root,"contentStatus",20) in setOf("DEMO","REVIEWED"))
        require(text(root,"pronunciationLocale",10)=="en-US")
        val course=root["course"];fields(course,"id","code","title","description","level")
        id(course);text(course,"code",60);text(course,"title",150);text(course,"description",2000);require(text(course,"level",10)=="A1")
        val topic=root["topic"];fields(topic,"id","slug","title","description","iconKey","displayOrder")
        id(topic);text(topic,"slug",80);text(topic,"title",120);text(topic,"description",2000);text(topic,"iconKey",50);number(topic,"displayOrder",0,1000)
        val unit=root["unit"];fields(unit,"id","courseId","topicId","title","position")
        id(unit);require(id(unit,"courseId")==id(course));require(id(unit,"topicId")==id(topic));text(unit,"title",150);number(unit,"position",1,1000)
        val words=array(root,"vocabularies",15,18)
        words.forEach { word ->
            fields(word,"word","translation","phonetic","partOfSpeech","definition","example","level")
            val value=text(word,"word",100);require(value==value.trim().lowercase(Locale.ROOT))
            text(word,"translation",255);text(word,"phonetic",100);text(word,"partOfSpeech",50);text(word,"definition",2000);text(word,"example",2000);require(text(word,"level",10)=="A1")
        }
        unique(words.map{it["word"].asText()})
        val allIds=mutableListOf(id(root,"packId"),id(course),id(topic),id(unit))
        val lessons=array(root,"lessons",3,3)
        unique(lessons.map{number(it,"position",1,3)});require(lessons.map{it["position"].asInt()}.sorted()==listOf(1,2,3))
        lessons.forEach { lesson ->
            fields(lesson,"id","unitId","title","position","estimatedMinutes","revision","vocabularyWords","questions")
            allIds+=id(lesson);require(id(lesson,"unitId")==id(unit));text(lesson,"title",150);number(lesson,"estimatedMinutes",1,30)
            val revision=lesson["revision"];fields(revision,"id","revisionNo","introduction","sentencePatterns")
            allIds+=id(revision);number(revision,"revisionNo",1,1000);text(revision,"introduction",4000)
            array(revision,"sentencePatterns",1,2).forEach{fields(it,"pattern","example","translation");text(it,"pattern",500);text(it,"example",500);text(it,"translation",500)}
            val vocabulary=array(lesson,"vocabularyWords",5,8).map{require(it.isTextual);it.asText()}
            unique(vocabulary);require(vocabulary.all{word->words.any{it["word"].asText()==word}})
            val questions=array(lesson,"questions",8,10)
            unique(questions.map{number(it,"position",1,questions.size)})
            val mixed=questions.any{it["type"]?.asText()!="MEANING_CHOICE"}
            if(mixed)require(questions.size==10){"Mixed lesson requires ten questions"}
            require(questions.count{it["type"]?.asText()=="LISTEN_CHOICE"}<=2){"At most two listening questions per lesson"}
            questions.forEach { q ->
                val type=text(q,"type",20);require(type in setOf("MEANING_CHOICE","LISTEN_CHOICE","FILL_GAP"))
                val required=listOf("id","type","prompt","explanation","position","weight","options","answerKey")+if(type=="LISTEN_CHOICE")listOf("speechText") else emptyList()
                fields(q,*required.toTypedArray());allIds+=id(q)
                text(q,"prompt",1000);text(q,"explanation",2000);number(q,"weight",1,10)
                if(mixed || root["contentStatus"].asText()=="DEMO")require(q["weight"].asInt()==1){"New mixed lessons use equal weight one"}
                if(type=="FILL_GAP") {
                    array(q,"options",0,0);fields(q["answerKey"],"acceptedAnswers")
                    val variants=array(q["answerKey"],"acceptedAnswers",1,8).map{require(it.isTextual && it.asText().isNotBlank() && it.asText().length<=500);it.asText()}
                    unique(variants.map{LessonGrader.normalizeFill(it)})
                } else {
                    val options=array(q,"options",4,4)
                    options.forEach{fields(it,"key","label","position");text(it,"key",8);text(it,"label",500);number(it,"position",1,4)}
                    unique(options.map{it["key"].asText()});unique(options.map{it["position"].asInt()})
                    if(type=="LISTEN_CHOICE") {
                        fields(q["answerKey"],"optionKey","fallbackPrompt");text(q,"speechText",1000);text(q["answerKey"],"fallbackPrompt",1000)
                    } else fields(q["answerKey"],"optionKey")
                    require(text(q["answerKey"],"optionKey",8) in options.map{it["key"].asText()})
                }
            }
        }
        unique(allIds)
    }
    private fun fields(node:JsonNode,vararg required:String) {
        require(node.isObject && node.fieldNames().asSequence().toSet()==required.toSet()){"Unexpected or missing seed fields: ${required.joinToString()}"}
    }
    private fun text(node:JsonNode,field:String,max:Int):String {
        val value=node[field];require(value!=null && value.isTextual && value.asText().isNotBlank() && value.asText().length<=max){"Invalid seed $field"};return value.asText()
    }
    private fun id(node:JsonNode,field:String="id")=UUID.fromString(text(node,field,36))
    private fun number(node:JsonNode,field:String,min:Int,max:Int):Int {
        val value=node[field];require(value!=null && value.isIntegralNumber && value.canConvertToInt() && value.asInt() in min..max){"Invalid seed $field"};return value.asInt()
    }
    private fun array(node:JsonNode,field:String,min:Int,max:Int):List<JsonNode> {
        val value=node[field];require(value!=null && value.isArray && value.size() in min..max){"Invalid seed $field"};return value.toList()
    }
    private fun <T> unique(values:List<T>) {require(values.distinct().size==values.size){"Duplicate seed IDs, words, variants or positions"}}
}
