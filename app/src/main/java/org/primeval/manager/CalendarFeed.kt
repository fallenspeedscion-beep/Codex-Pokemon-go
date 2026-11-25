package org.primeval.manager

import java.io.InputStream
import java.net.URL
import java.time.*
import java.time.format.DateTimeFormatter
import javax.net.ssl.HttpsURLConnection

object CalendarFeed {
    const val limit=4*1024*1024
    fun read(input:InputStream):String {
        val output=java.io.ByteArrayOutputStream()
        val block=ByteArray(8192)
        while(true){val n=input.read(block);if(n<0)break;require(output.size()+n<=limit){"Calendar exceeds 4 MiB"};output.write(block,0,n)}
        return output.toString("UTF-8")
    }
    fun fetch(source:String):String {
        var url=URL(source)
        repeat(5){
            require(url.protocol=="https" && url.userInfo==null){"Enter an HTTPS calendar URL"}
            val connection=url.openConnection() as HttpsURLConnection
            try{
                connection.connectTimeout=12000;connection.readTimeout=20000;connection.instanceFollowRedirects=false
                when(connection.responseCode){
                    in 200..299->return connection.inputStream.use(::read)
                    in listOf(301,302,303,307,308)->url=URL(url,connection.getHeaderField("Location") ?: error("Missing redirect"))
                    else->error("Calendar returned HTTP ${connection.responseCode}")
                }
            }finally{connection.disconnect()}
        }
        error("Too many redirects")
    }
    private data class Field(val params:String,val value:String)
    private val clock=DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss").withResolverStyle(java.time.format.ResolverStyle.STRICT)
    fun parse(text:String,source:String,zone:ZoneId=ZoneId.systemDefault()):List<CalendarItem>{
        val lines=text.replace("\r\n","\n").replace(Regex("\n[ \t]"),"").trim().lines()
        require(lines.firstOrNull()=="BEGIN:VCALENDAR" && lines.lastOrNull()=="END:VCALENDAR"){"Incomplete iCalendar file"}
        var fields:MutableMap<String,Field>?=null;var depth=0
        val entries=mutableMapOf<String,CalendarItem>()
        fun decode(value:String)=Regex("""\\([nN,;\\])""").replace(value){if(it.groupValues[1].equals("n",true))"\n" else it.groupValues[1]}
        fun date(f:Field):ZonedDateTime{
            val tz=Regex("""(?:^|;)TZID=([^;]+)""").find(f.params)?.groupValues?.get(1)?.trim('"')?.let(ZoneId::of) ?: zone
            if(f.value.length==8)return LocalDate.parse(f.value,DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay(tz)
            return LocalDateTime.parse(f.value.removeSuffix("Z"),clock).atZone(if(f.value.endsWith("Z"))ZoneOffset.UTC else tz)
        }
        val cancelFeed="METHOD:CANCEL" in lines
        for(line in lines){
            when{
                line=="BEGIN:VEVENT"->{require(fields==null);fields=mutableMapOf();depth=0}
                line=="END:VEVENT"->{
                    val f=fields ?: error("Unexpected event end")
                    require(depth==0 && listOf("RRULE","RDATE","RECURRENCE-ID","DURATION").none{it in f}){"Unsupported recurring event or duration"}
                    val uid=f["UID"]?.value?.takeIf{it.isNotBlank()} ?: error("Missing event UID")
                    val cancelled=f["STATUS"]?.value=="CANCELLED" || cancelFeed
                    val start=f["DTSTART"]?.let(::date)
                    require(start!=null || cancelled){"Missing event start"}
                    val day=f["DTSTART"]?.value?.length==8
                    val end=f["DTEND"]?.let(::date) ?: (if(day)start?.plusDays(1) else start)
                    require(start==null || end==null || !end.isBefore(start)){"Event ends before it starts"}
                    val item=CalendarItem(source,uid,decode(f["SUMMARY"]?.value ?: "Untitled event"),start?.toInstant()?.toEpochMilli() ?: 0,end?.toInstant()?.toEpochMilli() ?: 0,decode(f["DESCRIPTION"]?.value.orEmpty()),f["SEQUENCE"]?.value?.toInt() ?: 0,cancelled,day)
                    if((entries[uid]?.sequence ?: -1)<=item.sequence)entries[uid]=item
                    fields=null
                }
                fields!=null && line.startsWith("BEGIN:")->depth++
                fields!=null && line.startsWith("END:")->depth--
                fields!=null && depth==0->{val colon=line.indexOf(':');require(colon>0);val key=line.substring(0,colon)
                    fields[key.substringBefore(';').uppercase()]=Field(key.substringAfter(';',""),line.substring(colon+1))}
            }
        }
        require(fields==null){"Unclosed event"}
        return entries.values.toList()
    }
}
