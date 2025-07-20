package com.hamham.gpsmover.favorites

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hamham.gpsmover.R
import com.hamham.gpsmover.viewmodel.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.view.ViewGroup

class FavoritesPage @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var viewModel: MainViewModel? = null
    private var onFavoriteClick: ((Favourite) -> Unit)? = null
    private var onBackClick: (() -> Unit)? = null
    private lateinit var itemTouchHelper: androidx.recyclerview.widget.ItemTouchHelper
    private lateinit var favListAdapter: FavListAdapter

    init {
        LayoutInflater.from(context).inflate(R.layout.fragment_favorites, this, true)
    }

    private fun setupRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: android.graphics.Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                val position = parent.getChildAdapterPosition(view)
                if (position != RecyclerView.NO_POSITION) {
                    if (position < parent.adapter?.itemCount?.minus(1) ?: 0) {
                        outRect.bottom = (3 * context.resources.displayMetrics.density).toInt()
                    }
                }
            }
        })
        favListAdapter = FavListAdapter()
        recyclerView.adapter = favListAdapter
        itemTouchHelper = com.hamham.gpsmover.favorites.createItemTouchHelper(favListAdapter)
        itemTouchHelper.attachToRecyclerView(recyclerView)
        favListAdapter.setItemTouchHelper(itemTouchHelper)
        favListAdapter.onItemDelete = { favourite ->
            viewModel?.deleteFavourite(favourite)
        }
        favListAdapter.onItemClick = { favourite ->
            onFavoriteClick?.invoke(favourite)
        }
        favListAdapter.onItemMove = { fromPosition, toPosition ->
            val updatedFavorites = favListAdapter.getItems().mapIndexed { index, favourite ->
                favourite.copy(order = index)
            }
            viewModel?.updateFavouritesOrder(updatedFavorites)
        }
    }

    fun setViewModel(viewModel: MainViewModel) {
        this.viewModel = viewModel
        CoroutineScope(Dispatchers.Main).launch {
            viewModel.allFavList.collect { favorites ->
                updateFavoritesList(favorites)
            }
        }
    }

    fun setOnFavoriteClick(listener: (Favourite) -> Unit) {
        this.onFavoriteClick = listener
    }

    fun setOnBackClick(listener: () -> Unit) {
        this.onBackClick = listener
    }

    private fun updateFavoritesList(favorites: List<Favourite>) {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        val emptyCard = findViewById<View>(R.id.emptyCard)
        val emptyTitle = findViewById<TextView>(R.id.emptyTitle)
        val emptyDescription = findViewById<TextView>(R.id.emptyDescription)
        if (favorites.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyCard.visibility = View.VISIBLE
            emptyTitle.text = context.getString(R.string.empty_favorites_title)
            emptyDescription.text = context.getString(R.string.empty_favorites_description)
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyCard.visibility = View.GONE
            favListAdapter.setItems(favorites)
        }
    }

    // Inner adapter class
    inner class FavListAdapter : RecyclerView.Adapter<FavListAdapter.ViewHolder>() {
        private val items = mutableListOf<Favourite>()
        private var itemTouchHelper: androidx.recyclerview.widget.ItemTouchHelper? = null
        var onItemClick : ((Favourite) -> Unit)? = null
        var onItemDelete : ((Favourite) -> Unit)? = null
        var onItemMove : ((Int, Int) -> Unit)? = null
        fun setItemTouchHelper(itemTouchHelper: androidx.recyclerview.widget.ItemTouchHelper) {
            this.itemTouchHelper = itemTouchHelper
        }
        fun setItems(newItems: List<Favourite>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
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
                    val pos = adapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        onItemClick?.invoke(items[pos])
                    }
                }
            }
            fun bind(favorite: Favourite){
                address.text = favorite.address
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
            holder.itemView.setOnLongClickListener { _ ->
                itemTouchHelper?.let { helper ->
                    helper.startDrag(holder)
                }
                true
            }
            holder.dragHandle.setOnTouchListener(null)
        }
        override fun getItemCount(): Int = items.size
        fun moveItem(fromPosition: Int, toPosition: Int) {
            if (fromPosition < 0 || toPosition < 0 || fromPosition >= items.size || toPosition >= items.size) return
            val item = items.removeAt(fromPosition)
            items.add(toPosition, item)
            notifyItemMoved(fromPosition, toPosition)
            val updatedItems = items.mapIndexed { index, favourite ->
                favourite.copy(order = index)
            }
            items.clear()
            items.addAll(updatedItems)
            onItemMove?.invoke(fromPosition, toPosition)
        }
    }
}

// Top-level helper function for ItemTouchHelper
fun createItemTouchHelper(adapter: FavoritesPage.FavListAdapter): androidx.recyclerview.widget.ItemTouchHelper {
    return androidx.recyclerview.widget.ItemTouchHelper(object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
        androidx.recyclerview.widget.ItemTouchHelper.UP or androidx.recyclerview.widget.ItemTouchHelper.DOWN, 0
    ) {
        override fun isLongPressDragEnabled(): Boolean = true
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val fromPos = viewHolder.adapterPosition
            val toPos = target.adapterPosition
            if (fromPos != RecyclerView.NO_POSITION && toPos != RecyclerView.NO_POSITION) {
                adapter.moveItem(fromPos, toPos)
            }
            return true
        }
        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            when (actionState) {
                androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_DRAG -> {
                    viewHolder?.itemView?.apply {
                        alpha = 0.7f
                        elevation = 12f
                        scaleX = 1.05f
                        scaleY = 1.05f
                    }
                }
                androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_IDLE -> {
                    viewHolder?.itemView?.apply {
                        alpha = 1.0f
                        elevation = 0f
                        scaleX = 1.0f
                        scaleY = 1.0f
                    }
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
        }
    })
} 