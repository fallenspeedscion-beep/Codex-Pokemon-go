package org.primeval.manager

import java.util.Locale
import kotlin.math.roundToInt

fun ivs(a:Int?,d:Int?,s:Int?):Double? {
    if(a==null || d==null || s==null || a !in 0..15 || d !in 0..15 || s !in 0..15) return null
    return (a+d+s)*100.0/45
}
object Naming {
    const val default="{abbr} {iv}"
    val tokens=listOf("abbr","iv","atk","def","hp","level","shiny","shadow","lucky")
    fun preview(record:Specimen,template:String,level:Double?=null):String {
        val name=record.species
        val fields=mapOf("abbr" to name.substring(0,name.offsetByCodePoints(0,minOf(4,name.codePointCount(0,name.length)))),
            "iv" to record.ivPercent?.roundToInt()?.toString(),"atk" to record.attack?.toString(),"def" to record.defense?.toString(),
            "hp" to record.stamina?.toString(),"level" to level?.toString(),"shiny" to if(record.shiny==true) "✦" else "",
            "shadow" to if(record.shadow==true) "☾" else "", "lucky" to if(record.lucky==true) "★" else "")
        require(template.isNotBlank()){"Enter a naming template"}
        val result=Regex("\\{([^{}]+)}").replace(template){ match ->
            val key=match.groupValues[1]
            require(key in tokens){"Unknown token $key"}
            fields[key] ?: error("Missing $key")
        }.trim()
        require(result.isNotEmpty() && result.length<=12 && result.none{it.isISOControl() || it=='{' || it=='}'}){"Name must contain 1–12 UTF-16 units and valid tokens"}
        return result
    }
    fun protected(record:Specimen)=record.nickname.isBlank() || !record.nickname.equals(record.species,true)
}
