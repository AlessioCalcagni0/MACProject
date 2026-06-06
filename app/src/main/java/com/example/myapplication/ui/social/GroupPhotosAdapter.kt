package com.example.myapplication.ui.social

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.myapplication.R
import com.google.android.material.card.MaterialCardView
import java.io.File

class GroupPhotosAdapter(
    private var photos: List<String>,
    private val isSelectionEnabled: Boolean = false,
    private var selectedPhotoUrl: String? = null,
    private val onPhotoSelected: ((String) -> Unit)? = null
) : RecyclerView.Adapter<GroupPhotosAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivPhoto: ImageView = view.findViewById(R.id.ivGroupPhoto)
        val ivCheck: ImageView = view.findViewById(R.id.ivCheckCover)
        val card: MaterialCardView = view.findViewById(R.id.cardPhotoContainer)
    }

    fun updatePhotos(newList: List<String>) {
        photos = newList
        notifyDataSetChanged()
    }

    fun getPhotos(): List<String> = photos
    
    fun getSelectedPhoto(): String? = selectedPhotoUrl

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_group_photo, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val url = photos[position]
        val imageSource = if (url.startsWith("http")) {
            url
        } else {
            File(url)
        }

        Glide.with(holder.ivPhoto.context)
            .load(imageSource)
            .placeholder(R.drawable.ic_run)
            .error(R.drawable.ic_run)
            .into(holder.ivPhoto)

        if (isSelectionEnabled) {
            holder.ivCheck.visibility = if (url == selectedPhotoUrl) View.VISIBLE else View.GONE
            holder.card.strokeWidth = if (url == selectedPhotoUrl) 4 else 0
            
            holder.itemView.setOnClickListener {
                selectedPhotoUrl = url
                notifyDataSetChanged()
                onPhotoSelected?.invoke(url)
            }
        } else {
            holder.ivCheck.visibility = View.GONE
            holder.card.strokeWidth = 0
        }
    }

    override fun getItemCount() = photos.size
}
