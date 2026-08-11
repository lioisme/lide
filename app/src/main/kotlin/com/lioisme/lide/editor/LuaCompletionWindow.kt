package com.lioisme.lide.editor

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.AdapterView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import io.github.rosemoe.sora.widget.component.CompletionLayout
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.component.EditorCompletionAdapter
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/**
 * T6（2026-08-11）补全窗口自定义（对齐 Squircle `SquircleCompletion`）：
 * 圆角 8dp + 1dp 描边（[EditorColorScheme.COMPLETION_WND_CORNER]）、
 * 背景 [EditorColorScheme.COMPLETION_WND_BACKGROUND]、当前项
 * [EditorColorScheme.COMPLETION_WND_ITEM_CURRENT]、匹配文字（sora 发布期已把
 * `COMPLETION_WND_TEXT_MATCHED` 高亮写入 label span，本 adapter 直接 setText）。
 *
 * 全色跟随：[onApplyColorScheme] 随主题切换重建圆角背景；缺色回退
 * [EditorColorScheme.getColor] 默认值，读取失败静默降级（sora 主循环不逃逸异常）。
 */
class LuaCompletionWindow : CompletionLayout {

    private var listView: ListView? = null
    private var rootView: LinearLayout? = null
    private var editorAutoCompletion: EditorAutoCompletion? = null

    override fun setEditorCompletion(completion: EditorAutoCompletion) {
        editorAutoCompletion = completion
    }

    override fun setEnabledAnimation(enabledAnimation: Boolean) = Unit

    override fun inflate(context: Context): View {
        rootView =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = roundedBackground(context, color = Color.TRANSPARENT)
                clipToOutline = true
            }
        listView =
            ListView(context).apply {
                dividerHeight = 0
                onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
                    try {
                        editorAutoCompletion?.select(position)
                    } catch (_: Throwable) {
                        // 选择失败静默降级（补全窗口生命周期由 sora 主循环管理）。
                    }
                }
            }
        rootView?.addView(listView, LinearLayout.LayoutParams(-1, -1))
        return rootView!!
    }

    override fun onApplyColorScheme(colorScheme: EditorColorScheme) {
        try {
            val ctx = editorAutoCompletion?.context ?: return
            rootView?.background =
                roundedBackground(
                    ctx,
                    color = colorScheme.getColor(EditorColorScheme.COMPLETION_WND_BACKGROUND),
                    stroke =
                        colorScheme.getColor(EditorColorScheme.COMPLETION_WND_CORNER),
                )
            rootView?.let(::setRoundedOutline)
        } catch (_: Throwable) {
            // 色板读取失败保持上一背景（不逃逸进 sora 主循环）。
        }
    }

    override fun setLoading(state: Boolean) = Unit

    override fun getCompletionList(): ListView = listView!!

    override fun ensureListPositionVisible(position: Int, increment: Int) {
        listView?.post {
            while (listView!!.firstVisiblePosition + 1 > position && listView!!.canScrollList(-1)) {
                performScrollList(increment / 2)
            }
            while (listView!!.lastVisiblePosition - 1 < position && listView!!.canScrollList(1)) {
                performScrollList(-increment / 2)
            }
        }
    }

    private fun performScrollList(offset: Int) {
        val list = listView ?: return
        val down = SystemClock.uptimeMillis()
        var ev = MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
        list.onTouchEvent(ev)
        ev.recycle()
        ev = MotionEvent.obtain(down, down, MotionEvent.ACTION_MOVE, 0f, offset.toFloat(), 0)
        list.onTouchEvent(ev)
        ev.recycle()
        ev = MotionEvent.obtain(down, down, MotionEvent.ACTION_CANCEL, 0f, offset.toFloat(), 0)
        list.onTouchEvent(ev)
        ev.recycle()
    }

    private fun roundedBackground(
        context: Context,
        color: Int,
        stroke: Int = Color.TRANSPARENT,
    ): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius =
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    CORNER_RADIUS_DP,
                    context.resources.displayMetrics,
                )
            setStroke(
                TypedValue
                    .applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        STROKE_WIDTH_DP,
                        context.resources.displayMetrics,
                    )
                    .toInt(),
                stroke,
            )
            setColor(color)
        }

    private fun setRoundedOutline(view: View) {
        view.outlineProvider =
            object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(
                        0,
                        0,
                        view.width,
                        view.height,
                        TypedValue
                            .applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP,
                                CORNER_RADIUS_DP,
                                view.context.resources.displayMetrics,
                            ),
                    )
                }
            }
        view.clipToOutline = true
    }

    /**
     * 行项：图标 + 标签（primary 色，匹配段由 sora span 高亮）+ 描述（secondary 色）。
     * 当前项背景 = [EditorColorScheme.COMPLETION_WND_ITEM_CURRENT]，非当前透明。
     */
    class Adapter : EditorCompletionAdapter() {

        override fun getView(
            position: Int,
            convertView: View?,
            parent: ViewGroup?,
            isCurrentCursorPosition: Boolean,
        ): View {
            val item = getItem(position)
            val view =
                convertView ?: LayoutInflater.from(context)
                    .inflate(io.github.rosemoe.sora.R.layout.default_completion_result_item, parent, false)
            view.tag = position

            view.findViewById<ImageView>(io.github.rosemoe.sora.R.id.result_item_image)
                ?.setImageDrawable(item.icon)
            view.findViewById<TextView>(io.github.rosemoe.sora.R.id.result_item_label)?.apply {
                text = item.label
                setTextColor(getThemeColor(EditorColorScheme.COMPLETION_WND_TEXT_PRIMARY))
                setTypeface(Typeface.MONOSPACE)
            }
            view.findViewById<TextView>(io.github.rosemoe.sora.R.id.result_item_desc)?.apply {
                text = item.desc
                setTextColor(getThemeColor(EditorColorScheme.COMPLETION_WND_TEXT_SECONDARY))
            }

            view.setBackgroundColor(
                if (isCurrentCursorPosition) {
                    getThemeColor(EditorColorScheme.COMPLETION_WND_ITEM_CURRENT)
                } else {
                    Color.TRANSPARENT
                },
            )
            return view
        }

        override fun getItemHeight(): Int =
            TypedValue
                .applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    ITEM_HEIGHT_DP,
                    context.resources.displayMetrics,
                )
                .toInt()
    }

    private companion object {
        const val CORNER_RADIUS_DP = 8f
        const val STROKE_WIDTH_DP = 1f
        const val ITEM_HEIGHT_DP = 46f
    }
}
