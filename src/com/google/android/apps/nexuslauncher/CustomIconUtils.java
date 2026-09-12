package com.google.android.apps.nexuslauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.os.UserHandle;
import android.util.Log;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.search.DefaultAppSearchAlgorithm;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.graphics.DrawableFactory;
import com.android.launcher3.shortcuts.DeepShortcutManager;
import com.android.launcher3.shortcuts.ShortcutInfoCompat;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.LooperExecutor;
import com.google.android.apps.nexuslauncher.clock.CustomClock;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class CustomIconUtils {
    private final static String[] ICON_INTENTS = new String[] {
            "com.fede.launcher.THEME_ICONPACK",
            "com.anddoes.launcher.THEME",
            "com.novalauncher.THEME",
            "com.teslacoilsw.launcher.THEME",
            "com.gau.go.launcherex.theme",
            "org.adw.launcher.THEMES",
            "org.adw.launcher.icons.ACTION_PICK_ICON"
    };

    static HashMap<String, CharSequence> getPackProviders(Context context) {
        PackageManager pm = context.getPackageManager();
        HashMap<String, CharSequence> packs = new HashMap<>();
        for (String intent : ICON_INTENTS) {
            for (ResolveInfo info : pm.queryIntentActivities(new Intent(intent), PackageManager.GET_META_DATA)) {
                packs.put(info.activityInfo.packageName, info.loadLabel(pm));
            }
        }
        return packs;
    }

    static boolean isPackProvider(Context context, String packageName) {
        if (packageName != null && !packageName.isEmpty()) {
            PackageManager pm = context.getPackageManager();
            for (String intent : ICON_INTENTS) {
                if (pm.queryIntentActivities(new Intent(intent).setPackage(packageName),
                        PackageManager.GET_META_DATA).iterator().hasNext()) {
                    return true;
                }
            }
        }
        return false;
    }

    static String getCurrentPack(Context context) {
        return BuiltInIconPack.getPackageName(context);
    }

    static void setCurrentPack(Context context, String pack) {
        SharedPreferences.Editor edit = Utilities.getPrefs(context).edit();
        edit.putString(SettingsActivity.ICON_PACK_PREF, pack);
        edit.apply();
    }

    static boolean usingValidPack(Context context) {
        return BuiltInIconPack.getResources(context) != null;
    }

    static void applyIconPackAsync(final Context context) {
        new LooperExecutor(LauncherModel.getWorkerLooper()).execute(new Runnable() {
            @Override
            public void run() {
                UserManagerCompat userManagerCompat = UserManagerCompat.getInstance(context);
                LauncherModel model = LauncherAppState.getInstance(context).getModel();

                boolean noPack = CustomIconUtils.getCurrentPack(context).isEmpty();
                Utilities.getPrefs(context).edit().putBoolean(DefaultAppSearchAlgorithm.SEARCH_HIDDEN_APPS, !noPack).apply();
                if (noPack) {
                    CustomAppFilter.resetAppFilter(context);
                }
                for (UserHandle user : userManagerCompat.getUserProfiles()) {
                    model.onPackagesReload(user);
                }

                CustomIconProvider.clearDisabledApps(context);
                ((CustomDrawableFactory) DrawableFactory.get(context)).reloadIconPack();

                DeepShortcutManager shortcutManager = DeepShortcutManager.getInstance(context);
                LauncherAppsCompat launcherApps = LauncherAppsCompat.getInstance(context);
                for (UserHandle user : userManagerCompat.getUserProfiles()) {
                    HashSet<String> pkgsSet = new HashSet<>();
                    for (LauncherActivityInfo info : launcherApps.getActivityList(null, user)) {
                        pkgsSet.add(info.getComponentName().getPackageName());
                    }
                    for (String pkg : pkgsSet) {
                        reloadIcon(shortcutManager, model, user, pkg);
                    }
                }
            }
        });
    }

    static void reloadIconByKey(Context context, ComponentKey key) {
        LauncherModel model = LauncherAppState.getInstance(context).getModel();
        DeepShortcutManager shortcutManager = DeepShortcutManager.getInstance(context);
        reloadIcon(shortcutManager, model, key.user, key.componentName.getPackageName());
    }

    static void reloadIcon(DeepShortcutManager shortcutManager, LauncherModel model, UserHandle user, String pkg) {
        model.onPackageChanged(pkg, user);
        List<ShortcutInfoCompat> shortcuts = shortcutManager.queryForPinnedShortcuts(pkg, user);
        if (!shortcuts.isEmpty()) {
            model.updatePinnedShortcuts(pkg, shortcuts, user);
        }
    }

    static void parsePack(CustomDrawableFactory factory, PackageManager pm, String iconPack) {
        Resources res = BuiltInIconPack.getResources(factory.getContext());
        if (res == null) return;
        addBuiltIn(factory, res, iconPack, "com.sec.android.app.camera/.Camera", "samsung_camera");
        addBuiltIn(factory, res, iconPack, "com.samsung.android.app.contacts/com.samsung.android.contacts.contactslist.PeopleActivity", "samsung_contacts");
        addBuiltIn(factory, res, iconPack, "com.sec.android.gallery3d/com.samsung.android.gallery.app.activity.GalleryActivity", "samsung_gallery");
        addBuiltIn(factory, res, iconPack, "com.samsung.android.messaging/com.android.mms.ui.ConversationComposer", "messages");
        addBuiltIn(factory, res, iconPack, "com.samsung.android.dialer/.DialtactsActivity", "phone");
        addBuiltIn(factory, res, iconPack, "com.android.settings/.Settings", "settings");
        Log.i("PaperdeskIcons", "Loaded " + factory.packComponents.size()
                + " built-in Arcticons mappings");
    }

    private static void addBuiltIn(CustomDrawableFactory factory, Resources res, String iconPack,
            String component, String drawable) {
        ComponentName name = ComponentName.unflattenFromString(component);
        int drawableId = res.getIdentifier(drawable, "drawable", iconPack);
        if (name != null && drawableId != 0) factory.packComponents.put(name, drawableId);
    }
}
