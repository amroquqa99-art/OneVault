package org.onevault.app.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import org.onevault.app.data.Account

object Ui {
    private const val INK=0xFF102A36.toInt()
    private const val MUTE=0xFF667986.toInt()
    private const val ACCENT=0xFF0A7A68.toInt()
    fun dp(a:Activity,x:Int)=(x*a.resources.displayMetrics.density).toInt()
    fun rounded(color:Int,r:Int=15,stroke:Int?=null):GradientDrawable=GradientDrawable().apply {
        setColor(color);cornerRadius=r.toFloat();if(stroke!=null)setStroke(1,stroke)
    }
    fun column(a:Activity)=LinearLayout(a).apply { orientation=LinearLayout.VERTICAL }
    fun label(a:Activity,s:String,size:Float=15f,bold:Boolean=false,color:Int=INK)=TextView(a).apply {
        text=s;textSize=size;setTextColor(color);setPadding(0,dp(a,3),0,dp(a,3));
        if(bold)setTypeface(null,Typeface.BOLD)
    }
    fun card(a:Activity,parent:LinearLayout,title:String?,body:(LinearLayout)->Unit) {
        val frame=column(a).apply {
            background=rounded(Color.WHITE,dp(a,17),0xFFE4E9EA.toInt())
            setPadding(dp(a,15),dp(a,13),dp(a,15),dp(a,13))
            elevation=dp(a,1).toFloat()
        }
        if(title!=null) frame.addView(label(a,title,17f,true))
        body(frame)
        parent.addView(frame,LinearLayout.LayoutParams(-1,-2).apply { setMargins(0,0,0,dp(a,12)) })
    }
    fun field(a:Activity,hint:String,prefill:String="",password:Boolean=false)=EditText(a).apply {
        setSingleLine(true);setText(prefill);setSelectAllOnFocus(true)
        this.hint=hint;textSize=15f
        inputType=if(password) (android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD)
             else android.text.InputType.TYPE_CLASS_TEXT
        background=rounded(0xFFF2F6F6.toInt(),dp(a,9),0xFFCFD7D9.toInt())
        setPadding(dp(a,12),dp(a,11),dp(a,12),dp(a,11))
    }
    fun button(a:Activity,text:String,action:()->Unit):Button=Button(a).apply {
        this.text=text;isAllCaps=false;textSize=13f
        setTextColor(Color.WHITE)
        background=rounded(ACCENT,dp(a,12))
        setOnClickListener {action()}
    }
    fun secondaryButton(a:Activity,text:String,action:()->Unit):Button=Button(a).apply {
        this.text=text;isAllCaps=false;textSize=13f
        setTextColor(INK)
        background=rounded(0xFFE7EEEE.toInt(),dp(a,12))
        setOnClickListener {action()}
    }
    fun space(a:Activity,px:Int)=View(a).apply { layoutParams=LinearLayout.LayoutParams(1,dp(a,px)) }
    fun subtle(a:Activity,s:String)=label(a,s,13f,false,MUTE)
    fun bytes(b:Long):String {
        val unit=1024.0
        return if(b<1024)"$b B" else if(b<1024*1024) "%.1f KiB".format(b/unit)
           else if(b<1024L*1024*1024) "%.1f MiB".format(b/unit/unit)
           else "%.2f GiB".format(b/unit/unit/unit)
    }
}
