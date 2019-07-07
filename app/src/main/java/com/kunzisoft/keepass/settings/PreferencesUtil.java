/*
 * Copyright 2017 Brian Pellin, Jeremy Jamet / Kunzisoft.
 *     
 * This file is part of KeePass DX.
 *
 *  KeePass DX is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  KeePass DX is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with KeePass DX.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.kunzisoft.keepass.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.kunzisoft.keepass.R;
import com.kunzisoft.keepass.database.SortNodeEnum;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class PreferencesUtil {

    private static final String NO_BACKUP_PREFERENCE_FILE_NAME = "nobackup";

    public static SharedPreferences getNoBackupSharedPreferences(Context ctx) {
        return ctx.getSharedPreferences(
                PreferencesUtil.NO_BACKUP_PREFERENCE_FILE_NAME,
                Context.MODE_PRIVATE);
    }

    public static void deleteAllValuesFromNoBackupPreferences(Context ctx) {
        SharedPreferences prefsNoBackup = getNoBackupSharedPreferences(ctx);
        SharedPreferences.Editor sharedPreferencesEditor = prefsNoBackup.edit();
        sharedPreferencesEditor.clear();
        sharedPreferencesEditor.apply();
    }

    public static boolean omitBackup(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(context.getString(R.string.omitbackup_key),
                context.getResources().getBoolean(R.bool.omitbackup_default));
    }

    public static boolean showUsernamesListEntries(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(context.getString(R.string.list_entries_show_username_key),
                context.getResources().getBoolean(R.bool.list_entries_show_username_default));
    }

    /**
     * Retrieve the text size in SP, verify the integrity of the size stored in preference
     */
	public static float getListTextSize(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        String defaultSizeString = ctx.getString(R.string.list_size_default);
        String listSize = prefs.getString(ctx.getString(R.string.list_size_key), defaultSizeString);
        if (!Arrays.asList(ctx.getResources().getStringArray(R.array.list_size_values)).contains(listSize))
            listSize = defaultSizeString;
        return Float.parseFloat(listSize);
	}

    public static int getDefaultPasswordLength(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getInt(ctx.getString(R.string.password_length_key),
                        Integer.parseInt(ctx.getString(R.string.default_password_length)));
    }

    public static Set<String> getDefaultPasswordCharacters(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getStringSet(ctx.getString(R.string.list_password_generator_options_key),
                new HashSet<>(Arrays.asList(
                        ctx.getResources()
                                .getStringArray(R.array.list_password_generator_options_default_values))));
    }

    public static boolean isClipboardNotificationsEnable(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getBoolean(ctx.getString(R.string.clipboard_notifications_key),
                ctx.getResources().getBoolean(R.bool.clipboard_notifications_default));
    }

    public static boolean isLockDatabaseWhenScreenShutOffEnable(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getBoolean(ctx.getString(R.string.lock_database_screen_off_key),
                ctx.getResources().getBoolean(R.bool.lock_database_screen_off_default));
    }

    public static boolean isLockDatabaseWhenBackButtonOnRootClicked(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getBoolean(ctx.getString(R.string.lock_database_back_root_key),
                ctx.getResources().getBoolean(R.bool.lock_database_back_root_default));
    }

    public static boolean isFingerprintEnable(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getBoolean(ctx.getString(R.string.fingerprint_enable_key),
                ctx.getResources().getBoolean(R.bool.fingerprint_enable_default));
    }

    public static boolean isFullFilePathEnable(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getBoolean(ctx.getString(R.string.full_file_path_enable_key),
                ctx.getResources().getBoolean(R.bool.full_file_path_enable_default));
    }

    public static SortNodeEnum getListSort(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return SortNodeEnum.valueOf(prefs.getString(ctx.getString(R.string.sort_node_key),
                SortNodeEnum.TITLE.name()));
    }

    public static boolean getGroupsBeforeSort(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getBoolean(ctx.getString(R.string.sort_group_before_key),
                ctx.getResources().getBoolean(R.bool.sort_group_before_default));
    }

    public static boolean getAscendingSort(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getBoolean(ctx.getString(R.string.sort_ascending_key),
                ctx.getResources().getBoolean(R.bool.sort_ascending_default));
    }

    public static boolean getRecycleBinBottomSort(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getBoolean(ctx.getString(R.string.sort_recycle_bin_bottom_key),
                ctx.getResources().getBoolean(R.bool.sort_recycle_bin_bottom_default));
    }

    public static boolean isPasswordMask(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getBoolean(ctx.getString(R.string.maskpass_key),
                ctx.getResources().getBoolean(R.bool.maskpass_default));
    }

    public static boolean fieldFontIsInVisibility(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getBoolean(ctx.getString(R.string.monospace_font_fields_enable_key),
                ctx.getResources().getBoolean(R.bool.monospace_font_fields_enable_default));
    }

    public static boolean autoOpenSelectedFile(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getBoolean(ctx.getString(R.string.auto_open_file_uri_key),
                ctx.getResources().getBoolean(R.bool.auto_open_file_uri_default));
    }

    public static boolean isFirstTimeAskAllowCopyPasswordAndProtectedFields(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getBoolean(ctx.getString(R.string.allow_copy_password_first_time_key),
                ctx.getResources().getBoolean(R.bool.allow_copy_password_first_time_default));
    }

    public static boolean allowCopyPasswordAndProtectedFields(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getBoolean(ctx.getString(R.string.allow_copy_password_key),
                ctx.getResources().getBoolean(R.bool.allow_copy_password_default));
    }

    public static void setAllowCopyPasswordAndProtectedFields(Context ctx, boolean allowCopy) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        prefs.edit()
                .putBoolean(ctx.getString(R.string.allow_copy_password_first_time_key), false)
                .putBoolean(ctx.getString(R.string.allow_copy_password_key), allowCopy)
                .apply();
    }

    public static String getIconPackSelectedId(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString(
                context.getString(R.string.setting_icon_pack_choose_key),
                context.getString(R.string.setting_icon_pack_choose_default));
    }

    public static boolean emptyPasswordAllowed(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(context.getString(R.string.allow_no_password_key),
                context.getResources().getBoolean(R.bool.allow_no_password_default));
    }

    public static boolean enableReadOnlyDatabase(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(context.getString(R.string.enable_read_only_key),
                context.getResources().getBoolean(R.bool.enable_read_only_default));
    }

    public static boolean enableKeyboardNotificationEntry(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(context.getString(R.string.keyboard_notification_entry_key),
                context.getResources().getBoolean(R.bool.keyboard_notification_entry_default));
    }

    public static boolean enableKeyboardVibration(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(context.getString(R.string.keyboard_key_vibrate_key),
                context.getResources().getBoolean(R.bool.keyboard_key_vibrate_default));
    }

    public static boolean enableKeyboardSound(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(context.getString(R.string.keyboard_key_sound_key),
                context.getResources().getBoolean(R.bool.keyboard_key_sound_default));
    }
}
