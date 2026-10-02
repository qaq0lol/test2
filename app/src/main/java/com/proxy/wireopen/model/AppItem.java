package com.proxy.wireopen.model;

import android.graphics.drawable.Drawable;

public class AppItem {
    private final String appName;
    private final String packageName;
    private final Drawable icon;
    private final boolean isSystem;
    private boolean isSelected;

    public AppItem(String appName, String packageName, Drawable icon, boolean isSystem, boolean isSelected) {
        this.appName = appName;
        this.packageName = packageName;
        this.icon = icon;
        this.isSystem = isSystem;
        this.isSelected = isSelected;
    }

    public String getAppName() {
        return appName;
    }

    public String getPackageName() {
        return packageName;
    }

    public Drawable getIcon() {
        return icon;
    }

    public boolean isSystem() {
        return isSystem;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }
}
