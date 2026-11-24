package dev.lolokeksu.legioncontrol

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.LinearLayout

/** Two equal-width controls on normal phones; stack on narrow widths or large text.
 * Keep native buttons, click handlers, 48dp touch targets and natural wrapping. */
internal class CompactActionRow(context:Context,vararg buttons:Button):LinearLayout(context) {
    private fun dp(value:Int)=(value*resources.displayMetrics.density+0.5f).toInt()
    private var lastWide:Boolean?=null
    private var lastVisible:List<Int> = emptyList()
    init {
        orientation=VERTICAL
        buttons.forEach { button ->
            addView(button,LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT))
            LegionUiSupport.styleButtonSurface(button)
        }
    }
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        val visible=(0 until childCount).filter{getChildAt(it).visibility!=View.GONE}
        val available=MeasureSpec.getSize(widthMeasureSpec)-paddingStart-paddingEnd
        val wide=MeasureSpec.getMode(widthMeasureSpec)!=MeasureSpec.UNSPECIFIED &&
            available>=dp(300) && resources.configuration.fontScale<=1.25f && visible.size==2
        if(lastWide!=wide || lastVisible!=visible) {
            lastWide=wide;lastVisible=visible
            orientation=if(wide) HORIZONTAL else VERTICAL
            for(i in 0 until childCount) {
                val child=getChildAt(i)
                child.layoutParams=LayoutParams(if(wide)0 else LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT,if(wide)1f else 0f).apply {
                    if(wide && i==visible.firstOrNull()) marginEnd=dp(6)
                    bottomMargin=dp(6)
                }
            }
        }
        super.onMeasure(widthMeasureSpec,heightMeasureSpec)
    }
}
