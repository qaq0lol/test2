package com.proxy.wireopen.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.proxy.wireopen.R;
import com.proxy.wireopen.model.ConnectionRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RequestListAdapter extends RecyclerView.Adapter<RequestListAdapter.ViewHolder> {

    public interface OnDisconnectClickListener {
        void onDisconnect(ConnectionRecord record, int position);
    }

    private final List<ConnectionRecord> fullList = new ArrayList<>();
    private final List<ConnectionRecord> filteredList = new ArrayList<>();
    private String currentFilterQuery = "";
    private final OnDisconnectClickListener disconnectListener;

    public RequestListAdapter(OnDisconnectClickListener listener) {
        this.disconnectListener = listener;
    }

    public synchronized void setData(List<ConnectionRecord> list) {
        fullList.clear();
        if (list != null) {
            fullList.addAll(list);
        }
        applyFilter(currentFilterQuery);
    }

    public synchronized void filter(String query) {
        this.currentFilterQuery = query != null ? query.trim().toLowerCase(Locale.ROOT) : "";
        applyFilter(this.currentFilterQuery);
    }

    private void applyFilter(String query) {
        filteredList.clear();
        if (query.isEmpty()) {
            filteredList.addAll(fullList);
        } else {
            for (ConnectionRecord record : fullList) {
                if (matchesFilter(record, query)) {
                    filteredList.add(record);
                }
            }
        }
        notifyDataSetChanged();
    }

    private boolean matchesFilter(ConnectionRecord r, String query) {
        if (r.getTargetUrl() != null && r.getTargetUrl().toLowerCase(Locale.ROOT).contains(query)) return true;
        if (r.getNodeName() != null && r.getNodeName().toLowerCase(Locale.ROOT).contains(query)) return true;
        if (r.getRule() != null && r.getRule().toLowerCase(Locale.ROOT).contains(query)) return true;
        return false;
    }

    public synchronized void removeAt(int position) {
        if (position >= 0 && position < filteredList.size()) {
            ConnectionRecord r = filteredList.remove(position);
            fullList.remove(r);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, filteredList.size() - position);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_connection_request, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ConnectionRecord record = filteredList.get(position);
        holder.tvTarget.setText(record.getTargetUrl());
        holder.tvTraffic.setText(record.formatTrafficStr());
        holder.tvNode.setText(record.getNodeFlag() + " " + record.getNodeName());
        holder.tvRule.setText(record.getRule());

        holder.btnDisconnect.setOnClickListener(v -> {
            if (disconnectListener != null) {
                int pos = holder.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && pos < filteredList.size()) {
                    disconnectListener.onDisconnect(filteredList.get(pos), pos);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return filteredList.size();
    }

    public int getFullItemCount() {
        return fullList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvTarget;
        final TextView tvTraffic;
        final TextView tvNode;
        final TextView tvRule;
        final TextView btnDisconnect;

        ViewHolder(View itemView) {
            super(itemView);
            tvTarget = itemView.findViewById(R.id.tv_request_target);
            tvTraffic = itemView.findViewById(R.id.tv_request_traffic);
            tvNode = itemView.findViewById(R.id.tv_request_node);
            tvRule = itemView.findViewById(R.id.tv_request_rule);
            btnDisconnect = itemView.findViewById(R.id.btn_disconnect_request);
        }
    }
}
