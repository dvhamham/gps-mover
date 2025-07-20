package com.hamham.gpsmover.favorites

import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.hamham.gpsmover.R
import com.hamham.gpsmover.favorites.Favourite
import androidx.core.content.ContextCompat
import android.util.TypedValue

class FavListAdapter : RecyclerView.Adapter<FavListAdapter.ViewHolder>() {

    private val items = mutableListOf<Favourite>()
    private var itemTouchHelper: ItemTouchHelper? = null
    var onItemClick : ((Favourite) -> Unit)? = null
    var onItemDelete : ((Favourite) -> Unit)? = null
    var onItemMove : ((Int, Int) -> Unit)? = null

    fun setItemTouchHelper(itemTouchHelper: ItemTouchHelper) {
        if (this.itemTouchHelper != itemTouchHelper) {
            this.itemTouchHelper = itemTouchHelper
            Log.d("FavListAdapter", "ItemTouchHelper set successfully")
        } else {
            Log.d("FavListAdapter", "ItemTouchHelper already set, skipping")
        }
    }

    fun setItems(newItems: List<Favourite>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
        Log.d("FavListAdapter", "Items updated: ${items.size} items")
    }

    fun getItems(): List<Favourite> = items

    inner class ViewHolder(view: View): RecyclerView.ViewHolder(view) {
        val address: TextView = view.findViewById(R.id.address)
        val coords: TextView = view.findViewById(R.id.coords)
        val delete: ImageView = itemView.findViewById(R.id.del)
        val dragHandle: ImageView = itemView.findViewById(R.id.drag_handle)

        init {
            delete.setOnClickListener {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onItemDelete?.invoke(items[pos])
                }
            }
            itemView.setOnClickListener {
                // Remove haptic feedback here
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onItemClick?.invoke(items[pos])
                }
            }
        }

        fun bind(favorite: Favourite){
            address.text = favorite.address
            // Shorten the coordinates to show only 6 digits after the decimal point
            val formattedLat = String.format("%.6f", favorite.lat ?: 0.0)
            val formattedLng = String.format("%.6f", favorite.lng ?: 0.0)
            coords.text = "$formattedLat, $formattedLng"
            coords.visibility = View.VISIBLE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.fav_items, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
        // Enable drag from anywhere in the entire item
        holder.itemView.setOnLongClickListener { view ->
            Log.d("FavListAdapter", "Long press detected on item at position $position")
            itemTouchHelper?.let { helper ->
                Log.d("FavListAdapter", "Starting drag for position $position")
                helper.startDrag(holder)
            } ?: run {
                Log.e("FavListAdapter", "ItemTouchHelper is null!")
            }
            true
        }
        // Remove enabling drag from icon only
        holder.dragHandle.setOnTouchListener(null)
    }

    override fun getItemCount(): Int = items.size

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition < 0 || toPosition < 0 || fromPosition >= items.size || toPosition >= items.size) return
        
        Log.d("FavListAdapter", "Moving item from $fromPosition to $toPosition")
        
        val item = items.removeAt(fromPosition)
        items.add(toPosition, item)
        notifyItemMoved(fromPosition, toPosition)
        
        // Update order for all items after move
        val updatedItems = items.mapIndexed { index, favourite ->
            favourite.copy(order = index)
        }
        items.clear()
        items.addAll(updatedItems)
        
        onItemMove?.invoke(fromPosition, toPosition)
    }

    companion object {
        fun createItemTouchHelper(adapter: FavListAdapter): ItemTouchHelper {
            return ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
            ) {
                override fun isLongPressDragEnabled(): Boolean = true // Allow drag from anywhere

                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    val fromPos = viewHolder.adapterPosition
                    val toPos = target.adapterPosition
                    Log.d("FavListAdapter", "onMove called: from $fromPos to $toPos")
                    if (fromPos != RecyclerView.NO_POSITION && toPos != RecyclerView.NO_POSITION) {
                        adapter.moveItem(fromPos, toPos)
                    }
                    return true
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    // Not used for drag and drop
                }

                override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                    super.onSelectedChanged(viewHolder, actionState)
                    Log.d("FavListAdapter", "onSelectedChanged: actionState = $actionState")
                    when (actionState) {
                        ItemTouchHelper.ACTION_STATE_DRAG -> {
                            viewHolder?.itemView?.apply {
                                alpha = 0.7f
                                elevation = 12f
                                scaleX = 1.05f
                                scaleY = 1.05f
                            }
                            Log.d("FavListAdapter", "Drag started")
                        }
                        ItemTouchHelper.ACTION_STATE_IDLE -> {
                            viewHolder?.itemView?.apply {
                                alpha = 1.0f
                                elevation = 0f
                                scaleX = 1.0f
                                scaleY = 1.0f
                            }
                            Log.d("FavListAdapter", "Drag ended")
                        }
                    }
                }

                override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                    super.clearView(recyclerView, viewHolder)
                    viewHolder.itemView.apply {
                        alpha = 1.0f
                        elevation = 0f
                        scaleX = 1.0f
                        scaleY = 1.0f
                    }
                    Log.d("FavListAdapter", "clearView called")
                }
            })
        }
    }
}