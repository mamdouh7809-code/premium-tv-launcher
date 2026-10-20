package com.premiumtv.launcher

import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class HomeAdapter(
    private val prefs: Preferences,
    private val icons: IconCache,
    private val selected: (HomeItem.Tile) -> Unit,
    private val opened: (HomeItem.Tile) -> Unit,
    private val managed: (HomeItem.Tile) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var items: List<HomeItem> = emptyList()
        private set
    var columns: Int = 5
    var tileHeight = 126
    var navigator = FocusNavigator(emptyList(), columns)
        private set

    fun submit(newItems: List<HomeItem>) {
        items = newItems
        navigator = FocusNavigator(newItems.map { it is HomeItem.Tile }, columns)
        notifyDataSetChanged()
    }
    fun indexOf(key: String): Int = items.indexOfFirst { it.key == key }
    fun firstTile(): Int = items.indexOfFirst { it is HomeItem.Tile }
    override fun getItemCount() = items.size
    override fun getItemViewType(position: Int) = when (items[position]) {
        is HomeItem.Header -> 0
        is HomeItem.Message -> 1
        is HomeItem.Tile -> 2
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val context = parent.context
        if (viewType != 2) {
            val text = Ui.text(context, "", if (viewType == 0) 18f else 12f,
                if (viewType == 0) Ui.WHITE else Ui.MUTED, viewType == 0)
            text.gravity = Gravity.CENTER_VERTICAL or Gravity.START
            text.setPadding(context.dp(7), context.dp(8), context.dp(7), context.dp(4))
            text.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                context.dp(if (viewType == 0) 43 else 52))
            return TextHolder(text)
        }
        val frame = FrameLayout(context).apply {
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(tileHeight)).apply {
                setMargins(context.dp(7), context.dp(7), context.dp(7), context.dp(7))
            }
            isFocusable = true
            isFocusableInTouchMode = true
            background = Ui.background(context, prefs.accent, 17)
            stateListAnimator = null
            clipToOutline = true
        }
        val icon = ImageView(context).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        frame.addView(icon, FrameLayout.LayoutParams(context.dp(50), context.dp(50),
            Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = context.dp(14) })
        val title = Ui.text(context, "", 13f).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        frame.addView(title, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
            bottomMargin = context.dp(12); marginStart = context.dp(9); marginEnd = context.dp(9)
        })
        val favorite = Ui.text(context, "★", 12f, prefs.accent).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        frame.addView(favorite, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
            topMargin = context.dp(9); marginEnd = context.dp(11)
        })
        return TileHolder(frame, icon, title, favorite).apply {
            frame.setOnClickListener { tile(this)?.let(opened) }
            frame.setOnLongClickListener { tile(this)?.let(managed); true }
            frame.onFocusChangeListener = View.OnFocusChangeListener { view, focused ->
                Ui.animateFocus(view, focused, prefs.animations)
                if (focused) tile(this)?.let(selected)
            }
        }
    }

    private fun tile(holder: RecyclerView.ViewHolder): HomeItem.Tile? =
        items.getOrNull(holder.bindingAdapterPosition) as? HomeItem.Tile

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is HomeItem.Header -> (holder as TextHolder).text.text = item.title
            is HomeItem.Message -> (holder as TextHolder).text.text = item.text
            is HomeItem.Tile -> {
                holder as TileHolder
                holder.itemView.tag = item
                holder.itemView.contentDescription = item.app.label
                holder.title.text = item.app.label
                holder.favorite.visibility = if (item.favorite) View.VISIBLE else View.GONE
                holder.itemView.background = Ui.background(holder.itemView.context, prefs.accent, 17)
                icons.bind(item.app, holder.icon)
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        holder.itemView.animate().cancel()
        holder.itemView.scaleX = 1f
        holder.itemView.scaleY = 1f
        if (holder is TileHolder) {
            holder.icon.tag = null
            holder.icon.setImageDrawable(null)
        }
        super.onViewRecycled(holder)
    }

    private class TextHolder(val text: TextView) : RecyclerView.ViewHolder(text)
    private class TileHolder(frame: FrameLayout, val icon: ImageView, val title: TextView,
        val favorite: TextView) : RecyclerView.ViewHolder(frame)
}
