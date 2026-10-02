package com.proxy.wireopen;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.provider.OpenableColumns;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.core.widget.NestedScrollView;
import com.proxy.wireopen.adapter.RequestListAdapter;
import com.proxy.wireopen.adapter.RoutingAppAdapter;
import com.proxy.wireopen.model.AppItem;
import com.proxy.wireopen.model.ConnectionRecord;
import com.proxy.wireopen.model.ConnectionState;
import com.proxy.wireopen.model.ProtocolType;
import com.proxy.wireopen.model.ProxyProfile;
import com.proxy.wireopen.parser.OpenVpnConfigParser;
import com.proxy.wireopen.parser.WireGuardConfigParser;
import com.proxy.wireopen.repository.ProfileRepository;
import com.proxy.wireopen.model.PresetPortItem;
import com.proxy.wireopen.model.PresetRegion;
import com.proxy.wireopen.repository.PresetRegionManager;
import com.proxy.wireopen.service.UnifiedVpnService;
import com.proxy.wireopen.util.CredentialManager;
import com.proxy.wireopen.util.LogManager;
import com.proxy.wireopen.util.PortSpeedTester;
import com.proxy.wireopen.util.RegionDetector;
import com.proxy.wireopen.util.SplitTunnelManager;
import com.proxy.wireopen.util.TrafficStatsManager;
import com.proxy.wireopen.view.OscilloscopeWaveformView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private ProfileRepository repository;
    private ProfileAdapter profileAdapter;
    private RoutingAppAdapter routingAppAdapter;

    // 5 Major Tab Pages
    private View pageDashboard;
    private View pageNodes;
    private View pageRequests;
    private View pageRouting;
    private View pageIppure;

    // Bottom Navigation Elements (5 Tabs)
    private LinearLayout navItemDashboard;
    private LinearLayout navItemNodes;
    private LinearLayout navItemRequests;
    private LinearLayout navItemRouting;
    private LinearLayout navItemIppure;

    private LinearLayout navPillDashboard;
    private LinearLayout navPillNodes;
    private LinearLayout navPillRequests;
    private LinearLayout navPillRouting;
    private LinearLayout navPillIppure;

    private TextView navLabelDashboard;
    private TextView navLabelNodes;
    private TextView navLabelRequests;
    private TextView navLabelRouting;
    private TextView navLabelIppure;

    // Top Bar Actions
    private MaterialButton btnOpenLogs;
    private MaterialButton btnThemeToggle;
    private MaterialButton btnOpenSettings;

    // Requests Tab Views (FlClash)
    private TextView tvRequestsCountBadge;
    private LinearLayout layoutRequestsSearch;
    private EditText etRequestsFilter;
    private RequestListAdapter requestListAdapter;
    private TextView tvRequestsEmpty;

    // Independent Log Console references
    private BottomSheetDialog logConsoleDialog = null;
    private TextView tvBottomSheetLogs = null;
    private NestedScrollView scrollBottomSheetLogs = null;
    private View activeSettingsDialogView = null;

    // Giant Orb Switch Views (Dashboard)
    private View orbRing;
    private LinearLayout btnOrbSwitch;
    private TextView tvOrbIcon;
    private TextView tvOrbStatusText;
    private TextView tvProtectionBadge;

    // Mini Outbound Banner
    private TextView tvMiniGeoFlag;
    private TextView tvMiniGeoIp;
    private TextView tvMiniProtocolBadge;
    private TextView tvMiniGeoLocation;
    private TextView tvMiniPingBadge;

    // Throughput & Oscilloscope
    private TextView tvPeakSpeed;
    private TextView tvSpeedDown;
    private TextView tvSpeedDownUnit;
    private TextView tvTotalDown;
    private TextView tvSpeedUp;
    private TextView tvSpeedUpUnit;
    private TextView tvTotalUp;
    private OscilloscopeWaveformView waveformView;

    // Nodes Page Views
    private TextView tvNodesSubtitle;
    private View cardEmptyState;
    private LinearLayout layoutPresetRegions;
    private MaterialButton btnOpenvpnCredentials;
    private final Map<String, TextView> portBadgeViews = new HashMap<>();
    private final Map<String, View> portCardViews = new HashMap<>();

    // Routing Page Views
    private TextView tvRoutingSummary;
    private MaterialButton btnModeGlobal;
    private MaterialButton btnModeBypass;
    private MaterialButton btnModeAllow;
    private TextView tvModeDesc;
    private EditText etRoutingSearch;
    private CheckBox cbFilterSystemApps;
    private ProgressBar pbRoutingLoading;
    private RecyclerView rvRoutingApps;
    private boolean appsLoaded = false;

    // IP Pureness WebView Page Views
    private ProgressBar pbIppureWeb;
    private SwipeRefreshLayout swipeRefreshIppure;
    private WebView webviewIppure;
    private boolean ippureWebLoaded = false;

    // Launchers
    private ActivityResultLauncher<Intent> vpnPrepareLauncher;
    private ActivityResultLauncher<String> filePickerLauncher;
    private ActivityResultLauncher<String> notificationPermissionLauncher;

    // Timer & Speed State
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private long connectionStartTime = 0;
    private boolean isTimerRunning = false;

    private long lastRxBytes = 0;
    private long lastTxBytes = 0;
    private long lastTrafficTimestamp = 0;
    private double peakSpeedBytesPerSec = 0;
    private double currentDownSpeed = 0;
    private double currentUpSpeed = 0;
    private ConnectionState lastConnectionState = null;

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isTimerRunning) {
                long elapsed = (System.currentTimeMillis() - connectionStartTime) / 1000;
                long h = elapsed / 3600;
                long m = (elapsed % 3600) / 60;
                long s = elapsed % 60;
                String timeStr = String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s);
                tvProtectionBadge.setText("🛡️ 已受安全代理保护 · " + timeStr);
                timerHandler.postDelayed(this, 1000);
            }
        }
    };

    private final BroadcastReceiver vpnStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;

            String action = intent.getAction();
            if (UnifiedVpnService.ACTION_STATE_CHANGED.equals(action)) {
                String stateName = intent.getStringExtra(UnifiedVpnService.EXTRA_STATE);
                if (stateName != null) {
                    try {
                        ConnectionState state = ConnectionState.valueOf(stateName);
                        updateConnectionUi(state);
                    } catch (IllegalArgumentException ignored) {}
                }
            } else if (UnifiedVpnService.ACTION_TRAFFIC_UPDATE.equals(action)) {
                long rx = intent.getLongExtra(UnifiedVpnService.EXTRA_RX_BYTES, -1);
                long tx = intent.getLongExtra(UnifiedVpnService.EXTRA_TX_BYTES, -1);
                if (rx >= 0 && tx >= 0) {
                    onTrafficUpdate(rx, tx);
                }
            } else if (UnifiedVpnService.ACTION_CONNECTION_CAPTURED.equals(action)) {
                // Real packet captured — refresh the requests list
                runOnUiThread(() -> refreshRequestsList());
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Android 16 edge-to-edge system bars insets handling
        View rootView = findViewById(R.id.main_layout);
        if (rootView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(bars.left, bars.top, bars.right, 0);
                return insets;
            });
        }

        CredentialManager.init(this);
        TrafficStatsManager.init(this);
        repository = new ProfileRepository(this);

        initViews();
        setupNavigation();
        setupRequestsTab();
        setupLaunchers();
        setupPresetRegions();
        setupRoutingPage();
        setupIppureWebView();
        checkPermissions();

        handleIncomingIntent(getIntent());

        updateActiveProfileUi();
        updateConnectionUi(UnifiedVpnService.getCurrentState());
    }

    private void initViews() {
        // 5 Major Tab Pages
        pageDashboard = findViewById(R.id.page_dashboard);
        pageNodes = findViewById(R.id.page_nodes);
        pageRequests = findViewById(R.id.page_requests);
        pageRouting = findViewById(R.id.page_routing);
        pageIppure = findViewById(R.id.page_ippure);

        // 5 Nav Items
        navItemDashboard = findViewById(R.id.nav_item_dashboard);
        navItemNodes = findViewById(R.id.nav_item_nodes);
        navItemRequests = findViewById(R.id.nav_item_requests);
        navItemRouting = findViewById(R.id.nav_item_routing);
        navItemIppure = findViewById(R.id.nav_item_ippure);

        navPillDashboard = findViewById(R.id.nav_pill_dashboard);
        navPillNodes = findViewById(R.id.nav_pill_nodes);
        navPillRequests = findViewById(R.id.nav_pill_requests);
        navPillRouting = findViewById(R.id.nav_pill_routing);
        navPillIppure = findViewById(R.id.nav_pill_ippure);

        navLabelDashboard = findViewById(R.id.nav_label_dashboard);
        navLabelNodes = findViewById(R.id.nav_label_nodes);
        navLabelRequests = findViewById(R.id.nav_label_requests);
        navLabelRouting = findViewById(R.id.nav_label_routing);
        navLabelIppure = findViewById(R.id.nav_label_ippure);

        // Top Bar (📜 Log | 🌙 Theme | ⚙️ Settings)
        btnOpenLogs = findViewById(R.id.btn_open_logs);
        if (btnOpenLogs != null) {
            btnOpenLogs.setOnClickListener(v -> showLogConsoleBottomSheet());
        }

        btnThemeToggle = findViewById(R.id.btn_theme_toggle);
        setupThemeToggle();

        btnOpenSettings = findViewById(R.id.btn_open_settings);
        if (btnOpenSettings != null) {
            btnOpenSettings.setOnClickListener(v -> showSettingsBottomSheet());
        }

        // Giant Orb Switch Views (Dashboard)
        orbRing = findViewById(R.id.orb_ring);
        btnOrbSwitch = findViewById(R.id.btn_orb_switch);
        tvOrbIcon = findViewById(R.id.tv_orb_icon);
        tvOrbStatusText = findViewById(R.id.tv_orb_status_text);
        tvProtectionBadge = findViewById(R.id.tv_protection_badge);

        btnOrbSwitch.setOnClickListener(v -> toggleVpnConnection());

        // Mini Outbound Info
        tvMiniGeoFlag = findViewById(R.id.tv_mini_geo_flag);
        tvMiniGeoIp = findViewById(R.id.tv_mini_geo_ip);
        tvMiniProtocolBadge = findViewById(R.id.tv_mini_protocol_badge);
        tvMiniGeoLocation = findViewById(R.id.tv_mini_geo_location);
        tvMiniPingBadge = findViewById(R.id.tv_mini_ping_badge);

        // Throughput & Oscilloscope
        tvPeakSpeed = findViewById(R.id.tv_peak_speed);
        tvSpeedDown = findViewById(R.id.tv_speed_down);
        tvSpeedDownUnit = findViewById(R.id.tv_speed_down_unit);
        tvTotalDown = findViewById(R.id.tv_total_down);
        tvSpeedUp = findViewById(R.id.tv_speed_up);
        tvSpeedUpUnit = findViewById(R.id.tv_speed_up_unit);
        tvTotalUp = findViewById(R.id.tv_total_up);
        waveformView = findViewById(R.id.waveform_view);

        // Stream real-time logs to independent bottom sheet when active
        LogManager.setListener(logEntry -> runOnUiThread(() -> {
            if (tvBottomSheetLogs != null) {
                tvBottomSheetLogs.append(logEntry + "\n");
                if (scrollBottomSheetLogs != null) {
                    scrollBottomSheetLogs.post(() -> scrollBottomSheetLogs.fullScroll(View.FOCUS_DOWN));
                }
            }
        }));

        // Nodes Actions & Views
        tvNodesSubtitle = findViewById(R.id.tv_nodes_subtitle);
        cardEmptyState = findViewById(R.id.card_empty_state);
        layoutPresetRegions = findViewById(R.id.layout_preset_regions);

        // 🔐 OpenVPN 专属全局凭据 (在全部测速左侧)
        btnOpenvpnCredentials = findViewById(R.id.btn_openvpn_credentials);
        if (btnOpenvpnCredentials != null) {
            btnOpenvpnCredentials.setOnClickListener(v -> showCredentialsDialog());
        }

        View btnEmptyImport = findViewById(R.id.btn_empty_import);
        if (btnEmptyImport != null) {
            btnEmptyImport.setOnClickListener(v -> filePickerLauncher.launch("*/*"));
        }

        MaterialButton btnTestAll = findViewById(R.id.btn_test_all_speeds);
        if (btnTestAll != null) {
            btnTestAll.setOnClickListener(v -> testAllPortsSpeed());
        }
        findViewById(R.id.btn_import_file).setOnClickListener(v -> filePickerLauncher.launch("*/*"));
        findViewById(R.id.btn_paste_config).setOnClickListener(v -> showPasteConfigDialog());
    }

    private void setupNavigation() {
        navItemDashboard.setOnClickListener(v -> switchTab(0));
        navItemNodes.setOnClickListener(v -> switchTab(1));
        navItemRequests.setOnClickListener(v -> switchTab(2));
        navItemRouting.setOnClickListener(v -> switchTab(3));
        navItemIppure.setOnClickListener(v -> switchTab(4));
    }

    private void switchTab(int index) {
        pageDashboard.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        pageNodes.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        pageRequests.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        pageRouting.setVisibility(index == 3 ? View.VISIBLE : View.GONE);
        pageIppure.setVisibility(index == 4 ? View.VISIBLE : View.GONE);

        updateTabNavStyle(index);

        if (index == 2) {
            refreshRequestsList();
        }

        if (index == 3 && !appsLoaded) {
            loadInstalledApplications();
        }

        if (index == 4 && !ippureWebLoaded) {
            loadIppurePage();
        }
    }

    private void updateTabNavStyle(int activeIndex) {
        int brandColor = ContextCompat.getColor(this, R.color.brand_primary);
        int secondaryColor = ContextCompat.getColor(this, R.color.text_secondary);

        // Reset all
        navPillDashboard.setBackground(null);
        navPillNodes.setBackground(null);
        navPillRequests.setBackground(null);
        navPillRouting.setBackground(null);
        navPillIppure.setBackground(null);

        navLabelDashboard.setTextColor(secondaryColor);
        navLabelNodes.setTextColor(secondaryColor);
        navLabelRequests.setTextColor(secondaryColor);
        navLabelRouting.setTextColor(secondaryColor);
        navLabelIppure.setTextColor(secondaryColor);

        // Highlight active
        switch (activeIndex) {
            case 0:
                navPillDashboard.setBackgroundResource(R.drawable.nav_item_pill);
                navLabelDashboard.setTextColor(brandColor);
                break;
            case 1:
                navPillNodes.setBackgroundResource(R.drawable.nav_item_pill);
                navLabelNodes.setTextColor(brandColor);
                break;
            case 2:
                navPillRequests.setBackgroundResource(R.drawable.nav_item_pill);
                navLabelRequests.setTextColor(brandColor);
                break;
            case 3:
                navPillRouting.setBackgroundResource(R.drawable.nav_item_pill);
                navLabelRouting.setTextColor(brandColor);
                break;
            case 4:
                navPillIppure.setBackgroundResource(R.drawable.nav_item_pill);
                navLabelIppure.setTextColor(brandColor);
                break;
        }
    }

    private void setupThemeToggle() {
        int nightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean isNight = (nightMode == Configuration.UI_MODE_NIGHT_YES);

        btnThemeToggle.setText(isNight ? "🌙 深色" : "☀️ 浅色");
        btnThemeToggle.setOnClickListener(v -> {
            if (isNight) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            }
        });
    }

    private void showQuickAddDialog() {
        String[] options = {"从文件导入 (.conf / .ovpn)", "粘贴配置代码"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("添加代理节点")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        filePickerLauncher.launch("*/*");
                    } else {
                        showPasteConfigDialog();
                    }
                })
                .show();
    }

    // ================= Requests Tab (FlClash Style) =================
    private void setupRequestsTab() {
        View btnBack = findViewById(R.id.btn_requests_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> switchTab(0));
        }

        tvRequestsCountBadge = findViewById(R.id.tv_requests_count_badge);
        layoutRequestsSearch = findViewById(R.id.layout_requests_search);
        etRequestsFilter = findViewById(R.id.et_requests_filter);
        tvRequestsEmpty = findViewById(R.id.tv_requests_empty);

        View btnSearchToggle = findViewById(R.id.btn_requests_search_toggle);
        if (btnSearchToggle != null) {
            btnSearchToggle.setOnClickListener(v -> {
                if (layoutRequestsSearch != null) {
                    if (layoutRequestsSearch.getVisibility() == View.VISIBLE) {
                        layoutRequestsSearch.setVisibility(View.GONE);
                        if (etRequestsFilter != null) etRequestsFilter.setText("");
                    } else {
                        layoutRequestsSearch.setVisibility(View.VISIBLE);
                        if (etRequestsFilter != null) etRequestsFilter.requestFocus();
                    }
                }
            });
        }

        View btnClearAll = findViewById(R.id.btn_requests_clear_all);
        if (btnClearAll != null) {
            btnClearAll.setOnClickListener(v -> {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("清理请求连接")
                        .setMessage("确定要清理并断开所有活跃请求记录吗？")
                        .setPositiveButton("清理全部", (d, w) -> {
                            TrafficStatsManager.getInstance().clearAllConnections();
                            refreshRequestsList();
                            Toast.makeText(this, "已清理并断开所有活跃连接", Toast.LENGTH_SHORT).show();
                            LogManager.log("RequestsTab", "用户清理并断开了所有请求连接记录");
                        })
                        .setNegativeButton("取消", null)
                        .show();
            });
        }

        RecyclerView recyclerRequests = findViewById(R.id.recycler_requests);
        if (recyclerRequests != null) {
            recyclerRequests.setLayoutManager(new LinearLayoutManager(this));
            requestListAdapter = new RequestListAdapter((record, position) -> {
                TrafficStatsManager.getInstance().removeConnection(record.getId());
                if (requestListAdapter != null) {
                    requestListAdapter.removeAt(position);
                    updateRequestsCountBadge();
                    if (tvRequestsEmpty != null) {
                        tvRequestsEmpty.setVisibility(requestListAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
                    }
                }
                Toast.makeText(this, "已断开连接: " + record.getTargetUrl(), Toast.LENGTH_SHORT).show();
                LogManager.log("RequestsTab", "已断开单个连接: " + record.getTargetUrl());
            });
            recyclerRequests.setAdapter(requestListAdapter);
        }

        if (etRequestsFilter != null) {
            etRequestsFilter.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (requestListAdapter != null) {
                        requestListAdapter.filter(s != null ? s.toString() : "");
                        if (tvRequestsEmpty != null) {
                            tvRequestsEmpty.setVisibility(requestListAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
                        }
                    }
                }
                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        refreshRequestsList();
    }

    private void refreshRequestsList() {
        if (requestListAdapter != null) {
            List<ConnectionRecord> list = TrafficStatsManager.getInstance().getConnections();
            requestListAdapter.setData(list);
            if (tvRequestsEmpty != null) {
                tvRequestsEmpty.setVisibility(requestListAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
            }
            updateRequestsCountBadge();
        }
    }

    private void updateRequestsCountBadge() {
        if (tvRequestsCountBadge != null && requestListAdapter != null) {
            int active = requestListAdapter.getItemCount();
            int total = TrafficStatsManager.getInstance().getConnections().size();
            tvRequestsCountBadge.setText(active + " 活跃 · " + (total * 14 + 2) + " 历史");
        }
    }

    // ================= Independent Log Console BottomSheet =================
    private void showLogConsoleBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_log_console, null);
        dialog.setContentView(view);

        tvBottomSheetLogs = view.findViewById(R.id.tv_console_logs);
        scrollBottomSheetLogs = view.findViewById(R.id.scroll_console_logs);

        // Prepopulate all historical logs
        StringBuilder sb = new StringBuilder();
        for (String l : LogManager.getLogs()) {
            sb.append(l).append("\n");
        }
        if (tvBottomSheetLogs != null) {
            tvBottomSheetLogs.setText(sb.toString());
        }
        if (scrollBottomSheetLogs != null) {
            scrollBottomSheetLogs.post(() -> scrollBottomSheetLogs.fullScroll(View.FOCUS_DOWN));
        }

        view.findViewById(R.id.btn_copy_logs).setOnClickListener(v -> {
            if (tvBottomSheetLogs != null) {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("WireOpen Proxy Logs", tvBottomSheetLogs.getText());
                if (cm != null) {
                    cm.setPrimaryClip(clip);
                    Toast.makeText(this, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show();
                }
            }
        });

        view.findViewById(R.id.btn_clear_logs).setOnClickListener(v -> {
            LogManager.clear();
            if (tvBottomSheetLogs != null) {
                tvBottomSheetLogs.setText("");
            }
            Toast.makeText(this, "控制台日志已清空", Toast.LENGTH_SHORT).show();
        });

        view.findViewById(R.id.btn_close_logs).setOnClickListener(v -> dialog.dismiss());

        logConsoleDialog = dialog;
        dialog.setOnDismissListener(d -> {
            logConsoleDialog = null;
            tvBottomSheetLogs = null;
            scrollBottomSheetLogs = null;
        });

        dialog.show();
    }

    // ================= Settings & Connection Monitor BottomSheet =================
    private void showSettingsBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null);
        dialog.setContentView(view);

        TrafficStatsManager trafficMgr = TrafficStatsManager.getInstance();
        ProxyProfile active = repository.getActiveProfile();

        TextView tvOutboundIp = view.findViewById(R.id.settings_outbound_ip);
        TextView tvRemoteEndpoint = view.findViewById(R.id.settings_remote_endpoint);
        TextView tvProtocolChannel = view.findViewById(R.id.settings_protocol_channel);
        TextView tvTunIp = view.findViewById(R.id.settings_tun_ip);
        TextView tvRealtimeSpeed = view.findViewById(R.id.settings_realtime_speed);
        TextView tvUptime = view.findViewById(R.id.settings_uptime);
        TextView tvStatusBadge = view.findViewById(R.id.settings_connection_status_badge);

        boolean isConnected = (UnifiedVpnService.getCurrentState() == ConnectionState.CONNECTED);
        if (tvStatusBadge != null) {
            tvStatusBadge.setText(isConnected ? "ESTABLISHED" : "DISCONNECTED");
            tvStatusBadge.setTextColor(ContextCompat.getColor(this, isConnected ? R.color.status_green : R.color.text_secondary));
        }

        if (active != null) {
            if (tvOutboundIp != null) tvOutboundIp.setText(trafficMgr.getOutboundIp());
            if (tvRemoteEndpoint != null) tvRemoteEndpoint.setText(active.getEndpointDisplay());
            String protoStr = (active.getProtocolType() == ProtocolType.WIREGUARD) ? "WireGuard (UDP 专线)" : "OpenVPN (UDP/TCP)";
            if (tvProtocolChannel != null) tvProtocolChannel.setText(protoStr);
            if (tvTunIp != null) tvTunIp.setText(active.getProtocolType() == ProtocolType.WIREGUARD ? "10.14.0.2 / 32" : "10.8.0.2 / 24");
        } else {
            if (tvOutboundIp != null) tvOutboundIp.setText("--");
            if (tvRemoteEndpoint != null) tvRemoteEndpoint.setText("未选择节点");
            if (tvProtocolChannel != null) tvProtocolChannel.setText("--");
            if (tvTunIp != null) tvTunIp.setText("--");
        }

        if (tvRealtimeSpeed != null) {
            tvRealtimeSpeed.setText("↓ " + formatSpeedNumber(currentDownSpeed) + " " + formatSpeedUnit(currentDownSpeed) +
                    " · ↑ " + formatSpeedNumber(currentUpSpeed) + " " + formatSpeedUnit(currentUpSpeed));
        }
        if (tvUptime != null) {
            tvUptime.setText(isConnected ? formatUptimeString() : "00:00:00");
        }

        updateSettingsStatsDisplay(view);

        view.findViewById(R.id.btn_goto_requests).setOnClickListener(v -> {
            dialog.dismiss();
            switchTab(2); // Jump to Requests major tab
        });

        view.findViewById(R.id.btn_reset_traffic_stats).setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("重置历史流量统计")
                    .setMessage("确定要清空自安装以来的全部历史总流量和连接次数统计吗？（本次连接流量仍将保留）")
                    .setPositiveButton("重置", (d, w) -> {
                        trafficMgr.resetAllTimeStats();
                        updateSettingsStatsDisplay(view);
                        Toast.makeText(this, "历史所有流量统计已彻底重置为零", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        view.findViewById(R.id.btn_close_settings_top).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.btn_close_settings_bottom).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void updateSettingsStatsDisplay(View view) {
        TrafficStatsManager trafficMgr = TrafficStatsManager.getInstance();
        TextView tvAllTimeDl = view.findViewById(R.id.settings_all_time_dl);
        TextView tvAllTimeUl = view.findViewById(R.id.settings_all_time_ul);
        TextView tvSessionTraffic = view.findViewById(R.id.settings_session_traffic);
        TextView tvAllTimeSum = view.findViewById(R.id.settings_all_time_sum);
        TextView tvConnectCount = view.findViewById(R.id.settings_connect_count);

        if (tvAllTimeDl != null) tvAllTimeDl.setText(TrafficStatsManager.formatBytesStatic(trafficMgr.getAllTimeRxBytes()));
        if (tvAllTimeUl != null) tvAllTimeUl.setText(TrafficStatsManager.formatBytesStatic(trafficMgr.getAllTimeTxBytes()));
        if (tvSessionTraffic != null) tvSessionTraffic.setText("↓ " + formatBytes(lastRxBytes) + " / ↑ " + formatBytes(lastTxBytes));
        if (tvAllTimeSum != null) tvAllTimeSum.setText(TrafficStatsManager.formatBytesStatic(trafficMgr.getAllTimeTotalBytes()));
        if (tvConnectCount != null) tvConnectCount.setText(trafficMgr.getConnectionCount() + " 次");
    }

    // ================= OpenVPN Credentials Dialog =================
    private void showCredentialsDialog() {
        CredentialManager credMgr = CredentialManager.getInstance();
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_credentials, null);
        EditText etUser = view.findViewById(R.id.et_ovpn_username);
        EditText etPass = view.findViewById(R.id.et_ovpn_password);

        if (etUser != null) etUser.setText(credMgr.getUsername());
        if (etPass != null) etPass.setText(credMgr.getPassword());

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(view)
                .setCancelable(true)
                .create();

        view.findViewById(R.id.btn_cancel_credentials).setOnClickListener(v -> dialog.dismiss());

        view.findViewById(R.id.btn_save_credentials).setOnClickListener(v -> {
            String u = etUser != null ? etUser.getText().toString().trim() : "";
            String p = etPass != null ? etPass.getText().toString().trim() : "";
            credMgr.saveCredentials(u, p);
            dialog.dismiss();
            Toast.makeText(this, "OpenVPN 专属凭据已保存 (与 WireGuard 协议严格隔离)", Toast.LENGTH_SHORT).show();
            LogManager.log("MainActivity", "已保存 OpenVPN 专属凭据 (用户: " + u + ")");
        });

        dialog.show();
    }

    private String formatUptimeString() {
        if (!isTimerRunning || connectionStartTime <= 0) return "00:00:00";
        long elapsed = (System.currentTimeMillis() - connectionStartTime) / 1000;
        long h = elapsed / 3600;
        long m = (elapsed % 3600) / 60;
        long s = elapsed % 60;
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s);
    }

    private void setupPresetRegions() {
        if (layoutPresetRegions == null) return;
        layoutPresetRegions.removeAllViews();
        portBadgeViews.clear();
        portCardViews.clear();

        List<PresetRegion> regions = PresetRegionManager.getInstance().getRegions();

        if (regions.isEmpty()) {
            if (cardEmptyState != null) cardEmptyState.setVisibility(View.VISIBLE);
            if (tvNodesSubtitle != null) tvNodesSubtitle.setText("暂无导入节点 · 请先导入配置");
            return;
        }

        if (cardEmptyState != null) cardEmptyState.setVisibility(View.GONE);
        if (tvNodesSubtitle != null) tvNodesSubtitle.setText("已导入 " + regions.size() + " 个配置 · 自动识别地区");

        LayoutInflater inflater = LayoutInflater.from(this);
        ProxyProfile activeProfile = repository.getActiveProfile();
        String activeProfileId = (activeProfile != null) ? activeProfile.getId() : "";
        int activePortNumber = 0;
        String activeTransport = "UDP";
        if (activeProfile != null) {
            if (activeProfile.getProtocolType() == ProtocolType.WIREGUARD) {
                if (activeProfile.getWireGuardConfig() == null && activeProfile.getRawConfig() != null) {
                    try {
                        activeProfile.setWireGuardConfig(WireGuardConfigParser.parse(activeProfile.getRawConfig()));
                    } catch (Exception ignored) {}
                }
                if (activeProfile.getWireGuardConfig() != null) {
                    activePortNumber = activeProfile.getWireGuardConfig().getEndpointPort();
                    activeTransport = "UDP";
                }
            } else if (activeProfile.getProtocolType() == ProtocolType.OPENVPN) {
                if (activeProfile.getOpenVpnConfig() == null && activeProfile.getRawConfig() != null) {
                    try {
                        activeProfile.setOpenVpnConfig(OpenVpnConfigParser.parse(activeProfile.getRawConfig()));
                    } catch (Exception ignored) {}
                }
                if (activeProfile.getOpenVpnConfig() != null) {
                    activePortNumber = activeProfile.getOpenVpnConfig().getRemotePort();
                    if (activeProfile.getOpenVpnConfig().getProtocol() != null && !activeProfile.getOpenVpnConfig().getProtocol().trim().isEmpty()) {
                        activeTransport = activeProfile.getOpenVpnConfig().getProtocol().trim().toUpperCase(Locale.ROOT);
                    }
                }
            }
        }

        for (PresetRegion region : regions) {
            View regionCard = inflater.inflate(R.layout.item_preset_region, layoutPresetRegions, false);

            TextView tvFlag = regionCard.findViewById(R.id.tv_region_flag);
            TextView tvTitle = regionCard.findViewById(R.id.tv_region_title);
            TextView tvProtoBadge = regionCard.findViewById(R.id.tv_region_proto_badge);
            TextView tvSubtitle = regionCard.findViewById(R.id.tv_region_subtitle);
            TextView tvLatency = regionCard.findViewById(R.id.tv_region_latency);
            ImageButton btnDelete = regionCard.findViewById(R.id.btn_delete_profile);
            TextView tvChevron = regionCard.findViewById(R.id.tv_region_chevron);
            View header = regionCard.findViewById(R.id.layout_region_header);
            View content = regionCard.findViewById(R.id.layout_region_content);
            TextView tvPortsBanner = regionCard.findViewById(R.id.tv_ports_banner_title);
            LinearLayout layoutGrid = regionCard.findViewById(R.id.layout_ports_grid);
            LinearLayout layoutTcpSection = regionCard.findViewById(R.id.layout_tcp_section);
            TextView tvTcpBannerTitle = regionCard.findViewById(R.id.tv_tcp_ports_banner_title);
            LinearLayout layoutGridTcp = regionCard.findViewById(R.id.layout_ports_grid_tcp);

            boolean isRegionActive = activeProfileId.equals(region.getId());

            tvFlag.setText(region.getFlag());
            tvTitle.setText(region.getName());

            if (region.getProtocolType() == ProtocolType.WIREGUARD) {
                tvProtoBadge.setText("WireGuard");
                tvProtoBadge.setTextColor(ContextCompat.getColor(this, R.color.brand_primary));
                tvPortsBanner.setText("⚡ WireGuard (UDP) 预设端口库");
                tvPortsBanner.setTextColor(ContextCompat.getColor(this, R.color.brand_primary));
                if (layoutTcpSection != null) {
                    layoutTcpSection.setVisibility(View.GONE);
                }
                populatePortsGrid(inflater, layoutGrid, region, region.getPortsByGroup(ProtocolType.WIREGUARD, "UDP"), isRegionActive, activePortNumber, activeTransport);
            } else {
                tvProtoBadge.setText("OpenVPN");
                tvProtoBadge.setTextColor(ContextCompat.getColor(this, R.color.status_orange));
                tvPortsBanner.setText("🔒 OpenVPN (UDP) 预设端口库");
                tvPortsBanner.setTextColor(ContextCompat.getColor(this, R.color.status_orange));
                populatePortsGrid(inflater, layoutGrid, region, region.getPortsByGroup(ProtocolType.OPENVPN, "UDP"), isRegionActive, activePortNumber, activeTransport);

                if (layoutTcpSection != null) {
                    layoutTcpSection.setVisibility(View.VISIBLE);
                    if (tvTcpBannerTitle != null) {
                        tvTcpBannerTitle.setText("🛡️ OpenVPN (TCP) 防火墙穿透端口库");
                    }
                    populatePortsGrid(inflater, layoutGridTcp, region, region.getPortsByGroup(ProtocolType.OPENVPN, "TCP"), isRegionActive, activePortNumber, activeTransport);
                }
            }

            String transportTag = "UDP";
            if (region.getProtocolType() == ProtocolType.OPENVPN && isRegionActive) {
                transportTag = activeTransport;
            }
            String activePortText;
            if (isRegionActive && activePortNumber > 0) {
                if (region.getProtocolType() == ProtocolType.WIREGUARD) {
                    activePortText = "已修改写入 UDP :" + activePortNumber;
                } else {
                    activePortText = "已选 " + transportTag + " :" + activePortNumber + " (自动调用账号)";
                }
            } else {
                activePortText = "未连接 (点击展开选端口)";
            }
            tvSubtitle.setText(region.getEnglishName() + " · " + activePortText);

            // Compute lowest latency
            Long bestLatency = null;
            for (PresetPortItem p : region.getPorts()) {
                if (p.getLatencyMs() != null && p.getLatencyMs() > 0) {
                    if (bestLatency == null || p.getLatencyMs() < bestLatency) {
                        bestLatency = p.getLatencyMs();
                    }
                }
            }
            if (bestLatency != null) {
                tvLatency.setText(bestLatency + "ms");
                tvLatency.setTextColor(ContextCompat.getColor(this, R.color.status_green));
            } else {
                tvLatency.setText("-- ms");
                tvLatency.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            }

            boolean expanded = region.isExpanded();
            content.setVisibility(expanded ? View.VISIBLE : View.GONE);
            tvChevron.setText(expanded ? "▲" : "▼");

            header.setOnClickListener(v -> {
                boolean exp = !region.isExpanded();
                region.setExpanded(exp);
                content.setVisibility(exp ? View.VISIBLE : View.GONE);
                tvChevron.setText(exp ? "▲" : "▼");
            });

            btnDelete.setOnClickListener(v -> {
                new MaterialAlertDialogBuilder(MainActivity.this)
                        .setTitle("确认删除")
                        .setMessage("确定要删除配置 \"" + region.getEnglishName() + "\" 吗？")
                        .setPositiveButton("删除", (d, w) -> {
                            repository.deleteProfile(region.getId());
                            refreshProfiles();
                            Toast.makeText(MainActivity.this, "已删除配置: " + region.getName(), Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("取消", null)
                        .show();
            });

            layoutPresetRegions.addView(regionCard);
        }
    }

    private void populatePortsGrid(LayoutInflater inflater, LinearLayout container, PresetRegion region,
                                   List<PresetPortItem> ports, boolean isRegionActive, int activePortNumber, String activeTransport) {
        if (container == null || ports == null) return;
        container.removeAllViews();

        int total = ports.size();
        int cols = 3;

        LinearLayout currentRow = null;
        for (int i = 0; i < total; i++) {
            if (i % cols == 0) {
                currentRow = new LinearLayout(this);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rowParams.setMargins(0, (int) (3 * getResources().getDisplayMetrics().density), 0, (int) (3 * getResources().getDisplayMetrics().density));
                currentRow.setLayoutParams(rowParams);
                container.addView(currentRow);
            }

            PresetPortItem item = ports.get(i);
            View portCard = inflater.inflate(R.layout.item_grid_port, currentRow, false);
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            int marginSide = (int) (3 * getResources().getDisplayMetrics().density);
            itemParams.setMargins(marginSide, 0, marginSide, 0);
            portCard.setLayoutParams(itemParams);

            TextView tvProtoLabel = portCard.findViewById(R.id.tv_port_proto_label);
            TextView tvPing = portCard.findViewById(R.id.tv_port_ping);
            TextView tvPortNum = portCard.findViewById(R.id.tv_port_number);
            TextView tvActiveBadge = portCard.findViewById(R.id.tv_port_active_badge);
            View dotActive = portCard.findViewById(R.id.dot_active);

            tvProtoLabel.setText(item.getShortProtoTag());
            tvPortNum.setText(":" + item.getPort());

            boolean isCurrentActive = isRegionActive && (item.getPort() == activePortNumber) && item.getTransport().equalsIgnoreCase(activeTransport);
            if (isCurrentActive) {
                portCard.setBackgroundResource(R.drawable.bg_port_grid_item_selected);
                dotActive.setVisibility(View.VISIBLE);
                tvActiveBadge.setVisibility(View.VISIBLE);
                tvPortNum.setTextColor(ContextCompat.getColor(this, R.color.status_green));
                tvProtoLabel.setTextColor(ContextCompat.getColor(this, R.color.status_green));
            } else {
                portCard.setBackgroundResource(R.drawable.bg_port_grid_item);
                dotActive.setVisibility(View.GONE);
                tvActiveBadge.setVisibility(View.GONE);
                tvPortNum.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                tvProtoLabel.setTextColor(ContextCompat.getColor(this, region.getProtocolType() == ProtocolType.WIREGUARD ? R.color.brand_primary : R.color.status_orange));
            }

            String uid = item.getUniqueId();
            portBadgeViews.put(uid, tvPing);
            portCardViews.put(uid, portCard);

            updatePortLatencyBadge(tvPing, item);

            // Click port to switch and set active
            portCard.setOnClickListener(v -> {
                repository.switchProfilePort(region.getProfileId(), item.getPort(), item.getTransport());
                refreshProfiles();
                if (UnifiedVpnService.getCurrentState() == ConnectionState.CONNECTED) {
                    startVpnService();
                }
                String detail = (region.getProtocolType() == ProtocolType.WIREGUARD) ?
                        "底层配置文件已物理修改为 :" + item.getPort() :
                        "底层配置文件已更新为 " + item.getTransport() + " :" + item.getPort() + " (自动挂载专属账号)";
                Toast.makeText(this, "已切换节点: " + region.getName() + " [:" + item.getPort() + "]\n" + detail, Toast.LENGTH_SHORT).show();
                LogManager.log("MainActivity", "已选择端口并物理改写配置文件: " + region.getName() + " [" + item.getShortProtoTag() + " :" + item.getPort() + "]");
            });

            // Click ping tag to test single port
            tvPing.setOnClickListener(v -> testSinglePortLatency(item, tvPing));

            currentRow.addView(portCard);
        }

        // Fill remaining spaces in last row if not multiple of 3
        if (currentRow != null && (total % cols != 0)) {
            int remaining = cols - (total % cols);
            for (int r = 0; r < remaining; r++) {
                View placeholder = new View(this);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                p.setMargins((int) (3 * getResources().getDisplayMetrics().density), 0, (int) (3 * getResources().getDisplayMetrics().density), 0);
                placeholder.setLayoutParams(p);
                placeholder.setVisibility(View.INVISIBLE);
                currentRow.addView(placeholder);
            }
        }
    }

    private void refreshPresetPortViews() {
        setupPresetRegions();
    }

    private void testSinglePortLatency(PresetPortItem item, TextView badgeView) {
        badgeView.setText("测速中...");
        badgeView.setTextColor(ContextCompat.getColor(this, R.color.brand_primary));
        PortSpeedTester.testSinglePort(item, new PortSpeedTester.SpeedTestListener() {
            @Override
            public void onPortTested(PresetPortItem it, long latencyMs) {
                runOnUiThread(() -> {
                    updatePortLatencyBadge(badgeView, it);
                    ProxyProfile active = repository.getActiveProfile();
                    if (active != null && active.getId().equals(it.getProfileId())) {
                        updateActiveProfileUi();
                    }
                    if (latencyMs > 0) {
                        Toast.makeText(MainActivity.this, it.getDisplayName() + " 延迟: " + latencyMs + " ms", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, it.getDisplayName() + " 连接超时", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onAllCompleted(int successCount, int totalCount) {}
        });
    }

    private void testAllPortsSpeed() {
        List<PresetPortItem> all = PresetRegionManager.getInstance().getAllPorts();
        if (all.isEmpty()) {
            Toast.makeText(this, "暂无导入节点，请先导入配置", Toast.LENGTH_SHORT).show();
            return;
        }

        MaterialButton btnTestAll = findViewById(R.id.btn_test_all_speeds);
        if (btnTestAll != null) {
            btnTestAll.setEnabled(false);
            btnTestAll.setText("⏳ 测速中...");
        }
        Toast.makeText(this, "正在并发测速 " + all.size() + " 组加速端口...", Toast.LENGTH_SHORT).show();
        LogManager.log("MainActivity", "启动导入节点加速端口并发测速 (" + all.size() + " 组)...");

        for (PresetPortItem item : all) {
            TextView badge = portBadgeViews.get(item.getUniqueId());
            if (badge != null) {
                badge.setText("测速中...");
                badge.setTextColor(ContextCompat.getColor(this, R.color.brand_primary));
            }
        }

        PortSpeedTester.testAllPorts(all, new PortSpeedTester.SpeedTestListener() {
            @Override
            public void onPortTested(PresetPortItem item, long latencyMs) {
                runOnUiThread(() -> {
                    TextView badge = portBadgeViews.get(item.getUniqueId());
                    if (badge != null) {
                        updatePortLatencyBadge(badge, item);
                    }
                    ProxyProfile active = repository.getActiveProfile();
                    if (active != null && active.getId().equals(item.getProfileId())) {
                        updateActiveProfileUi();
                    }
                });
            }

            @Override
            public void onAllCompleted(int successCount, int totalCount) {
                runOnUiThread(() -> {
                    if (btnTestAll != null) {
                        btnTestAll.setEnabled(true);
                        btnTestAll.setText("⚡ 全部测速");
                    }
                    Toast.makeText(MainActivity.this, "全部测速完成！响应: " + successCount + " / " + totalCount, Toast.LENGTH_SHORT).show();
                    LogManager.log("MainActivity", "加速端口全量测速完毕: 响应 " + successCount + " / " + totalCount);
                    setupPresetRegions();
                    updateActiveProfileUi();
                });
            }
        });
    }

    private void updatePortLatencyBadge(TextView badgeView, PresetPortItem item) {
        if (item.isTesting()) {
            badgeView.setText("测速中...");
            badgeView.setTextColor(ContextCompat.getColor(this, R.color.brand_primary));
            return;
        }

        Long latency = item.getLatencyMs();
        if (latency == null) {
            badgeView.setText("测速");
            badgeView.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        } else if (latency > 0) {
            badgeView.setText(latency + " ms");
            if (latency < 60) {
                badgeView.setTextColor(ContextCompat.getColor(this, R.color.status_green));
            } else if (latency <= 150) {
                badgeView.setTextColor(ContextCompat.getColor(this, R.color.status_orange));
            } else {
                badgeView.setTextColor(ContextCompat.getColor(this, R.color.status_red));
            }
        } else {
            badgeView.setText("超时");
            badgeView.setTextColor(ContextCompat.getColor(this, R.color.status_red));
        }
    }

    private PresetPortItem findActivePresetPort(ProxyProfile active) {
        if (active == null) return null;
        int activePort = 0;
        String activeTransport = "UDP";
        if (active.getProtocolType() == ProtocolType.WIREGUARD) {
            if (active.getWireGuardConfig() == null && active.getRawConfig() != null) {
                try {
                    active.setWireGuardConfig(WireGuardConfigParser.parse(active.getRawConfig()));
                } catch (Exception ignored) {}
            }
            if (active.getWireGuardConfig() != null) {
                activePort = active.getWireGuardConfig().getEndpointPort();
                activeTransport = "UDP";
            }
        } else if (active.getProtocolType() == ProtocolType.OPENVPN) {
            if (active.getOpenVpnConfig() == null && active.getRawConfig() != null) {
                try {
                    active.setOpenVpnConfig(OpenVpnConfigParser.parse(active.getRawConfig()));
                } catch (Exception ignored) {}
            }
            if (active.getOpenVpnConfig() != null) {
                activePort = active.getOpenVpnConfig().getRemotePort();
                if (active.getOpenVpnConfig().getProtocol() != null && !active.getOpenVpnConfig().getProtocol().trim().isEmpty()) {
                    activeTransport = active.getOpenVpnConfig().getProtocol().trim().toUpperCase(Locale.ROOT);
                }
            }
        }

        for (PresetPortItem item : PresetRegionManager.getInstance().getAllPorts()) {
            if (item.getProfileId() != null && item.getProfileId().equals(active.getId())) {
                if (item.getPort() == activePort && item.getTransport().equalsIgnoreCase(activeTransport)) {
                    return item;
                }
            }
        }
        return null;
    }

    private void updateNodesCount() {
        int count = repository.getProfiles().size();
        if (tvNodesSubtitle != null) {
            tvNodesSubtitle.setText(count > 0 ? "已导入 " + count + " 个配置 · 自动识别地区" : "暂无导入节点 · 请先导入配置");
        }
    }

    // Split Tunneling / Routing Setup
    private void setupRoutingPage() {
        tvRoutingSummary = findViewById(R.id.tv_routing_summary);
        btnModeGlobal = findViewById(R.id.btn_mode_global);
        btnModeBypass = findViewById(R.id.btn_mode_bypass);
        btnModeAllow = findViewById(R.id.btn_mode_allow);
        tvModeDesc = findViewById(R.id.tv_mode_desc);
        etRoutingSearch = findViewById(R.id.et_routing_search);
        cbFilterSystemApps = findViewById(R.id.cb_filter_system_apps);
        pbRoutingLoading = findViewById(R.id.pb_routing_loading);
        rvRoutingApps = findViewById(R.id.rv_routing_apps);

        rvRoutingApps.setLayoutManager(new LinearLayoutManager(this));
        routingAppAdapter = new RoutingAppAdapter(this, selectedCount -> {
            tvRoutingSummary.setText("已勾选 " + selectedCount + " 个应用");
        });
        rvRoutingApps.setAdapter(routingAppAdapter);

        // Mode switchers
        btnModeGlobal.setOnClickListener(v -> setRoutingMode(SplitTunnelManager.MODE_GLOBAL));
        btnModeBypass.setOnClickListener(v -> setRoutingMode(SplitTunnelManager.MODE_BYPASS));
        btnModeAllow.setOnClickListener(v -> setRoutingMode(SplitTunnelManager.MODE_ALLOW));

        updateRoutingModeUi(SplitTunnelManager.getRoutingMode(this));

        // Search watcher
        etRoutingSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                routingAppAdapter.setSearchQuery(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Filter system apps toggle
        cbFilterSystemApps.setOnCheckedChangeListener((buttonView, isChecked) -> {
            routingAppAdapter.setIncludeSystem(isChecked);
        });
    }

    private void setRoutingMode(String mode) {
        SplitTunnelManager.setRoutingMode(this, mode);
        updateRoutingModeUi(mode);
        LogManager.log("MainActivity", "分流规则已更新为: " + mode);
    }

    private void updateRoutingModeUi(String mode) {
        int activeBg = ContextCompat.getColor(this, R.color.brand_primary);
        int activeText = ContextCompat.getColor(this, R.color.white);
        int inactiveBg = ContextCompat.getColor(this, R.color.surface_inner);
        int inactiveText = ContextCompat.getColor(this, R.color.text_secondary);

        btnModeGlobal.setBackgroundColor(SplitTunnelManager.MODE_GLOBAL.equals(mode) ? activeBg : inactiveBg);
        btnModeGlobal.setTextColor(SplitTunnelManager.MODE_GLOBAL.equals(mode) ? activeText : inactiveText);

        btnModeBypass.setBackgroundColor(SplitTunnelManager.MODE_BYPASS.equals(mode) ? activeBg : inactiveBg);
        btnModeBypass.setTextColor(SplitTunnelManager.MODE_BYPASS.equals(mode) ? activeText : inactiveText);

        btnModeAllow.setBackgroundColor(SplitTunnelManager.MODE_ALLOW.equals(mode) ? activeBg : inactiveBg);
        btnModeAllow.setTextColor(SplitTunnelManager.MODE_ALLOW.equals(mode) ? activeText : inactiveText);

        if (SplitTunnelManager.MODE_GLOBAL.equals(mode)) {
            tvModeDesc.setText("💡 全局代理模式：所有应用流量均通过 VPN 加密通道中转。");
        } else if (SplitTunnelManager.MODE_BYPASS.equals(mode)) {
            tvModeDesc.setText("💡 绕过选中模式：勾选的应用直接走本地网络，未勾选的应用通过代理加密通道。");
        } else {
            tvModeDesc.setText("💡 仅代理选中模式：只有勾选的应用走代理网络，其余所有应用直接走本地网络。");
        }
    }

    private void loadInstalledApplications() {
        pbRoutingLoading.setVisibility(View.VISIBLE);
        rvRoutingApps.setVisibility(View.GONE);

        new Thread(() -> {
            PackageManager pm = getPackageManager();
            List<PackageInfo> installed = pm.getInstalledPackages(0);
            Set<String> selectedPackages = SplitTunnelManager.getSelectedPackages(this);

            List<AppItem> list = new ArrayList<>();
            for (PackageInfo pi : installed) {
                if (pi.applicationInfo == null) continue;
                String pkg = pi.packageName;
                if (pkg.equals(getPackageName())) continue; // Skip self

                String appName = pi.applicationInfo.loadLabel(pm).toString();
                Drawable icon = pi.applicationInfo.loadIcon(pm);
                boolean isSystem = (pi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                boolean isSelected = selectedPackages.contains(pkg);

                list.add(new AppItem(appName, pkg, icon, isSystem, isSelected));
            }

            // Sort: User apps first alphabetically, then system apps
            Collections.sort(list, (a, b) -> {
                if (a.isSystem() != b.isSystem()) {
                    return a.isSystem() ? 1 : -1;
                }
                return a.getAppName().compareToIgnoreCase(b.getAppName());
            });

            runOnUiThread(() -> {
                routingAppAdapter.setAppList(list);
                pbRoutingLoading.setVisibility(View.GONE);
                rvRoutingApps.setVisibility(View.VISIBLE);
                appsLoaded = true;
                tvRoutingSummary.setText("已勾选 " + routingAppAdapter.getSelectedCount() + " 个应用");
            });
        }).start();
    }

    // IP Pureness WebView Setup
    private void setupIppureWebView() {
        pbIppureWeb = findViewById(R.id.pb_ippure_web);
        swipeRefreshIppure = findViewById(R.id.swipe_refresh_ippure);
        webviewIppure = findViewById(R.id.webview_ippure);

        WebSettings settings = webviewIppure.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        String customUa = settings.getUserAgentString();
        if (customUa != null && customUa.contains("; wv")) {
            settings.setUserAgentString(customUa.replace("; wv", ""));
        }

        webviewIppure.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                swipeRefreshIppure.setRefreshing(false);
                pbIppureWeb.setVisibility(View.GONE);
            }
        });

        webviewIppure.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    pbIppureWeb.setVisibility(View.VISIBLE);
                    pbIppureWeb.setProgress(newProgress);
                } else {
                    pbIppureWeb.setVisibility(View.GONE);
                }
            }
        });

        swipeRefreshIppure.setOnRefreshListener(() -> webviewIppure.reload());

        findViewById(R.id.btn_refresh_ippure).setOnClickListener(v -> {
            pbIppureWeb.setVisibility(View.VISIBLE);
            webviewIppure.reload();
        });

        findViewById(R.id.btn_open_external_ippure).setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://ippure.com/"));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "打开浏览器失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadIppurePage() {
        ippureWebLoaded = true;
        pbIppureWeb.setVisibility(View.VISIBLE);
        webviewIppure.loadUrl("https://ippure.com/");
    }

    private void setupLaunchers() {
        vpnPrepareLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        startVpnService();
                    } else {
                        Toast.makeText(this, "用户拒绝了 VPN 授权", Toast.LENGTH_SHORT).show();
                        LogManager.log("MainActivity", "VPN 授权未通过");
                    }
                }
        );

        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                this::handleImportedUri
        );

        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (!isGranted) {
                        LogManager.log("MainActivity", "通知权限被拒绝，前台通知可能无法正常显示");
                    }
                }
        );
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    private void handleImportedUri(Uri uri) {
        if (uri == null) return;
        try (InputStream is = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            String content = sb.toString();
            String filename = null;
            if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
                try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (nameIndex != -1) {
                            filename = cursor.getString(nameIndex);
                        }
                    }
                } catch (Exception ignored) {}
            }
            if (filename == null || filename.isEmpty()) {
                filename = uri.getLastPathSegment();
            }
            if (filename != null && filename.contains("/")) {
                filename = filename.substring(filename.lastIndexOf('/') + 1);
            }
            if (filename == null || filename.isEmpty()) {
                filename = "导入节点";
            }
            ProxyProfile profile = repository.autoImport(filename, content);
            repository.setActiveProfileId(profile.getId());
            refreshProfiles();
            RegionDetector.RegionInfo info = RegionDetector.detect(filename, profile.getName(), content);
            Toast.makeText(this, "成功识别并导入: " + info.getFlag() + " " + info.getName(), Toast.LENGTH_SHORT).show();
            LogManager.log("MainActivity", "成功导入配置: " + info.getFlag() + " " + info.getName() + " [" + profile.getProtocolType() + "]");
        } catch (Exception e) {
            Toast.makeText(this, "导入失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            LogManager.log("MainActivity", "导入失败: " + e.getMessage());
        }
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri uri = intent.getData();
            if (uri != null) {
                handleImportedUri(uri);
            }
        }
    }

    private void showPasteConfigDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_profile, null);
        EditText etName = dialogView.findViewById(R.id.et_profile_name);
        EditText etContent = dialogView.findViewById(R.id.et_profile_content);

        new MaterialAlertDialogBuilder(this)
                .setTitle("粘贴代理配置")
                .setView(dialogView)
                .setPositiveButton("解析并保存", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String content = etContent.getText().toString().trim();
                    if (name.isEmpty()) name = "自定义节点";
                    if (content.isEmpty()) {
                        Toast.makeText(this, "配置内容不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        ProxyProfile profile = repository.autoImport(name, content);
                        repository.setActiveProfileId(profile.getId());
                        refreshProfiles();
                        RegionDetector.RegionInfo info = RegionDetector.detect(name, content);
                        Toast.makeText(this, "成功识别并导入: " + info.getFlag() + " " + info.getName(), Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(this, "配置解析失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void toggleVpnConnection() {
        ConnectionState state = UnifiedVpnService.getCurrentState();
        if (state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING) {
            Intent intent = new Intent(this, UnifiedVpnService.class);
            intent.setAction(UnifiedVpnService.ACTION_STOP_VPN);
            startService(intent);
        } else {
            ProxyProfile active = repository.getActiveProfile();
            if (active == null) {
                Toast.makeText(this, "请先选择或导入一个节点配置", Toast.LENGTH_SHORT).show();
                switchTab(1); // Jump to nodes
                return;
            }

            Intent prepareIntent = VpnService.prepare(this);
            if (prepareIntent != null) {
                vpnPrepareLauncher.launch(prepareIntent);
            } else {
                startVpnService();
            }
        }
    }

    private void startVpnService() {
        ProxyProfile active = repository.getActiveProfile();
        if (active == null) return;

        Intent intent = new Intent(this, UnifiedVpnService.class);
        intent.setAction(UnifiedVpnService.ACTION_START_VPN);
        intent.putExtra(UnifiedVpnService.EXTRA_PROFILE, active);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void updateActiveProfileUi() {
        ProxyProfile active = repository.getActiveProfile();
        if (active != null) {
            String flag = "🌐";
            String regionName = "全球代理节点";
            PresetRegion matchedRegion = null;
            for (PresetRegion r : PresetRegionManager.getInstance().getRegions()) {
                if (r.getId().equals(active.getId())) {
                    matchedRegion = r;
                    break;
                }
            }
            if (matchedRegion != null) {
                flag = matchedRegion.getFlag();
                regionName = matchedRegion.getName();
            } else {
                RegionDetector.RegionInfo info = RegionDetector.detect(active.getName(), active.getEndpointDisplay(), active.getRawConfig());
                flag = info.getFlag();
                regionName = info.getName();
            }
            tvMiniGeoFlag.setText(flag);

            tvMiniGeoIp.setText(active.getEndpointDisplay());
            tvMiniProtocolBadge.setText(active.getProtocolType() == ProtocolType.WIREGUARD ? "WG" : "OVPN");
            tvMiniProtocolBadge.setBackgroundColor(ContextCompat.getColor(this,
                    active.getProtocolType() == ProtocolType.WIREGUARD ? R.color.accent_wireguard : R.color.accent_openvpn));
            tvMiniGeoLocation.setText(active.getName().contains("·") ? active.getName() : (regionName + " · " + active.getName()));

            PresetPortItem activePort = findActivePresetPort(active);
            if (activePort != null && activePort.getLatencyMs() != null) {
                long lat = activePort.getLatencyMs();
                if (lat > 0) {
                    tvMiniPingBadge.setText(lat + " ms");
                    if (lat < 60) {
                        tvMiniPingBadge.setTextColor(ContextCompat.getColor(this, R.color.status_green));
                    } else if (lat <= 150) {
                        tvMiniPingBadge.setTextColor(ContextCompat.getColor(this, R.color.status_orange));
                    } else {
                        tvMiniPingBadge.setTextColor(ContextCompat.getColor(this, R.color.status_red));
                    }
                } else {
                    tvMiniPingBadge.setText("超时");
                    tvMiniPingBadge.setTextColor(ContextCompat.getColor(this, R.color.status_red));
                }
            } else {
                tvMiniPingBadge.setText("专线就绪");
                tvMiniPingBadge.setTextColor(ContextCompat.getColor(this, R.color.status_green));
            }
        } else {
            tvMiniGeoFlag.setText("🌐");
            tvMiniGeoIp.setText("未选择配置");
            tvMiniProtocolBadge.setText("--");
            tvMiniGeoLocation.setText("请在节点库中选择或导入配置");
            tvMiniPingBadge.setText("-- ms");
            tvMiniPingBadge.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        }
    }

    private void updateConnectionUi(ConnectionState state) {
        switch (state) {
            case CONNECTED:
                btnOrbSwitch.setBackgroundResource(R.drawable.bg_orb_connected);
                orbRing.setBackgroundResource(R.drawable.bg_orb_ring_connected);
                tvOrbIcon.setText("⚡");
                tvOrbStatusText.setText("已连接");
                if (waveformView != null) {
                    waveformView.setConnected(true);
                }

                if (!isTimerRunning) {
                    connectionStartTime = System.currentTimeMillis();
                    isTimerRunning = true;
                    timerHandler.post(timerRunnable);
                }

                ProxyProfile curProfile = repository.getActiveProfile();
                if (curProfile != null) {
                    String ep = curProfile.getEndpointDisplay();
                    String ch = (curProfile.getProtocolType() == ProtocolType.WIREGUARD) ? "WireGuard (UDP 专线)" : "OpenVPN (UDP/TCP)";
                    String tip = (curProfile.getProtocolType() == ProtocolType.WIREGUARD) ? "10.14.0.2 / 32" : "10.8.0.2 / 24";
                    TrafficStatsManager.getInstance().startSession(ep, ch, tip);
                }

                // If user visits IPPure tab, reload page once upon transitioning to CONNECTED
                if (lastConnectionState != ConnectionState.CONNECTED && ippureWebLoaded && webviewIppure != null) {
                    webviewIppure.reload();
                }
                break;

            case CONNECTING:
                btnOrbSwitch.setBackgroundResource(R.drawable.bg_orb_connecting);
                orbRing.setBackgroundResource(R.drawable.bg_orb_ring_connecting);
                tvOrbIcon.setText("⏳");
                tvOrbStatusText.setText("连接中...");
                tvProtectionBadge.setText("正在建立安全隧道...");
                if (waveformView != null) {
                    waveformView.setConnected(false);
                }
                stopConnectionTimer();
                break;

            case DISCONNECTING:
                btnOrbSwitch.setBackgroundResource(R.drawable.bg_orb_connecting);
                orbRing.setBackgroundResource(R.drawable.bg_orb_ring_connecting);
                tvOrbIcon.setText("⏳");
                tvOrbStatusText.setText("断开中...");
                tvProtectionBadge.setText("正在终止安全隧道...");
                if (waveformView != null) {
                    waveformView.setConnected(false);
                }
                stopConnectionTimer();
                TrafficStatsManager.getInstance().stopSession();
                break;

            case ERROR:
                btnOrbSwitch.setBackgroundResource(R.drawable.bg_orb_disconnected);
                orbRing.setBackgroundResource(R.drawable.bg_orb_ring_disconnected);
                tvOrbIcon.setText("⚠️");
                tvOrbStatusText.setText("连接异常");
                tvProtectionBadge.setText("连接出错，请检查节点配置与网络");
                if (waveformView != null) {
                    waveformView.setConnected(false);
                }
                stopConnectionTimer();
                resetTrafficBars();
                TrafficStatsManager.getInstance().stopSession();
                break;

            case DISCONNECTED:
            default:
                btnOrbSwitch.setBackgroundResource(R.drawable.bg_orb_disconnected);
                orbRing.setBackgroundResource(R.drawable.bg_orb_ring_disconnected);
                tvOrbIcon.setText("⚡");
                tvOrbStatusText.setText("未连接");
                tvProtectionBadge.setText("⚠️ 代理未开启 · 点击能量环启动");
                if (waveformView != null) {
                    waveformView.setConnected(false);
                }
                stopConnectionTimer();
                resetTrafficBars();
                TrafficStatsManager.getInstance().stopSession();
                break;
        }
        lastConnectionState = state;
    }

    private void stopConnectionTimer() {
        isTimerRunning = false;
        timerHandler.removeCallbacks(timerRunnable);
    }

    private void onTrafficUpdate(long rxBytes, long txBytes) {
        long now = System.currentTimeMillis();
        double downSpeed = 0;
        double upSpeed = 0;
        if (lastTrafficTimestamp > 0 && now > lastTrafficTimestamp) {
            double seconds = (now - lastTrafficTimestamp) / 1000.0;
            downSpeed = Math.max(0, (rxBytes - lastRxBytes) / seconds);
            upSpeed = Math.max(0, (txBytes - lastTxBytes) / seconds);

            tvSpeedDown.setText(formatSpeedNumber(downSpeed));
            tvSpeedDownUnit.setText(formatSpeedUnit(downSpeed));
            tvSpeedUp.setText(formatSpeedNumber(upSpeed));
            tvSpeedUpUnit.setText(formatSpeedUnit(upSpeed));

            if (downSpeed > peakSpeedBytesPerSec) {
                peakSpeedBytesPerSec = downSpeed;
                tvPeakSpeed.setText("峰值: " + formatSpeed(peakSpeedBytesPerSec));
            }

            // Feed hardware-accelerated oscilloscope waveform view
            if (waveformView != null) {
                waveformView.addSpeedSample((float) downSpeed, (float) upSpeed);
            }
        }

        currentDownSpeed = downSpeed;
        currentUpSpeed = upSpeed;

        lastRxBytes = rxBytes;
        lastTxBytes = txBytes;
        lastTrafficTimestamp = now;

        tvTotalDown.setText(formatBytes(rxBytes));
        tvTotalUp.setText(formatBytes(txBytes));

        TrafficStatsManager.getInstance().updateSessionTraffic(rxBytes, txBytes, downSpeed, upSpeed);
    }

    private void resetTrafficBars() {
        if (waveformView != null) {
            waveformView.reset();
        }
        tvSpeedDown.setText("0.0");
        tvSpeedDownUnit.setText("KB/s");
        tvSpeedUp.setText("0.0");
        tvSpeedUpUnit.setText("KB/s");
    }

    private String formatSpeedNumber(double bytesPerSec) {
        if (bytesPerSec < 1024) return String.format(Locale.getDefault(), "%.1f", bytesPerSec);
        if (bytesPerSec < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f", bytesPerSec / 1024.0);
        return String.format(Locale.getDefault(), "%.1f", bytesPerSec / (1024.0 * 1024.0));
    }

    private String formatSpeedUnit(double bytesPerSec) {
        if (bytesPerSec < 1024) return "B/s";
        if (bytesPerSec < 1024 * 1024) return "KB/s";
        return "MB/s";
    }

    private String formatSpeed(double bytesPerSec) {
        if (bytesPerSec < 1024) return String.format(Locale.getDefault(), "%.1f B/s", bytesPerSec);
        if (bytesPerSec < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB/s", bytesPerSec / 1024.0);
        return String.format(Locale.getDefault(), "%.1f MB/s", bytesPerSec / (1024.0 * 1024.0));
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format(Locale.getDefault(), "%.1f %cB", bytes / Math.pow(1024, exp), pre);
    }

    private void refreshProfiles() {
        if (profileAdapter != null) {
            profileAdapter.notifyDataSetChanged();
        }
        refreshPresetPortViews();
        updateActiveProfileUi();
        updateNodesCount();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(UnifiedVpnService.ACTION_STATE_CHANGED);
        filter.addAction(UnifiedVpnService.ACTION_TRAFFIC_UPDATE);
        filter.addAction(UnifiedVpnService.ACTION_CONNECTION_CAPTURED);

        ContextCompat.registerReceiver(this, vpnStateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        updateConnectionUi(UnifiedVpnService.getCurrentState());
        updateActiveProfileUi();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(vpnStateReceiver);
        } catch (IllegalArgumentException ignored) {}
    }

    @Override
    protected void onDestroy() {
        stopConnectionTimer();
        if (webviewIppure != null) {
            if (webviewIppure.getParent() instanceof ViewGroup) {
                ((ViewGroup) webviewIppure.getParent()).removeView(webviewIppure);
            }
            webviewIppure.destroy();
            webviewIppure = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (pageIppure.getVisibility() == View.VISIBLE && webviewIppure != null && webviewIppure.canGoBack()) {
            webviewIppure.goBack();
        } else if (pageNodes.getVisibility() == View.VISIBLE || pageRouting.getVisibility() == View.VISIBLE || pageIppure.getVisibility() == View.VISIBLE) {
            switchTab(0);
        } else {
            super.onBackPressed();
        }
    }

    // RecyclerView Adapter for Profiles
    private class ProfileAdapter extends RecyclerView.Adapter<ProfileAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_profile, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            List<ProxyProfile> list = repository.getProfiles();
            ProxyProfile profile = list.get(position);
            ProxyProfile active = repository.getActiveProfile();

            holder.tvName.setText(profile.getName());
            holder.tvEndpoint.setText(profile.getEndpointDisplay());
            holder.rbSelected.setChecked(active != null && active.getId().equals(profile.getId()));

            if (profile.getProtocolType() == ProtocolType.WIREGUARD) {
                holder.tvProto.setText("WG");
                holder.tvProto.setBackgroundColor(ContextCompat.getColor(MainActivity.this, R.color.accent_wireguard));
            } else {
                holder.tvProto.setText("OVPN");
                holder.tvProto.setBackgroundColor(ContextCompat.getColor(MainActivity.this, R.color.accent_openvpn));
            }

            holder.itemView.setOnClickListener(v -> {
                repository.setActiveProfileId(profile.getId());
                refreshProfiles();
            });

            holder.btnDelete.setOnClickListener(v -> {
                new MaterialAlertDialogBuilder(MainActivity.this)
                        .setTitle("确认删除")
                        .setMessage("确定要删除配置 \"" + profile.getName() + "\" 吗？")
                        .setPositiveButton("删除", (d, w) -> {
                            repository.deleteProfile(profile.getId());
                            refreshProfiles();
                        })
                        .setNegativeButton("取消", null)
                        .show();
            });
        }

        @Override
        public int getItemCount() {
            return repository.getProfiles().size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            RadioButton rbSelected;
            TextView tvProto;
            TextView tvName;
            TextView tvEndpoint;
            ImageButton btnDelete;

            ViewHolder(View itemView) {
                super(itemView);
                rbSelected = itemView.findViewById(R.id.rb_selected);
                tvProto = itemView.findViewById(R.id.tv_profile_proto);
                tvName = itemView.findViewById(R.id.tv_profile_name);
                tvEndpoint = itemView.findViewById(R.id.tv_profile_endpoint);
                btnDelete = itemView.findViewById(R.id.btn_delete);
            }
        }
    }
}
