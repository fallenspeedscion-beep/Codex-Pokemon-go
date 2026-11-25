package org.primeval.manager

import android.app.*
import android.content.*
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.Settings
import android.text.InputType
import android.view.*
import android.widget.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

class MainActivity:Activity(){
    private val prefs by lazy{getSharedPreferences("settings",0)}
    private val store by lazy{Store.open(this)}
    private val io=Executors.newSingleThreadExecutor()
    private var profiles=listOf<Profile>()
    private var records=listOf<Specimen>()
    private var events=listOf<CalendarItem>()
    private var profile=0L
    private var page="Dashboard"
    private var query=""
    private var filter="All"
    private var offset=0
    private val selected=mutableSetOf<Long>()
    private var exportIds=longArrayOf()
    private var exportJson=false
    private lateinit var body:LinearLayout
    private lateinit var root:LinearLayout
    private val dark get()=resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK==Configuration.UI_MODE_NIGHT_YES
    private val accent get()=Color.parseColor(if(dark)"#83DDC2" else "#006B54")
    private val ink get()=Color.parseColor(if(dark)"#EBF6F1" else "#152E28")
    private val paper get()=Color.parseColor(if(dark)"#14241F" else "#F3F8F4")
    private val categories=listOf("Dashboard","Scanner","Collection","Event Advisor","Profiles","Settings")
    override fun onCreate(state:Bundle?){
        if(dark)setTheme(android.R.style.Theme_Material_NoActionBar)
        super.onCreate(state)
        profile=prefs.getLong("profile",0);page=state?.getString("page") ?: page
        exportIds=state?.getLongArray("ids") ?: exportIds;exportJson=state?.getBoolean("json") ?: false
        root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(paper)
            setOnApplyWindowInsetsListener{v,i->val bars=i.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);i}}
        setContentView(root);reload()
    }
    override fun onSaveInstanceState(out:Bundle){super.onSaveInstanceState(out);out.putString("page",page);out.putLongArray("ids",exportIds);out.putBoolean("json",exportJson)}
    override fun onResume(){super.onResume();if(profiles.isNotEmpty())reload()}
    override fun onDestroy(){io.shutdown();super.onDestroy()}
    private fun toast(msg:String)=Toast.makeText(this,msg,Toast.LENGTH_LONG).show()
    private fun <T> work(job:()->T,done:(T)->Unit){
        io.execute{val res=runCatching(job);runOnUiThread{if(!isDestroyed && !isFinishing)res.fold(done){toast(it.localizedMessage ?: "Operation failed")}}}
    }
    private fun reload(){
        work({val dao=store.catalog();store.runInTransaction{if(dao.profiles().isEmpty())dao.add(Profile(name="My collection"))}
            Triple(dao.profiles(),dao.all(),dao.events())}){(p,r,e)->profiles=p;records=r;events=e
            if(p.none{it.id==profile})profile=p.first().id
            render()}
    }
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    private fun column()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
    private fun label(parent:LinearLayout,str:String,size:Int=16,bold:Boolean=false):TextView=TextView(this).apply{
        text=str;textSize=size.toFloat();setTextColor(if(bold)accent else ink);if(bold)setTypeface(null,Typeface.BOLD)
        setPadding(0,dp(6),0,dp(5));parent.addView(this)}
    private fun btn(parent:LinearLayout,str:String,action:()->Unit)=Button(this).apply{text=str;isAllCaps=false;setOnClickListener{action()};parent.addView(this)}
    private fun field(parent:LinearLayout,hintText:String,value:String="")=EditText(this).apply{hint=hintText;textSize=16f;setSingleLine();setText(value);contentDescription=hintText;parent.addView(this)}
    private fun choice(parent:LinearLayout,items:List<String>,index:Int=0)=Spinner(this).apply{
        adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,items);setSelection(index.coerceIn(items.indices));parent.addView(this)}
    private fun navigate(dest:String){page=dest;render()}
    private fun render(){
        root.removeAllViews()
        val header=LinearLayout(this).apply{setPadding(dp(16),dp(8),dp(12),dp(8));gravity=Gravity.CENTER_VERTICAL;setBackgroundColor(Color.rgb(18,65,52))}
        root.addView(header)
        val brand=TextView(this).apply{text="Primeval";setTextColor(Color.WHITE);textSize=24f;setTypeface(Typeface.SERIF,Typeface.BOLD)}
        header.addView(brand,LinearLayout.LayoutParams(0,-2,1f))
        btn(header,"Sections ▾"){vMenu(header)}
        val scroll=ScrollView(this);root.addView(scroll)
        body=column().apply{setPadding(dp(20),dp(12),dp(20),dp(32))};scroll.addView(body)
        label(body,profiles.first{it.id==profile}.name,14,true)
        label(body,page,32,true)
        when(page){
            "Dashboard"->dashboard()
            "Profiles"->profilePage()
            "Collection"->collection()
            "Scanner"->scanner()
            "Settings"->settings()
            else->advisor()
        }
    }
    private fun vMenu(anchor:View){PopupMenu(this,anchor,Gravity.END).apply{categories.forEach{menu.add(it)};setOnMenuItemClickListener{navigate(it.title.toString());true};show()}}
    private fun current()=records.filter{it.profileId==profile}
    private fun dashboard(){
        val rows=current()
        label(body,"${rows.size} Pokémon   ·   ${rows.map{it.species.lowercase()}.distinct().size} species",22)
        label(body,"${rows.count{it.ivPercent==100.0}} perfect IV   ·   ${rows.count{it.shadow==true}} Shadow")
        btn(body,"Open scanner"){navigate("Scanner")};btn(body,"Browse collection"){navigate("Collection")}
        label(body,"Recent records",20,true)
        if(rows.isEmpty())label(body,"Your first scan or manual entry will appear here.")
        rows.take(4).forEach{r->btn(body,summary(r)){edit(r)}}
        btn(body,"Add a Pokémon"){edit(null)}
    }
    private fun profilePage(){
        label(body,"Choose the collection that matches the account you opened in Pokémon GO.")
        profiles.forEach{p->btn(body,p.name+"  ·  "+records.count{it.profileId==p.id}+if(p.id==profile)"  ✓" else ""){
            GameScanner.active?.finishSession();profile=p.id;prefs.edit().putLong("profile",profile).apply();selected.clear();query="";filter="All";offset=0;render()}}
        val input=field(body,"New profile name")
        btn(body,"Create profile"){val name=input.text.toString().trim();if(name.isEmpty())toast("Enter a name") else work({store.catalog().add(Profile(name=name))}){GameScanner.active?.finishSession();profile=it;selected.clear();prefs.edit().putLong("profile",it).apply();reload()}}
    }
    private fun filtered()=current().filter{r->listOf(r.species,r.nickname,r.form,r.fast,r.charged,r.second).any{it.contains(query,true)} && when(filter){"Shadow"->r.shadow==true;"Perfect IV"->r.ivPercent==100.0;"Missing IV"->r.ivPercent==null;else->true}}
    private fun summary(r:Specimen)=listOf(r.nickname.ifBlank{r.species},"CP ${r.cp ?: "?"}",r.ivPercent?.let{"%.1f%%".format(it)} ?: "IV ?").joinToString("   ·   ")
    private fun collection(){
        val search=field(body,"Species, nickname or move",query);val flags=listOf("All","Shadow","Perfect IV","Missing IV");val scope=choice(body,flags,flags.indexOf(filter))
        btn(body,"Apply search and filter"){query=search.text.toString().trim();filter=scope.selectedItem.toString();offset=0;render()}
        val rows=filtered();label(body,"${rows.size} results · ${selected.size} selected")
        btn(body,"Add a Pokémon"){edit(null)}
        for(r in rows.drop(offset).take(40)){
            val row=LinearLayout(this);body.addView(row);row.addView(CheckBox(this).apply{contentDescription="Select ${r.species} #${r.id}";isChecked=r.id in selected;setOnCheckedChangeListener{_,on->if(on)selected.add(r.id) else selected.remove(r.id);render()}})
            btn(row,summary(r)){edit(r)}
        }
        if(offset>0)btn(body,"Previous 40"){offset=(offset-40).coerceAtLeast(0);render()}
        if(offset+40<rows.size)btn(body,"Next 40"){offset+=40;render()}
        btn(body,"Export records"){navigate("Settings")}
    }
    private fun edit(existing:Specimen?){
        val r=existing ?: Specimen(profileId=profile,species="")
        val panel=column().apply{setPadding(dp(20),0,dp(20),0)}
        fun input(label:String,value:Any?):EditText{label(panel,label,14,true);return field(panel,label,value?.toString().orEmpty())}
        val species=input("Species",r.species);val form=input("Form",r.form);val nick=input("Current nickname",r.nickname)
        val nums=listOf("CP","Maximum HP","Attack IV","Defense IV","Stamina IV").zip(listOf(r.cp,r.hp,r.attack,r.defense,r.stamina)).map{(key,value)->input(key,value).apply{inputType=InputType.TYPE_CLASS_NUMBER}}
        val moves=listOf("Fast Attack","Charged Attack 1","Charged Attack 2").zip(listOf(r.fast,r.charged,r.second)).map{input(it.first,it.second)}
        val status=listOf("Shiny","Shadow","Purified","Lucky").zip(listOf(r.shiny,r.shadow,r.purified,r.lucky)).map{(key,v)->label(panel,key);choice(panel,listOf("Unknown","Yes","No"),when(v){true->1;false->2;else->0})}
        val max=input("Max status",r.maxStatus);val notes=input("Review notes",r.notes)
        fun record():Specimen{
            val name=species.text.toString().trim();require(name.isNotBlank()){"Enter a species"}
            val n=nums.mapIndexed{i,f->f.text.toString().takeIf{it.isNotBlank()}?.let{it.toIntOrNull()?.takeIf{v->if(i>=2)v in 0..15 else v>0} ?: error("Invalid ${f.hint}")}}
            val s=status.map{listOf(null,true,false)[it.selectedItemPosition]}
            return r.copy(species=name,form=form.text.toString(),nickname=nick.text.toString(),cp=n[0],hp=n[1],attack=n[2],defense=n[3],stamina=n[4],
                fast=moves[0].text.toString(),charged=moves[1].text.toString(),second=moves[2].text.toString(),shiny=s[0],shadow=s[1],purified=s[2],lucky=s[3],maxStatus=max.text.toString(),notes=notes.text.toString(),verification="Manually reviewed")
        }
        val preview=label(panel,"");btn(panel,"Preview nickname"){runCatching{val p=record();Naming.preview(p,template(p.species))}.onSuccess{preview.text=it}.onFailure{preview.text=it.message}}
        btn(panel,"Copy preview"){if(preview.text.isNotBlank())runCatching{val p=record();Naming.preview(p,template(p.species))}.onSuccess{getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Nickname",it));toast("Copied $it")}}
        val refs=runCatching{BattleReference.parse(org.json.JSONObject(prefs.getString("rules","{}")!!))}.getOrDefault(emptyList())
        label(panel,BattleReference.describe(r,refs))
        val candidates=current().count{it.id!=r.id && it.species.equals(r.species,true) && it.cp==r.cp && it.hp==r.hp}
        if(candidates>0)label(panel,"$candidates other records share species, CP and HP. Each has a separate local ID.")
        label(panel,"${r.confidence} · ${r.verification}")
        val dialog=AlertDialog.Builder(this).setTitle(if(existing==null)"Add Pokémon" else "Record #${r.id}").setView(ScrollView(this).apply{addView(panel)}).setPositiveButton("Save",null).setNegativeButton("Close",null).create()
        dialog.show();dialog.getButton(-1).setOnClickListener{
            runCatching{record()}.onSuccess{p->dialog.getButton(-1).isEnabled=false;work({if(existing==null)store.catalog().add(p) else store.catalog().edit(p)}){dialog.dismiss();reload()}}.onFailure{toast(it.message ?: "Invalid record")}
        }
    }
    private fun template(species:String)=prefs.getString("template."+species.lowercase(),null) ?: prefs.getString("template",Naming.default)!!
    private fun scanner(){
        label(body,"Tag your Pokémon Appraise, search #Appraise, then open the first details page. Return to this app to start the panel.")
        val mode=choice(body,listOf("Scan only","Assisted","Automatic"))
        val moves=CheckBox(this).apply{text="Read regular attacks";isChecked=true};body.addView(moves)
        val follow=CheckBox(this).apply{text="Follow labelled appraisal and Next controls"};body.addView(follow)
        label(body,"The optional accessibility service reads the game window on demand. Automatic mode also operates uniquely labelled rename controls. You can pause or stop from its panel.")
        btn(body,"Accessibility settings"){startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}
        btn(body,"Start with ${profiles.first{it.id==profile}.name}"){
            val service=GameScanner.active
            if(service==null){toast("Enable Primeval in Accessibility settings first");return@btn}
            if(android.os.Build.VERSION.SDK_INT<34){toast("Live window capture requires Android 14 or newer");return@btn}
            val p=profiles.first{it.id==profile};val m=mode.selectedItem.toString()
            fun begin(){service.start(p.id,p.name,m,moves.isChecked,follow.isChecked);moveTaskToBack(true)}
            if(m=="Automatic")AlertDialog.Builder(this).setTitle("Enable automatic rename for this session?").setMessage("Read the suggested name before confirming. Only confirm default species names. Stop if the game layout or Pokémon changes.").setPositiveButton("Enable"){_,_->begin()}.setNegativeButton("Cancel",null).show() else begin()
        }
        btn(body,"Stop scanner"){GameScanner.active?.finishSession();toast("Stopped");reload()}
        val history=column();body.addView(history)
        work({store.catalog().history(profile)}){items->if(items.isNotEmpty()){label(history,"Recent scan history",20,true);items.forEach{label(history,"${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it.time))} · ${it.message}",14)}}}
    }
    private fun settings(){
        label(body,"Nickname templates",20,true);label(body,Naming.tokens.joinToString("  "){"{$it}"})
        val species=field(body,"Species override (blank for universal)")
        val format=field(body,"Template",prefs.getString("template",Naming.default)!!)
        btn(body,"Load template"){format.setText(template(species.text.toString().trim()))}
        btn(body,"Save template"){
            val key=species.text.toString().trim().lowercase();val value=format.text.toString()
            val sample=Specimen(profileId=profile,species=key.ifBlank{"Pikachu"},attack=15,defense=15,stamina=15,shiny=true,shadow=true,lucky=true)
            runCatching{Naming.preview(sample,value,40.0)}.onSuccess{prefs.edit().putString(if(key.isBlank())"template" else "template.$key",value).apply();toast("Saved · sample $it")}.onFailure{toast(it.message ?: "Invalid template")}
        }
        val library=field(body,"Reusable template name")
        btn(body,"Save to template library"){
            val name=library.text.toString().trim();if(name.isBlank()){toast("Enter a template name");return@btn}
            val value=format.text.toString()
            runCatching{Naming.preview(Specimen(profileId=profile,species="Eevee",attack=1,defense=1,stamina=1),value,20.0)}.onSuccess{
                prefs.edit().putString("library.$name",value).apply();render()
            }.onFailure{toast(it.message ?: "Invalid template")}
        }
        prefs.all.keys.filter{it.startsWith("library.")}.sorted().forEach{key->btn(body,"Load ${key.removePrefix("library.")}"){format.setText(prefs.getString(key,""));library.setText(key.removePrefix("library."))}}
        label(body,"Export collection",20,true)
        val scope=choice(body,listOf("Active profile","Entire collection","Selected records","Current search results"))
        fun export(json:Boolean){
            val rows=when(scope.selectedItemPosition){1->records;2->current().filter{it.id in selected};3->filtered();else->current()}
            exportIds=rows.map{it.id}.toLongArray();exportJson=json
            val ext=if(json)"json" else "csv"
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(if(json)"application/json" else "text/csv").putExtra(Intent.EXTRA_TITLE,"primeval.$ext"),10)
        }
        btn(body,"Save CSV"){export(false)};btn(body,"Save JSON"){export(true)}
        label(body,"Event sources",20,true)
        val url=field(body,"HTTPS calendar URL")
        btn(body,"Subscribe and refresh"){refresh(url.text.toString().trim())}
        for(source in sources().sorted()){
            label(body,source);if(source.startsWith("https://"))btn(body,"Refresh feed"){refresh(source)}
            btn(body,"Remove source"){work({store.catalog().clearSource(source)}){setSources(sources()-source);reload()}}
        }
        btn(body,"Import ICS file"){open(11)}
        btn(body,"Import event rules (JSON)"){open(12)}
    }
    private fun sources()=prefs.getStringSet("sources",emptySet())!!.toSet()
    private fun setSources(s:Set<String>){prefs.edit().putStringSet("sources",s).apply()}
    private fun refresh(source:String){work({store.cache(source,CalendarFeed.fetch(source))}){setSources(sources()+source);toast("Calendar refreshed");reload()}}
    private fun open(code:Int){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),code)}
    @Deprecated("Platform activity result")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
        super.onActivityResult(requestCode,resultCode,data);val uri=data?.data ?: return;if(resultCode!=RESULT_OK)return
        when(requestCode){
            10->{val ids=exportIds.toSet();val json=exportJson
                work({val dao=store.catalog();contentResolver.openOutputStream(uri,"wt")?.bufferedWriter(Charsets.UTF_8)?.use{Export.write(it,dao.all().filter{r->r.id in ids},dao.profiles().associate{p->p.id to p.name},json)} ?: error("Cannot open destination")}){toast("Export saved")}}
            11,12->work({val text=contentResolver.openInputStream(uri)?.use(CalendarFeed::read) ?: error("Cannot read file")
                if(requestCode==11)store.cache(uri.toString(),text) else{Advisor.parse(text);prefs.edit().putString("rules",text).apply()}}){
                if(requestCode==11)setSources(sources()+uri.toString());toast("Import complete");reload()}
        }
    }
    private fun advisor(){
        val rules=runCatching{Advisor.parse(prefs.getString("rules","")!!)}.getOrDefault(emptyList())
        val now=System.currentTimeMillis();val future=events.filter{!it.cancelled && it.end>=now}
        for(c in listOf("Frustration","Community Day")){
            label(body,c,20,true)
            if(current().isEmpty())label(body,"Add a collection record to evaluate.")
            current().forEach{r->val advice=Advisor.assess(r,c,rules,now)
                label(body,"${r.species} · ${advice.priority.name.replace('_',' ')}",16,true);label(body,advice.reason,14)}
        }
        rules.filter{it.start>now}.map{it.start to it.category}.distinct().sortedBy{it.first}.forEach{(whenAt,category)->
            val upcoming=current().map{it to Advisor.assess(it,category,rules,whenAt)}.filter{it.second.priority in listOf(Priority.MUST_DO,Priority.RECOMMENDED,Priority.OPTIONAL)}
            if(upcoming.isNotEmpty()){
                label(body,"$category · ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(whenAt))}",18,true)
                upcoming.forEach{(r,a)->label(body,"${r.species} · ${a.priority.name.replace('_',' ')}\n${a.reason}".replace("\n"," "))}
            }
        }
        label(body,"Calendar",20,true)
        if(future.isEmpty())label(body,"Import a file or subscribe to a feed in Settings.")
        for(e in future){
            label(body,e.title,17,true)
            fun time(ms:Long)=DateTimeFormatter.ofPattern(if(e.allDay)"MMM d, uuuu" else "MMM d, uuuu HH:mm z").format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
            label(body,"${time(e.start)} – ${time(e.end)}");if(e.description.isNotBlank())label(body,e.description,14)
            btn(body,"Add to device calendar"){runCatching{startActivity(Intent(Intent.ACTION_INSERT,CalendarContract.Events.CONTENT_URI).putExtra(CalendarContract.Events.TITLE,e.title).putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,e.start).putExtra(CalendarContract.EXTRA_EVENT_END_TIME,e.end).putExtra(CalendarContract.Events.DESCRIPTION,e.description).putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY,e.allDay))}.onFailure{toast("No calendar app available")}}
        }
    }
}
