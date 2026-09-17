package com.zamankilidi.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppListAdapter(initialApps: List<AppInfo>) :
    RecyclerView.Adapter<AppListAdapter.VH>() {

    private var apps: List<AppInfo> = initialApps

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivIcon)
        val label: TextView = view.findViewById(R.id.tvLabel)
        val checkbox: CheckBox = view.findViewById(R.id.cbAllowed)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = apps[position]
        holder.icon.setImageDrawable(app.icon)
        holder.label.text = app.label
        // Checkbox'ın kendi eski state'ini geri çağırmasını önlemek için
        // dinleyiciyi kaldırıp koyuyoruz (RecyclerView view'ları tekrar kullanır).
        holder.checkbox.setOnCheckedChangeListener(null)
        holder.checkbox.isChecked = app.allowed
        holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            app.allowed = isChecked
        }
        holder.itemView.setOnClickListener {
            holder.checkbox.isChecked = !holder.checkbox.isChecked
        }
    }

    override fun getItemCount(): Int = apps.size

    /**
     * Sıralama modu değiştiğinde aynı AppInfo nesnelerini (dolayısıyla
     * "allowed" işaretlerini kaybetmeden) yeni sırada gösterir.
     */
    fun updateList(newApps: List<AppInfo>) {
        apps = newApps
        notifyDataSetChanged()
    }
}
