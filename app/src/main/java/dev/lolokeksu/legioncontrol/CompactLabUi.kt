package dev.lolokeksu.legioncontrol

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Shared compact controls; preserve 48dp targets and stack at narrow/large-font layouts. */
internal object CompactLabUi {
    private fun dp(context:Context,value:Int)=(context.resources.displayMetrics.density*value).toInt()
    fun row(context:Context,vararg buttons:Button):LinearLayout {
        val config=context.resources.configuration
        val stacked=config.screenWidthDp<360 || config.fontScale>=1.3f
        return LinearLayout(context).apply {
            orientation=if(stacked) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            buttons.forEachIndexed { i,button->
                LegionUiSupport.styleButtonSurface(button)
                addView(button,LinearLayout.LayoutParams(if(stacked) -1 else 0,-2,if(stacked) 0f else 1f).apply{
                    if(stacked)bottomMargin=dp(context,6)
                    else if(i>0)marginStart=dp(context,6)
                })
            }
            gravity=Gravity.TOP
        }
    }
    fun label(context:Context,value:String)=TextView(context).apply{
        text=value;textSize=12f;setTextColor(LegionUiSupport.MUTED)
        setPadding(0,dp(context,5),0,dp(context,7))
    }
    fun metric(context:Context)=TextView(context).apply {
        textSize=16f;setTextColor(Color.WHITE);setLineSpacing(dp(context,2).toFloat(),1f)
        LegionUiSupport.reportSurface(this)
    }
    fun button(context:Context,title:String,click:()->Unit)=Button(context).apply{
        text=title;isAllCaps=false;setOnClickListener{click()}
        LegionUiSupport.styleButtonSurface(this)
    }
    /** Disclosure owns visibility only; it never runs an action hidden inside it. */
    fun disclosure(context:Context,title:String,content:View,initial:Boolean=false):LinearLayout {
        return LinearLayout(context).apply {
            orientation=LinearLayout.VERTICAL
            var expanded=initial
            val toggle=button(context,title){}
            fun sync(){
                content.visibility=if(expanded)View.VISIBLE else View.GONE
                toggle.text=title+(if(expanded)"  −" else "  +")
                toggle.stateDescription=if(expanded)"Развёрнуто" else "Свёрнуто"
            }
            toggle.setOnClickListener{expanded=!expanded;sync()}
            addView(toggle,LinearLayout.LayoutParams(-1,-2))
            addView(content,LinearLayout.LayoutParams(-1,-2))
            sync()
        }
    }
}
