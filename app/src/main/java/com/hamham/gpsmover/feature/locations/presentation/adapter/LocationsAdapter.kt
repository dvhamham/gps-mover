package com.hamham.gpsmover.feature.locations.presentation.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hamham.gpsmover.R
import com.hamham.gpsmover.feature.locations.domain.model.Location
import com.hamham.gpsmover.helpers.performHapticClick

/**
 * Modern RecyclerView adapter for locations using ListAdapter and DiffUtil
 */
class LocationsAdapter : ListAdapter<Location, LocationsAdapter.LocationViewHolder>(LocationDiffCallback()) {

    var onLocationClick: ((Location) -> Unit)? = null
    var onLocationDelete: ((Location) -> Unit)? = null
    var onLocationMove: (() -> Unit)? = null

    fun moveItem(fromPosition: Int, toPosition: Int) {
        val currentList = currentList.toMutableList()
        if (fromPosition in currentList.indices && toPosition in currentList.indices && fromPosition != toPosition) {
            val item = currentList.removeAt(fromPosition)
            currentList.add(toPosition, item)
            
            // Update order field for proper persistence
            currentList.forEachIndexed { index, location ->
                // Create new location with updated order
                val updatedLocation = location.copy(order = index)
                currentList[index] = updatedLocation
            }
            
            // Submit the new list (ListAdapter requires a new list object)
            submitList(ArrayList(currentList)) {
                // Callback after list is submitted
                onLocationMove?.invoke()
            }
        }
    }

    fun getCurrentLocationsList(): List<Location> = currentList

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LocationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.fav_items, parent, false)
        return LocationViewHolder(view)
    }

    override fun onBindViewHolder(holder: LocationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class LocationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val address: TextView = itemView.findViewById(R.id.address)
        private val coords: TextView = itemView.findViewById(R.id.coords)
        private val deleteButton: ImageView = itemView.findViewById(R.id.del)

        init {
            itemView.setOnClickListener {
                it.performHapticClick()
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onLocationClick?.invoke(getItem(adapterPosition))
                }
            }
            
            deleteButton.setOnClickListener {
                it.performHapticClick()
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onLocationDelete?.invoke(getItem(adapterPosition))
                }
            }
        }

        fun bind(location: Location) {
            address.text = location.name
            coords.text = location.getFormattedCoordinates()
        }
    }
}

/**
 * DiffUtil callback for efficient list updates
 */
private class LocationDiffCallback : DiffUtil.ItemCallback<Location>() {
    override fun areItemsTheSame(oldItem: Location, newItem: Location): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Location, newItem: Location): Boolean {
        return oldItem == newItem
    }
}
