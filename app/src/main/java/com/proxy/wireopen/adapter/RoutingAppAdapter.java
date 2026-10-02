package com.proxy.wireopen.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.proxy.wireopen.R;
import com.proxy.wireopen.model.AppItem;
import com.proxy.wireopen.util.SplitTunnelManager;

import java.util.ArrayList;
import java.util.List;

public class RoutingAppAdapter extends RecyclerView.Adapter<RoutingAppAdapter.ViewHolder> {

    public interface OnSelectionChangedListener {
        void onSelectionChanged(int selectedCount);
    }

    private final Context context;
    private final List<AppItem> fullList = new ArrayList<>();
    private final List<AppItem> filteredList = new ArrayList<>();
    private final OnSelectionChangedListener selectionListener;

    private String currentSearch = "";
    private boolean includeSystem = false;

    public RoutingAppAdapter(Context context, OnSelectionChangedListener listener) {
        this.context = context;
        this.selectionListener = listener;
    }

    public void setAppList(List<AppItem> apps) {
        fullList.clear();
        fullList.addAll(apps);
        filter();
    }

    public void setSearchQuery(String query) {
        this.currentSearch = query != null ? query.trim().toLowerCase() : "";
        filter();
    }

    public void setIncludeSystem(boolean include) {
        this.includeSystem = include;
        filter();
    }

    public int getSelectedCount() {
        int count = 0;
        for (AppItem app : fullList) {
            if (app.isSelected()) count++;
        }
        return count;
    }

    private void filter() {
        filteredList.clear();
        for (AppItem item : fullList) {
            if (!includeSystem && item.isSystem()) {
                continue;
            }
            if (!currentSearch.isEmpty()) {
                boolean matchName = item.getAppName().toLowerCase().contains(currentSearch);
                boolean matchPkg = item.getPackageName().toLowerCase().contains(currentSearch);
                if (!matchName && !matchPkg) {
                    continue;
                }
            }
            filteredList.add(item);
        }
        notifyDataSetChanged();
        if (selectionListener != null) {
            selectionListener.onSelectionChanged(getSelectedCount());
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_routing_app, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppItem item = filteredList.get(position);

        holder.tvAppName.setText(item.getAppName());
        holder.tvAppPackage.setText(item.getPackageName());
        if (item.getIcon() != null) {
            holder.ivAppIcon.setImageDrawable(item.getIcon());
        } else {
            holder.ivAppIcon.setImageResource(android.R.drawable.sym_def_app_icon);
        }

        if (item.isSystem()) {
            holder.tvAppTypeTag.setText("系统");
            holder.tvAppTypeTag.setTextColor(ContextCompat.getColor(context, R.color.status_orange));
        } else {
            holder.tvAppTypeTag.setText("用户");
            holder.tvAppTypeTag.setTextColor(ContextCompat.getColor(context, R.color.brand_primary));
        }

        holder.cbAppSelected.setChecked(item.isSelected());

        holder.itemView.setOnClickListener(v -> {
            boolean newState = !item.isSelected();
            item.setSelected(newState);
            holder.cbAppSelected.setChecked(newState);
            SplitTunnelManager.togglePackage(context, item.getPackageName(), newState);
            if (selectionListener != null) {
                selectionListener.onSelectionChanged(getSelectedCount());
            }
        });
    }

    @Override
    public int getItemCount() {
        return filteredList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAppIcon;
        TextView tvAppName;
        TextView tvAppTypeTag;
        TextView tvAppPackage;
        CheckBox cbAppSelected;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAppIcon = itemView.findViewById(R.id.iv_app_icon);
            tvAppName = itemView.findViewById(R.id.tv_app_name);
            tvAppTypeTag = itemView.findViewById(R.id.tv_app_type_tag);
            tvAppPackage = itemView.findViewById(R.id.tv_app_package);
            cbAppSelected = itemView.findViewById(R.id.cb_app_selected);
        }
    }
}
