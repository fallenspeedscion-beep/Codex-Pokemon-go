package org.primeval.manager

import kotlin.math.*

data class Line(val text:String,val left:Int,val top:Int,val right:Int,val bottom:Int)
interface Pixels { val width:Int; val height:Int; fun rgb(x:Int,y:Int):Int }
data class Visible(val species:String?=null,val form:String?=null,val shiny:Boolean?=null,val shadow:Boolean?=null,
    val purified:Boolean?=null,val lucky:Boolean?=null,val maxStatus:String="")
data class Detail(val name:String,val cp:Int,val hp:Int,val visible:Visible=Visible())
data class Spread(val attack:Int,val defense:Int,val stamina:Int)
data class Attacks(val fast:String,val charged:String,val second:String?,val complete:Boolean)
object Recognition {
    private val cp=Regex("""^CP\s*(\d+)$""",RegexOption.IGNORE_CASE)
    private val hp=Regex("""^(\d+)\s*/\s*(\d+)\s*HP$""",RegexOption.IGNORE_CASE)
    fun detail(lines:List<Line>):Detail? {
        val power=lines.mapNotNull{cp.matchEntire(it.text.trim())?.groupValues?.get(1)?.toIntOrNull()}.singleOrNull() ?: return null
        val health=lines.singleOrNull{hp.matches(it.text.trim())} ?: return null
        val names=lines.filter{it.bottom<=health.top && it.text.matches(Regex("""[\p{L}][\p{L}\p{M}\p{N} .’'♀♂%★✦-]{0,23}""")) && !cp.matches(it.text.trim()) && it.right>health.left && it.left<health.right}
        val name=names.maxByOrNull{it.bottom}?.text ?: return null
        val words=lines.map{it.text.trim()}
        fun after(label:String)=words.singleOrNull{it.startsWith("$label:",true)}?.substringAfter(':')?.trim()?.takeIf{it.isNotBlank()}
        fun flag(label:String)=true.takeIf{words.any{word->word.equals(label,true) || word.equals("$label Pokémon",true)}}
        val visible=Visible(after("Species"),after("Form"),flag("Shiny"),flag("Shadow"),flag("Purified"),flag("Lucky"),
            listOf("Dynamax","Gigantamax").singleOrNull{flag(it)==true}.orEmpty())
        return Detail(name,power,hp.matchEntire(health.text.trim())!!.groupValues[2].toInt(),visible)
    }
    fun bars(lines:List<Line>,image:Pixels):Spread? {
        val labels=listOf("Attack","Defense","HP").map { label->lines.singleOrNull{it.text.trim().equals(label,true)} ?: return null }
        val gap=labels[1].top-labels[0].top
        if(gap<=0 || abs(labels[2].top-labels[1].top-gap)>gap/4) return null
        val values=labels.map{l ->
            val samples=(l.bottom until minOf(l.bottom+gap/2,image.height)).mapNotNull{y->bar(image,l.left,y)}
            val value=samples.groupBy{it}.maxByOrNull{it.value.size} ?: return null
            if(value.value.size<2 || samples.any{it!=value.key}) return null
            value.key
        }
        return Spread(values[0],values[1],values[2])
    }
    private fun bar(im:Pixels,left:Int,y:Int):Int? {
        fun color(x:Int):Int {
            val p=im.rgb(x,y); val r=p shr 16 and 255;val g=p shr 8 and 255;val b=p and 255
            return when {r>185 && r-g>24 && r-b>65 -> 2
                r in 165..239 && maxOf(r,g,b)-minOf(r,g,b)<14 -> 1
                else -> 0}
        }
        val start=(maxOf(0,left-im.width/80)..minOf(im.width-1,left+im.width/60)).firstOrNull{color(it)>0} ?: return null
        var last=start;var fill=0;var empty=false
        for(x in start until im.width){
            when(color(x)){
                0->if(x-last>maxOf(2,im.width/200)) break
                1->{empty=true;last=x}
                2->{if(empty)return null;fill=x-start+1;last=x}
            }
        }
        val width=last-start+1
        if(width !in im.width/7..im.width/2)return null
        val scaled=15.0*fill/width
        return scaled.roundToInt().takeIf{abs(it-scaled)<0.22}
    }
    // Only accept labelled slots. Layouts lacking labels stay pending for human review.
    fun moves(lines:List<Line>):Attacks? {
        val regular=lines.sortedBy{it.top}.takeWhile{!it.text.contains("Max Move",true)}
        fun slot(label:String):String? {
            val index=regular.indexOfFirst{it.text.trim().equals(label,true)}
            return regular.getOrNull(index+1)?.text?.takeIf{index>=0 && it.matches(Regex("""[A-Za-z][A-Za-z '’-]{1,32}""")) && !it.contains("Attack",true)}
        }
        val fast=slot("Fast Attack") ?: return moveTable(regular)
        val charged=slot("Charged Attack") ?: slot("Charged Attack 1") ?: return null
        val second=slot("Charged Attack 2")
        return Attacks(fast,charged,second,second!=null || regular.any{it.text.equals("New Attack",true)})
    }
    // Attack names sit to the left of damage in the battle tab. Keep uncertain
    // slot counts open until the lower boundary of the regular table is visible.
    private fun moveTable(lines:List<Line>):Attacks?{
        val tab=lines.indexOfLast{it.text.equals("Trainer Battles",true) || it.text.equals("Gyms & Raids",true)}
        if(tab<0)return null
        val body=lines.drop(tab+1).takeWhile{!it.text.equals("New Attack",true)}
        val namePattern=Regex("""[A-Za-z][A-Za-z '’-]{1,32}""")
        val joined=Regex("""^([A-Za-z][A-Za-z '’-]{1,32}?)\s+(\d{1,3})$""")
        val names=body.mapNotNull{line->
            joined.matchEntire(line.text)?.groupValues?.get(1) ?: line.text.takeIf{
                namePattern.matches(it) && body.count{n->n.left>line.right && n.text.matches(Regex("""\d{1,3}""")) &&
                    kotlin.math.abs((n.top+n.bottom)-(line.top+line.bottom))<line.bottom-line.top}==1
            }
        }
        if(names.size !in 2..3 || names.distinct().size!=names.size)return null
        return Attacks(names[0],names[1],names.getOrNull(2),names.size==3 || lines.any{it.text.equals("New Attack",true)})
    }

}
enum class Phase { DETAILS, APPRAISAL, MOVES, REVIEW, NEXT, PAUSED, STOPPED }
class Session(val scanMoves:Boolean) {
    var phase=Phase.DETAILS;private set
    var failures=0;private set
    var details:Detail?=null;private set
    var spread:Spread?=null;private set
    var attacks:Attacks?=null;private set
    private val visited=mutableSetOf<Triple<String,Int,Int>>()
    private fun key(detail:Detail)=Triple(detail.name,detail.cp,detail.hp)
    var reason="";private set
    fun fail(message:String){reason=message;if(++failures>=3)pause(message)}
    fun pause(message:String){reason=message;phase=Phase.PAUSED}
    fun stop(){phase=Phase.STOPPED}
    fun resume(){if(phase==Phase.PAUSED){details=null;spread=null;attacks=null;phase=Phase.DETAILS;failures=0}}
    fun detail(value:Detail){if(phase !in listOf(Phase.DETAILS,Phase.NEXT))return
        if(key(value) in visited){fail("Same or indistinguishable Pokémon. End the session or check the next page.");return}
        details=value;spread=null;attacks=null;phase=Phase.APPRAISAL;failures=0}
    fun appraise(value:Spread){if(phase!=Phase.APPRAISAL)return;spread=value;phase=if(scanMoves)Phase.MOVES else Phase.REVIEW;failures=0}
    fun moves(value:Attacks){if(phase==Phase.MOVES){attacks=value;phase=Phase.REVIEW;failures=0}}
    fun keepUncertainBars(){if(phase==Phase.APPRAISAL){spread=null;phase=if(scanMoves)Phase.MOVES else Phase.REVIEW;failures=0}}
    fun keepUncertainMoves(){if(phase==Phase.MOVES){phase=Phase.REVIEW;failures=0}}
    fun detailAfterRename(value:Detail){if(phase==Phase.NEXT)visited.add(key(value))}
    fun saved(){if(phase==Phase.REVIEW){details?.let{visited.add(key(it))};phase=Phase.NEXT;failures=0}}
}
fun <T> consensus(first:T?,second:T?):T?=first?.takeIf{it==second}
