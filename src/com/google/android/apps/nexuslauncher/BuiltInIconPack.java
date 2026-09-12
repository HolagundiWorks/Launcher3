package com.google.android.apps.nexuslauncher;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Loads the bundled Arcticons APK as resources; it is never installed as an app. */
final class BuiltInIconPack {
    private static final String ASSET = "arcticons-15.0.5.apk";
    private static Resources sResources;
    private static String sPackageName;

    private BuiltInIconPack() { }

    static synchronized Resources getResources(Context context) {
        if (sResources != null) return sResources;
        try {
            File archive = new File(context.getFilesDir(), ASSET);
            if (!archive.exists()) copyAsset(context, archive);

            PackageManager pm = context.getPackageManager();
            PackageInfo info = pm.getPackageArchiveInfo(archive.getAbsolutePath(), 0);
            if (info == null || info.applicationInfo == null) return null;
            ApplicationInfo appInfo = info.applicationInfo;
            appInfo.sourceDir = archive.getAbsolutePath();
            appInfo.publicSourceDir = archive.getAbsolutePath();
            sPackageName = appInfo.packageName;
            sResources = pm.getResourcesForApplication(appInfo);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return sResources;
    }

    static String getPackageName(Context context) {
        getResources(context);
        return sPackageName;
    }

    private static void copyAsset(Context context, File target) throws IOException {
        InputStream input = context.getAssets().open(ASSET);
        OutputStream output = new FileOutputStream(target);
        byte[] buffer = new byte[64 * 1024];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        output.close();
        input.close();
    }
}
