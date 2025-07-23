package com.hamham.gpsmover.feature.locations.presentation.adapter

import android.graphics.Canvas
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

/**
 * Enhanced ItemTouchHelper callback for improved drag and drop functionality
 * Fixes issues with premature dropping and provides better visual feedback
 */
class LocationTouchHelperCallback(
    private val adapter: LocationsAdapter
) : ItemTouchHelper.SimpleCallback(
    ItemTouchHelper.UP or ItemTouchHelper.DOWN, 
    0 // No swipe actions
) {

    private var isDragging = false

    override fun isLongPressDragEnabled(): Boolean = true

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        // Allow vertical dragging only
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
        return makeMovementFlags(dragFlags, 0)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val fromPosition = viewHolder.adapterPosition
        val toPosition = target.adapterPosition
        
        if (fromPosition != RecyclerView.NO_POSITION && toPosition != RecyclerView.NO_POSITION) {
            adapter.moveItem(fromPosition, toPosition)
            return true
        }
        return false
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // No swipe functionality needed
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        
        when (actionState) {
            ItemTouchHelper.ACTION_STATE_DRAG -> {
                isDragging = true
                viewHolder?.itemView?.apply {
                    // Simple visual feedback without scaling
                    alpha = 0.8f
                    elevation = 8f
                    translationZ = 4f
                }
            }
            ItemTouchHelper.ACTION_STATE_IDLE -> {
                isDragging = false
            }
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        
        isDragging = false
        viewHolder.itemView.apply {
            // Quick animation back to original state without scaling
            animate()
                .alpha(1.0f)
                .translationZ(0f)
                .setDuration(100)
                .start()
            elevation = 0f
        }
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && isCurrentlyActive) {
            // Maintain visual feedback during active dragging without scaling
            viewHolder.itemView.apply {
                alpha = 0.8f
                elevation = 8f
                translationZ = 4f
            }
        }
    }

    override fun getAnimationDuration(
        recyclerView: RecyclerView,
        animationType: Int,
        animateDx: Float,
        animateDy: Float
    ): Long {
        // Use faster animations for more responsive feel
        return when (animationType) {
            ItemTouchHelper.ANIMATION_TYPE_DRAG -> 100L
            else -> super.getAnimationDuration(recyclerView, animationType, animateDx, animateDy)
        }
    }

    override fun getMoveThreshold(viewHolder: RecyclerView.ViewHolder): Float {
        // Use lower threshold for easier and more responsive dragging
        return 0.1f
    }

    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float {
        // Disable swipe completely
        return Float.MAX_VALUE
    }
}
