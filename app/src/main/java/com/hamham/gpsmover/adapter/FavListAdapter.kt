package com.hamham.gpsmover.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hamham.gpsmover.R
import com.hamham.gpsmover.room.Favourite

class FavListAdapter(
    ) : ListAdapter<Favourite,FavListAdapter.ViewHolder>(FavListComparetor()) {

    var onItemClick : ((Favourite) -> Unit)? = null
    var onItemDelete : ((Favourite) -> Unit)? = null

   inner class ViewHolder(view: View): RecyclerView.ViewHolder(view) {

        private val address: TextView = view.findViewById(R.id.address)
        private val coords: TextView = view.findViewById(R.id.coords)
        private val delete: ImageView = itemView.findViewById(R.id.del)

        fun bind(favorite: Favourite){
            address.text = favorite.address
            coords.visibility = View.GONE
            delete.setOnClickListener {
                onItemDelete?.invoke(favorite)
            }
            itemView.setOnClickListener {
                onItemClick?.invoke(favorite)
            }
        }
    }

    class FavListComparetor : DiffUtil.ItemCallback<Favourite>() {
        override fun areItemsTheSame(oldItem: Favourite, newItem: Favourite): Boolean {
            return oldItem.address == newItem.address
        }

        override fun areContentsTheSame(oldItem: Favourite, newItem: Favourite): Boolean {
            return oldItem == newItem
        }

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.fav_items, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        if (item != null){
            holder.bind(item)

            // Set alternating background colors
            val backgroundColor = if (position % 2 == 0) {
                android.R.color.system_neutral1_50 // Light background for even positions
            } else {
                android.R.color.system_neutral1_100 // Darker background for odd positions
            }
            holder.itemView.setBackgroundResource(backgroundColor)
        }
    }



}