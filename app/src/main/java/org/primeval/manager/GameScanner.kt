package org.primeval.manager

import android.accessibilityservice.AccessibilityService
import android.content.*
import android.app.AlertDialog
import java.lang.ref.WeakReference
import android.graphics.*
import android.os.*
import android.view.*
import android.view.accessibility.*
import android.widget.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

class GameScanner:AccessibilityService() {
    companion object {
        const val game="com.nianticlabs.pokemongo"
        private var reference=WeakReference<GameScanner>(null)
        val active:GameScanner? get()=reference.get()
    }
    private val handler=Handler(Looper.getMainLooper())
    private val io=Executors.newSingleThreadExecutor()
    private val reader by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private var panel:LinearLayout?=null
    private lateinit var title:TextView
    private lateinit var read:Button
    private lateinit var pause:Button
    private var session=Session(false)
    private var profile=0L
    private var mode=""
    private var profileName=""
    private var generation=0
    private var busy=false
    private var scrolls=0
    private var lastScroll=""
    private var proposed=""
    private var approvedSpecies:String?=null
    private var customOverride=false
    private var storedId=0L
    private var review:AlertDialog?=null
    private lateinit var option:Button
    private var moveEvidence=""
    private var nextRequested=false
    private var original:Detail?=null
    private enum class Rename { NONE, VERIFY, EDIT, CHECK }
    private var rename=Rename.NONE
    override fun onServiceConnected(){reference=WeakReference(this)}
    override fun onInterrupt(){suspendScan("Service interrupted")}
    override fun onDestroy(){finishSession();reference.clear();reader.close();io.shutdown();super.onDestroy()}
    override fun onAccessibilityEvent(event:AccessibilityEvent?) {
        if(review==null && panel!=null && event?.eventType==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && rootInActiveWindow?.packageName?.toString()!=game && startedReading) suspendScan("Foreground changed. Recheck the current Pokémon.")
    }
    private var startedReading=false
    private fun suspendScan(why:String){if(panel==null)return;generation++;review?.dismiss();review=null;approvedSpecies=null;busy=false;rename=Rename.NONE;session.pause(why);show(why)}
    fun finishSession(){
        generation++;review?.dismiss();review=null;approvedSpecies=null;busy=false;session.stop();rename=Rename.NONE
        panel?.let{getSystemService(WindowManager::class.java).removeView(it)};panel=null;startedReading=false
    }
    fun start(id:Long,name:String,selectedMode:String,moves:Boolean){
        finishSession();profile=id;profileName=name;mode=selectedMode;session=Session(moves);scrolls=0;lastScroll=""
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(10,4,10,4);setBackgroundColor(0xef163530.toInt())}
        title=TextView(this).apply{setTextColor(-1);textSize=14f}
        box.addView(title);val row=LinearLayout(this);box.addView(row)
        fun button(label:String,action:()->Unit)=Button(this).apply{text=label;setOnClickListener{action()};row.addView(this)}
        read=button("Read"){captureStep()}
        pause=button("Pause"){if(session.phase==Phase.PAUSED){generation++;session.resume();show()}else suspendScan("Paused by user")}
        button("Stop"){finishSession()}
        option=Button(this).apply{isAllCaps=false;box.addView(this);setOnClickListener{
            if(session.phase==Phase.MOVES){session.keepUncertainMoves();show("Uncertain move evidence will be saved")}
            else if(session.phase==Phase.NEXT && rename==Rename.NONE && !nextRequested){requestNext()}
        }}
        val params=WindowManager.LayoutParams((resources.displayMetrics.widthPixels*.94).toInt(),-2,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT)
        params.gravity=Gravity.TOP or Gravity.CENTER_HORIZONTAL
        getSystemService(WindowManager::class.java).addView(box,params);panel=box;show()
    }
    private fun show(message:String=""){
        val prompt=when {
            rename==Rename.VERIFY->"Return to details. Verify identity before rename."
            rename==Rename.EDIT->"Read the labelled name editor."
            rename==Rename.CHECK->"Read details to verify the saved name."
            else->when(session.phase){
                Phase.DETAILS->"Open first details in #Appraise, then Read."
                Phase.APPRAISAL->"Open appraisal for this same Pokémon, then Read."
                Phase.MOVES->"Close appraisal and show regular attacks, then Read."
                Phase.REVIEW->"Return to the same details. Review species, appraisal and moves before saving."
                Phase.NEXT->"Open the next filtered Pokémon and Read, or Stop at the end."
                Phase.PAUSED->"Resume starts a fresh identity check."
                Phase.STOPPED->"Stopped"
            }
        }
        title.text="$profileName · $mode\n$prompt\n$message".trimEnd()
        read.text=if(session.phase==Phase.REVIEW)"Review" else "Read"
        option.visibility=if(!busy && rename==Rename.NONE && (session.phase==Phase.MOVES && session.failures>0 || session.phase==Phase.NEXT && !nextRequested))View.VISIBLE else View.GONE
        option.text=if(session.phase==Phase.NEXT)"Advance using labelled Next control" else "Keep move reading as uncertain"
        read.isEnabled=!busy && session.phase!=Phase.PAUSED
        pause.text=if(session.phase==Phase.PAUSED)"Resume" else "Pause"
    }
    private fun reject(message:String){busy=false;session.fail(message);if(session.phase==Phase.PAUSED)rename=Rename.NONE;show(message)}
    private fun window()=rootInActiveWindow?.takeIf{it.packageName?.toString()==game}
    private fun captureStep(){
        if(busy)return
        if(window()==null){reject("Pokémon GO must be in the foreground");return}
        startedReading=true;busy=true;show("Comparing two observations…")
        val token=++generation
        if(rename==Rename.EDIT){editName();return}
        frame(token){first,_-> handler.postDelayed({if(token==generation)frame(token){second,lines->
            busy=false
            val value=consensus(first,second)
            if(value==null){
                if(session.phase==Phase.MOVES && first==null && second==null)tryScroll(lines)
                else reject("Uncertain or disagreeing observations")
            }else accept(value)
        }},650)}
    }
    private fun frame(token:Int,done:(Any?,List<Line>)->Unit){
        val root=window()
        if(root==null || Build.VERSION.SDK_INT<34){reject("Window capture requires Android 14+ and the game foreground");return}
        takeScreenshotOfWindow(root.windowId,mainExecutor,object:TakeScreenshotCallback{
            override fun onFailure(code:Int){if(token==generation)reject("Capture failed ($code)")}
            override fun onSuccess(result:ScreenshotResult){
                val buffer=result.hardwareBuffer
                val bmp=try{if(token==generation)Bitmap.wrapHardwareBuffer(buffer,result.colorSpace)?.copy(Bitmap.Config.ARGB_8888,false) else null}finally{buffer.close()}
                if(token!=generation)return
                if(bmp==null){reject("Unreadable frame");return}
                reader.process(InputImage.fromBitmap(bmp,0)).addOnCompleteListener{task->
                    try{
                        if(token!=generation)return@addOnCompleteListener
                        if(!task.isSuccessful || window()?.windowId!=root.windowId){reject("Game window or text unavailable");return@addOnCompleteListener}
                        val lines=task.result.textBlocks.flatMap{it.lines}.mapNotNull{line->line.boundingBox?.let{Line(line.text,it.left,it.top,it.right,it.bottom)}}
                        val value=when(session.phase){
                            Phase.APPRAISAL->Recognition.bars(lines,object:Pixels{override val width=bmp.width;override val height=bmp.height;override fun rgb(x:Int,y:Int)=bmp.getPixel(x,y)})
                            Phase.MOVES->Recognition.moves(lines)
                            else->Recognition.detail(lines)
                        }
                        done(value,lines)
                    }finally{bmp.recycle()}
                }
            }
        })
    }
    private fun nodes():List<AccessibilityNodeInfo>{
        val root=window() ?: return emptyList()
        val result=mutableListOf<AccessibilityNodeInfo>();val queue=ArrayDeque<AccessibilityNodeInfo>();queue.add(root)
        while(queue.isNotEmpty() && result.size<400){
            val n=queue.removeFirst();result.add(n)
            for(i in 0 until n.childCount)n.getChild(i)?.let{queue.add(it)}
        }
        return result.filter{it.isVisibleToUser && it.isEnabled && it.packageName?.toString()==game}
    }
    private fun control(label:String)=nodes().singleOrNull{it.isClickable && (it.contentDescription?.toString().equals(label,true) || it.text?.toString().equals(label,true))}
    private fun requestNext(){
        val token=++generation;busy=true;show("Checking before advance")
        frame(token){value,_->if(value!=original || value==null){suspendScan("Current page changed before advance");return@frame}
            nextRequested=true
            val clicked=control("Next Pokémon")?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true
            busy=false;show(if(clicked)"Checking next details…" else "No unique Next Pokémon control. Advance manually or stop.")
            if(clicked)handler.postDelayed({if(generation==token && session.phase==Phase.NEXT)captureStep()},750)
        }
    }
    private fun tryScroll(lines:List<Line>){
        moveEvidence=lines.joinToString(" | "){it.text}.take(750)
        val fingerprint=lines.joinToString("|"){it.text}
        val scroll=nodes().singleOrNull{it.isScrollable}
        if(scrolls>=3 || fingerprint==lastScroll || scroll==null || !scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)){
            reject("Move section uncertain. Scroll manually or pause.");return
        }
        scrolls++;lastScroll=fingerprint;show("Scroll requested. Read again to verify the new view.")
    }
    private fun accept(value:Any){
        if(rename!=Rename.NONE){verifyRename(value as? Detail);return}
        when(session.phase){
            Phase.DETAILS,Phase.NEXT->{session.detail(value as Detail);scrolls=0;lastScroll="";moveEvidence="";approvedSpecies=null;customOverride=false;nextRequested=false}
            Phase.APPRAISAL->session.appraise(value as Spread)
            Phase.MOVES->session.moves(value as Attacks)
            Phase.REVIEW->{if(value!=session.details){suspendScan("Details changed before save");return};if(approvedSpecies==null)review(value as Detail) else save();return}
            else->return
        }
        show(if(session.failures>0)session.reason else value.toString())
    }
    private fun review(detail:Detail){
        val s=session.spread ?: return
        val content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(32,12,32,12)}
        fun caption(value:String){content.addView(TextView(this).apply{text=value})}
        caption("Current name: ${detail.name} · CP ${detail.cp} · HP ${detail.hp}")
        caption("Attack ${s.attack}   Defense ${s.defense}   Stamina ${s.stamina}")
        caption(session.attacks?.let{listOfNotNull(it.fast,it.charged,it.second).joinToString(" / ")} ?: "Moves unconfirmed")
        caption("Confirm species. Enter the actual species for a custom nickname.")
        val species=EditText(this).apply{setSingleLine();setText(detail.visible.species ?: detail.name);content.addView(this)}
        val override=CheckBox(this).apply{text="Allow replacing this Pokémon’s custom nickname";if(mode=="Automatic")content.addView(this)}
        val dialog=AlertDialog.Builder(this,android.R.style.Theme_Material_Light_Dialog_Alert).setTitle("Review scan · $profileName").setView(content)
            .setNegativeButton("Cancel",null).setPositiveButton("Confirm",null).create()
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
        dialog.setOnDismissListener{review=null}
        review=dialog;dialog.show()
        dialog.getButton(-1).setOnClickListener{
            val name=species.text.toString().trim()
            if(name.isBlank()){species.error="Enter species";return@setOnClickListener}
            approvedSpecies=name;customOverride=override.isChecked;dialog.dismiss()
            val token=generation
            handler.postDelayed({if(token==generation && session.phase==Phase.REVIEW)captureStep()},600)
        }
    }
    private fun save(){
        val d=session.details ?: return;val s=session.spread ?: return;val a=session.attacks
        val species=approvedSpecies ?: return
        val record=Specimen(profileId=profile,species=species,nickname=d.name,form=d.visible.form.orEmpty(),shiny=d.visible.shiny,shadow=d.visible.shadow,purified=d.visible.purified,lucky=d.visible.lucky,maxStatus=d.visible.maxStatus,cp=d.cp,hp=d.hp,attack=s.attack,defense=s.defense,stamina=s.stamina,
            fast=a?.fast.orEmpty(),charged=a?.charged.orEmpty(),second=a?.second.orEmpty(),
            confidence="Two matching frames per step; manual species confirmation",verification="Scan reviewed",notes=if(a?.complete==true)"" else "Move slots unconfirmed. $moveEvidence")
        val prefs=getSharedPreferences("settings",0)
        val template=prefs.getString("template."+species.lowercase(),null) ?: prefs.getString("template",Naming.default)!!
        proposed=runCatching{Naming.preview(record,template)}.getOrDefault("")
        val token=generation;busy=true;show("Saving observation")
        io.execute{
            val result=runCatching{Store.open(this).catalog().add(record)}
            handler.post{if(token==generation){
                busy=false
                if(result.isFailure)reject("Local save failed") else {
                    storedId=result.getOrThrow();session.saved();original=d
                    if(mode=="Automatic" && (!Naming.protected(record) || customOverride) && proposed.isNotBlank() && proposed!=d.name)rename=Rename.VERIFY
                    if(mode=="Assisted" && proposed.isNotBlank())getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Nickname",proposed))
                    show("Saved #${result.getOrNull()}"+if(mode!="Scan only")" · $proposed" else "")
                }
            }}
        }
    }
    private fun verifyRename(detail:Detail?){
        val old=original ?: return
        when(rename){
            Rename.VERIFY->{
                if(detail!=old || control("Edit nickname")?.performAction(AccessibilityNodeInfo.ACTION_CLICK)!=true){suspendScan("Identity or unique rename control unavailable");return}
                rename=Rename.EDIT;show()
            }
            Rename.CHECK->{if(detail!=old.copy(name=proposed)){suspendScan("Rename outcome uncertain. Inspect manually.");return}
                rename=Rename.NONE;session.detailAfterRename(detail);original=detail
                val id=storedId;val name=proposed;val token=generation
                busy=true;show("Recording verified name")
                io.execute{val result=runCatching{Store.open(this).catalog().verifiedName(id,name)}
                    handler.post{if(generation==token){busy=false;if(result.getOrNull()==1)show("Verified $name") else suspendScan("Game rename verified. Local record update failed.")}}}}
            else->suspendScan("Unexpected rename state")
        }
    }
    private fun editName(){
        val editor=nodes().singleOrNull{it.isEditable && it.text?.toString()==original?.name}
        if(editor==null || control("OK")==null){suspendScan("Name editor could not be verified");return}
        val args=Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,proposed)}
        if(!editor.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args)){suspendScan("Editor refused text");return}
        if(nodes().singleOrNull{it.isEditable}?.text?.toString()!=proposed || control("OK")?.performAction(AccessibilityNodeInfo.ACTION_CLICK)!=true){suspendScan("Check the open editor manually");return}
        busy=false;rename=Rename.CHECK;show()
    }
}
