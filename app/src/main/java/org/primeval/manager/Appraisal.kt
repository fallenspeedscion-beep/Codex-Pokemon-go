package org.primeval.manager

import kotlin.math.*
import java.util.Locale

data class Rail(val left:Int,val y:Int,val width:Int,val ratio:Double,val iv:Int,val confidence:Double)
data class PanelRead(val rails:List<Rail>,val error:String?,val species:String?=null){
    val spread get()=if(error==null && rails.size==3) Spread(rails[0].iv,rails[1].iv,rails[2].iv) else null
    fun summary():String = "panel="+(spread!=null)+" "+listOf("Attack","Defense","HP").mapIndexed{i,label->
        val rail=rails.getOrNull(i)
        if(rail==null)"$label=missing" else String.format(Locale.ROOT,"%s[x=%d,y=%d,w=%d,ratio=%.3f,iv=%d,confidence=%.2f]",label,rail.left,rail.y,rail.width,rail.ratio,rail.iv,rail.confidence)
    }.joinToString(" ")+" "+(error ?: "ok")
    fun agrees(other:PanelRead):Boolean=spread!=null && spread==other.spread && rails.zip(other.rails).all{(a,b)->
        val tolerance=max(2,a.width/70)
        abs(a.left-b.left)<=tolerance && abs(a.y-b.y)<=tolerance && abs(a.width-b.width)<=tolerance && abs(a.ratio-b.ratio)<0.023 && min(a.confidence,b.confidence)>=0.75
    }
}

/** Find the three uniform five-IV segments on the white appraisal card. Measurements
 * use interior scan lines to avoid foreshortened endpoints on the rounded caps. */
object Appraisal {
    private val caught=Regex("""\bThis\s+([\p{L}♀♂.’' -]{1,30}?)\s+was\s+caught\b""",RegexOption.IGNORE_CASE)
    fun species(lines:List<Line>)=caught.find(lines.sortedBy{it.top}.joinToString(" "){it.text})?.groupValues?.get(1)?.trim()
    private fun material(rgb:Int):Int{
        val r=rgb shr 16 and 255; val g=rgb shr 8 and 255; val b=rgb and 255
        return when{
            minOf(r,g,b)>=244 -> 0 // card and separator
            r in 145..240 && maxOf(r,g,b)-minOf(r,g,b)<=16 -> 1 // empty rail
            r>=160 && r-g>=28 && r-b>=40 && g>=55 -> 2 // orange or rose
            else -> -1
        }
    }
    private fun trace(im:Pixels,y:Int):List<Rail>{
        val runs=mutableListOf<IntRange>()
        val limit=minOf(im.width,im.width*7/10)
        val colors=IntArray(limit){material(im.rgb(it,y))}
        var x=0
        while(x<limit){
            if(colors[x]<=0){x++;continue}
            val start=x;while(x<limit && colors[x]>0)x++
            if(x-start>=im.width/30)runs.add(start until x)
        }
        return runs.windowed(3).mapNotNull{segments->
            val first=segments.first().first;val last=segments.last().last
            val width=last-first+1
            if(width !in im.width/6..im.width/2 || first<2 || last+2>=limit)return@mapNotNull null
            if(colors[first-2]!=0 || colors[last+2]!=0)return@mapNotNull null
            val lengths=segments.map{it.last-it.first+1}
            if(lengths.min()*1.16<lengths.max())return@mapNotNull null
            if(segments.zipWithNext().any{(a,b)-> val gap=b.first-a.last-1
                    gap !in 1..max(2,width/28) || (a.last+1 until b.first).none{colors[it]==0}
                })return@mapNotNull null
            var fill=0;var sawEmpty=false;var cells=0
            for(seg in segments){
                for(i in seg){
                    if(colors[i]==2){if(sawEmpty)return@mapNotNull null;fill++}else sawEmpty=true
                }
                for(cell in 0..4){
                    val center=seg.first+((cell+0.5)*(seg.last-seg.first+1)/5).toInt()
                    if(colors[center]==2)cells++
                }
            }
            val ratio=fill.toDouble()/lengths.sum()
            val scaled=15*ratio;val iv=scaled.roundToInt()
            val confidence=1-abs(scaled-iv)
            if(cells!=iv || confidence<0.75)return@mapNotNull null
            Rail(first,y,width,ratio,iv,confidence)
        }
    }
    private val labels=listOf("Attack","Defense","HP")
    fun read(lines:List<Line>,im:Pixels):PanelRead{
        val species=species(lines)
        val anchors=labels.map{label->lines.filter{it.text.trim().equals(label,true) && it.left<im.width/2}}
        val all=anchors.flatten()
        if(anchors.count{it.isNotEmpty()}<2 && species==null)return PanelRead(emptyList(),"appraisal anchors missing")
        val start=if(all.isEmpty())im.height/2 else max(0,all.minOf{it.top}-im.width/6)
        val end=if(all.isEmpty())im.height else min(im.height,all.maxOf{it.bottom}+im.width/4)
        val step=max(1,im.width/500)
        val groups=mutableListOf<MutableList<Rail>>()
        for(y in start until end step step)for(row in trace(im,y)){
            val group=groups.lastOrNull{g->y-g.last().y<=2*step && abs(row.left-g.last().left)<=max(3,row.width/25)}
            if(group==null)groups.add(mutableListOf(row)) else group.add(row)
        }
        val rows=groups.mapNotNull{group->
            if(group.size<2)return@mapNotNull null
            val center=group[group.size/2]
            val middle=group.filter{abs(it.y-center.y)<=max(step,(group.last().y-group.first().y)/4)}
            center.takeIf{middle.all{it.iv==center.iv}}
        }
        val panels=mutableListOf<List<Rail>>()
        for(a in rows)for(b in rows.filter{it.y-a.y in a.width/6..a.width/2})for(c in rows.filter{it.y>b.y}){
            val gap=b.y-a.y
            val ordered=listOf(a,b,c)
            if(abs(c.y-b.y-gap)>max(step*2,gap/9))continue
            if(ordered.any{abs(it.left-a.left)>max(3,a.width/30) || abs(it.width-a.width)>max(4,a.width/24)})continue
            if(anchors.indices.any{i->anchors[i].isNotEmpty() && anchors[i].none{l->
                abs(l.left-ordered[i].left)<a.width/8 && ordered[i].y-l.bottom in -gap/8..gap*2/3
            }})continue
            panels.add(ordered)
        }
        val panel=panels.singleOrNull() ?: return PanelRead(emptyList(),"aligned card count=${panels.size}, candidate rails=${rows.size}; "+
            labels.mapIndexed{i,label->"$label="+rows.any{r->anchors[i].any{l->abs(l.left-r.left)<r.width/8 && r.y-l.bottom in 0..r.width/5}}}.joinToString(" "))
        // A textual rating is used only alongside the card. Badge art alone is ambiguous.
        val stars=lines.filter{it.bottom<=panel[0].y && panel[0].y-it.bottom<panel[0].width && it.left<panel[0].left+panel[0].width}.mapNotNull{
            Regex("""^([0-4])[- ]stars?$""",RegexOption.IGNORE_CASE).matchEntire(it.text.trim())?.groupValues?.get(1)?.toInt()
        }.singleOrNull()
        val total=panel.sumOf{it.iv}
        val category=when(total){in 0..22->0;in 23..29->1;in 30..36->2;in 37..44->3;else->4}
        return PanelRead(panel,if(stars!=null && stars!=category)"displayed rating conflicts with bars" else null,species)
    }
}
