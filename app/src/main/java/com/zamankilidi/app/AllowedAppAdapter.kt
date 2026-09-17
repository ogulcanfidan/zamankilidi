package com.zamankilidi.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// NOT: Burada ayrı bir "LaunchableApp" veri sınıfı yerine BİLEREK AppInfo
// (MainActivity'de kullanılan aynı sınıf) kullanılıyor - ikisi zaten
// packageName/label/icon alanlarında birebir aynıydı, sadece AppInfo'nun
// fazladan bir "allowed" alanı vardı. Burada hepsi zaten izinli olduğu için
// (bu liste sadece izinli uygulamaları gösteriyor) o alan hep true.
class AllowedAppAdapter(
    private val apps: List<AppInfo>,
    private val onClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<AllowedAppAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivIcon)
        val label: TextView = view.findViewById(R.id.tvLabel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_allowed_app, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = apps[position]
        holder.icon.setImageDrawable(app.icon)
        holder.label.text = app.label
        holder.itemView.setOnClickListener { onClick(app) }
    }

    override fun getItemCount(): Int = apps.size
}
