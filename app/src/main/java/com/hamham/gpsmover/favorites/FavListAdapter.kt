package com.hamham.gpsmover.favorites

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.hamham.gpsmover.R
import com.hamham.gpsmover.helpers.performHapticClick

class FavListAdapter : RecyclerView.Adapter<FavListAdapter.ViewHolder>() {

    private val items = mutableListOf<Favourite>()
    var onItemClick: ((Favourite) -> Unit)? = null
    var onItemDelete: ((Favourite) -> Unit)? = null
    var onItemMove: (() -> Unit)? = null

    fun setItems(newItems: List<Favourite>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun getItems(): List<Favourite> = items

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition in items.indices && toPosition in items.indices) {
            val item = items.removeAt(fromPosition)
            items.add(toPosition, item)
            notifyItemMoved(fromPosition, toPosition)
            onItemMove?.invoke()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.fav_items, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val address: TextView = view.findViewById(R.id.address)
        private val coords: TextView = view.findViewById(R.id.coords)
        private val deleteButton: ImageView = view.findViewById(R.id.del)

        init {
            itemView.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onItemClick?.invoke(items[adapterPosition])
                }
            }
            deleteButton.setOnClickListener {
                it.performHapticClick()
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onItemDelete?.invoke(items[adapterPosition])
                }
            }
        }

        fun bind(favourite: Favourite) {
            address.text = favourite.address
            val lat = String.format("%.6f", favourite.lat)
            val lng = String.format("%.6f", favourite.lng)
            coords.text = "$lat, $lng"
        }
    }
} 