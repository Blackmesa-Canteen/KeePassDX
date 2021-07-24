/*
 * Copyright 2019 Jeremy Jamet / Kunzisoft.
 *
 * This file is part of KeePassDX.
 *
 *  KeePassDX is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  KeePassDX is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with KeePassDX.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.kunzisoft.keepass.settings

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.fragment.app.DialogFragment
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.SwitchPreference
import com.kunzisoft.androidclearchroma.ChromaUtil
import com.kunzisoft.keepass.R
import com.kunzisoft.keepass.activities.dialogs.AssignMasterKeyDialogFragment
import com.kunzisoft.keepass.activities.helpers.ReadOnlyHelper
import com.kunzisoft.keepass.database.crypto.EncryptionAlgorithm
import com.kunzisoft.keepass.database.crypto.kdf.KdfEngine
import com.kunzisoft.keepass.database.element.Group
import com.kunzisoft.keepass.database.element.database.CompressionAlgorithm
import com.kunzisoft.keepass.services.DatabaseTaskNotificationService
import com.kunzisoft.keepass.settings.preference.*
import com.kunzisoft.keepass.settings.preferencedialogfragment.*
import com.kunzisoft.keepass.tasks.ActionRunnable
import com.kunzisoft.keepass.utils.MenuUtil

class NestedDatabaseSettingsFragment : NestedSettingsFragment() {

    private var mDatabaseReadOnly: Boolean = false
    private var mDatabaseAutoSaveEnabled: Boolean = true

    private var dbNamePref: InputTextPreference? = null
    private var dbDescriptionPref: InputTextPreference? = null
    private var dbDefaultUsername: InputTextPreference? = null
    private var dbCustomColorPref: DialogColorPreference? = null
    private var dbDataCompressionPref: Preference? = null
    private var recycleBinGroupPref: DialogListExplanationPreference? = null
    private var templatesGroupPref: DialogListExplanationPreference? = null
    private var dbMaxHistoryItemsPref: InputNumberPreference? = null
    private var dbMaxHistorySizePref: InputNumberPreference? = null
    private var mEncryptionAlgorithmPref: DialogListExplanationPreference? = null
    private var mKeyDerivationPref: DialogListExplanationPreference? = null
    private var mRoundPref: InputKdfNumberPreference? = null
    private var mMemoryPref: InputKdfSizePreference? = null
    private var mParallelismPref: InputKdfNumberPreference? = null

    override fun onCreateScreenPreference(screen: Screen, savedInstanceState: Bundle?, rootKey: String?) {
        setHasOptionsMenu(true)

        mDatabaseReadOnly = mDatabase?.isReadOnly == true
                || ReadOnlyHelper.retrieveReadOnlyFromInstanceStateOrArguments(savedInstanceState, arguments)

        // Load the preferences from an XML resource
        when (screen) {
            Screen.DATABASE -> {
                onCreateDatabasePreference(rootKey)
            }
            Screen.DATABASE_SECURITY -> {
                onCreateDatabaseSecurityPreference(rootKey)
            }
            Screen.DATABASE_MASTER_KEY -> {
                onCreateDatabaseMasterKeyPreference(rootKey)
            }
            else -> {}
        }
    }

    private fun onCreateDatabasePreference(rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_database, rootKey)

        mDatabase?.let { database ->
            if (database.loaded) {

                val dbGeneralPrefCategory: PreferenceCategory? = findPreference(getString(R.string.database_category_general_key))

                // Database name
                dbNamePref = findPreference(getString(R.string.database_name_key))
                if (database.allowName) {
                    dbNamePref?.summary = database.name
                } else {
                    dbGeneralPrefCategory?.removePreference(dbNamePref)
                }

                // Database description
                dbDescriptionPref = findPreference(getString(R.string.database_description_key))
                if (database.allowDescription) {
                    dbDescriptionPref?.summary = database.description
                } else {
                    dbGeneralPrefCategory?.removePreference(dbDescriptionPref)
                }

                // Database default username
                dbDefaultUsername = findPreference(getString(R.string.database_default_username_key))
                if (database.allowDefaultUsername) {
                    dbDefaultUsername?.summary = database.defaultUsername
                } else {
                    dbDefaultUsername?.isEnabled = false
                    // TODO dbGeneralPrefCategory?.removePreference(dbDefaultUsername)
                }

                // Database custom color
                dbCustomColorPref = findPreference(getString(R.string.database_custom_color_key))
                if (database.allowCustomColor) {
                    dbCustomColorPref?.apply {
                        try {
                            color = Color.parseColor(database.customColor)
                            summary = database.customColor
                        } catch (e: Exception) {
                            color = DialogColorPreference.DISABLE_COLOR
                            summary = ""
                        }
                    }
                } else {
                    dbCustomColorPref?.isEnabled = false
                    // TODO dbGeneralPrefCategory?.removePreference(dbCustomColorPref)
                }

                // Version
                findPreference<Preference>(getString(R.string.database_version_key))
                        ?.summary = database.version

                val dbCompressionPrefCategory: PreferenceCategory? = findPreference(getString(R.string.database_category_data_key))

                // Database compression
                dbDataCompressionPref = findPreference(getString(R.string.database_data_compression_key))
                if (database.allowDataCompression) {
                    dbDataCompressionPref?.summary = (database.compressionAlgorithm
                            ?: CompressionAlgorithm.None).getName(resources)
                } else {
                    dbCompressionPrefCategory?.isVisible = false
                }

                val dbRecycleBinPrefCategory: PreferenceCategory? = findPreference(getString(R.string.database_category_recycle_bin_key))
                recycleBinGroupPref = findPreference(getString(R.string.recycle_bin_group_key))

                // Recycle bin
                if (database.allowConfigurableRecycleBin) {
                    val recycleBinEnablePref: SwitchPreference? = findPreference(getString(R.string.recycle_bin_enable_key))
                    recycleBinEnablePref?.apply {
                        isChecked = database.isRecycleBinEnabled
                        isEnabled = if (!mDatabaseReadOnly) {
                            setOnPreferenceChangeListener { _, newValue ->
                                val recycleBinEnabled = newValue as Boolean
                                database.enableRecycleBin(recycleBinEnabled, resources)
                                refreshRecycleBinGroup()
                                // Save the database if not in readonly mode
                                (context as SettingsActivity?)?.
                                    mProgressDatabaseTaskProvider?.startDatabaseSave(mDatabaseAutoSaveEnabled)
                                true
                            }
                            true
                        } else {
                            false
                        }
                    }
                    // Change the recycle bin group
                    recycleBinGroupPref?.setOnPreferenceClickListener {

                        true
                    }
                    // Recycle Bin group
                    refreshRecycleBinGroup()
                } else {
                    recycleBinGroupPref?.onPreferenceClickListener = null
                    dbRecycleBinPrefCategory?.isVisible = false
                }

                // Templates
                val templatesGroupPrefCategory: PreferenceCategory? = findPreference(getString(R.string.database_category_templates_key))
                templatesGroupPref = findPreference(getString(R.string.templates_group_uuid_key))
                if (database.allowConfigurableTemplatesGroup) {
                    val templatesEnablePref: SwitchPreference? = findPreference(getString(R.string.templates_group_enable_key))
                    templatesEnablePref?.apply {
                        isChecked = database.isTemplatesEnabled
                        isEnabled = if (!mDatabaseReadOnly) {
                            setOnPreferenceChangeListener { _, newValue ->
                                val templatesEnabled = newValue as Boolean
                                database.enableTemplates(templatesEnabled, resources)
                                refreshTemplatesGroup()
                                // Save the database if not in readonly mode
                                (context as SettingsActivity?)?.
                                    mProgressDatabaseTaskProvider?.startDatabaseSave(mDatabaseAutoSaveEnabled)
                                true
                            }
                            true
                        } else {
                            false
                        }
                    }
                    // Recycle Bin group
                    refreshTemplatesGroup()
                } else {
                    templatesGroupPrefCategory?.isVisible = false
                }

                // History
                findPreference<PreferenceCategory>(getString(R.string.database_category_history_key))
                        ?.isVisible = database.manageHistory == true

                // Max history items
                dbMaxHistoryItemsPref = findPreference<InputNumberPreference>(getString(R.string.max_history_items_key))?.apply {
                    summary = database.historyMaxItems.toString()
                }

                // Max history size
                dbMaxHistorySizePref = findPreference<InputNumberPreference>(getString(R.string.max_history_size_key))?.apply {
                    summary = database.historyMaxSize.toString()
                }

            } else {
                Log.e(javaClass.name, "Database isn't ready")
            }
        }
    }

    private fun refreshRecycleBinGroup() {
        recycleBinGroupPref?.apply {
            if (mDatabase?.isRecycleBinEnabled == true) {
                summary = mDatabase?.recycleBin?.toString()
                isEnabled = true
            } else {
                summary = null
                isEnabled = false
            }
        }
    }

    private fun refreshTemplatesGroup() {
        templatesGroupPref?.apply {
            if (mDatabase?.isTemplatesEnabled == true) {
                summary = mDatabase?.templatesGroup?.toString()
                isEnabled = true
            } else {
                summary = null
                isEnabled = false
            }
        }
    }

    private fun onCreateDatabaseSecurityPreference(rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_database_security, rootKey)

        mDatabase?.let { database ->
            if (database.loaded) {
                // Encryption Algorithm
                mEncryptionAlgorithmPref = findPreference<DialogListExplanationPreference>(getString(R.string.encryption_algorithm_key))?.apply {
                    summary = database.getEncryptionAlgorithmName()
                }

                // Key derivation function
                mKeyDerivationPref = findPreference<DialogListExplanationPreference>(getString(R.string.key_derivation_function_key))?.apply {
                    summary = database.getKeyDerivationName()
                }

                // Round encryption
                mRoundPref = findPreference<InputKdfNumberPreference>(getString(R.string.transform_rounds_key))?.apply {
                    summary = database.numberKeyEncryptionRounds.toString()
                }

                // Memory Usage
                mMemoryPref = findPreference<InputKdfSizePreference>(getString(R.string.memory_usage_key))?.apply {
                    summary = database.memoryUsage.toString()
                }

                // Parallelism
                mParallelismPref = findPreference<InputKdfNumberPreference>(getString(R.string.parallelism_key))?.apply {
                    summary = database.parallelism.toString()
                }
            } else {
                Log.e(javaClass.name, "Database isn't ready")
            }
        }
    }

    private fun onCreateDatabaseMasterKeyPreference(rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_database_master_key, rootKey)

        mDatabase?.let { database ->
            if (database.loaded) {
                findPreference<Preference>(getString(R.string.settings_database_change_credentials_key))?.apply {
                    isEnabled = if (!mDatabaseReadOnly) {
                        onPreferenceClickListener = Preference.OnPreferenceClickListener {
                            AssignMasterKeyDialogFragment.getInstance(database.allowNoMasterKey)
                                    .show(parentFragmentManager, "passwordDialog")
                            false
                        }
                        true
                    } else {
                        false
                    }
                }
            } else {
                Log.e(javaClass.name, "Database isn't ready")
            }
        }
    }

    private val colorSelectedListener: ((Boolean, Int)-> Unit) = { enable, color ->
        dbCustomColorPref?.summary = ChromaUtil.getFormattedColorString(color, false)
        if (enable) {
            dbCustomColorPref?.color = color
        } else {
            dbCustomColorPref?.color = DialogColorPreference.DISABLE_COLOR
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)

        try {
            // To reassign color listener after orientation change
            val chromaDialog = parentFragmentManager.findFragmentByTag(TAG_PREF_FRAGMENT) as DatabaseColorPreferenceDialogFragmentCompat?
            chromaDialog?.onColorSelectedListener = colorSelectedListener
        } catch (e: Exception) {}

        return view
    }

    override fun onProgressDialogThreadResult(actionTask: String,
                                     result: ActionRunnable.Result) {
        result.data?.let { data ->
            if (data.containsKey(DatabaseTaskNotificationService.OLD_ELEMENT_KEY)
                    && data.containsKey(DatabaseTaskNotificationService.NEW_ELEMENT_KEY)) {
                when (actionTask) {
                    /*
                    --------
                    Main preferences
                    --------
                    */
                    DatabaseTaskNotificationService.ACTION_DATABASE_UPDATE_NAME_TASK -> {
                        val oldName = data.getString(DatabaseTaskNotificationService.OLD_ELEMENT_KEY)!!
                        val newName = data.getString(DatabaseTaskNotificationService.NEW_ELEMENT_KEY)!!
                        val nameToShow =
                                if (result.isSuccess) {
                                    newName
                                } else {
                                    mDatabase?.name = oldName
                                    oldName
                                }
                        dbNamePref?.summary = nameToShow
                    }
                    DatabaseTaskNotificationService.ACTION_DATABASE_UPDATE_DESCRIPTION_TASK -> {
                        val oldDescription = data.getString(DatabaseTaskNotificationService.OLD_ELEMENT_KEY)!!
                        val newDescription = data.getString(DatabaseTaskNotificationService.NEW_ELEMENT_KEY)!!
                        val descriptionToShow =
                                if (result.isSuccess) {
                                    newDescription
                                } else {
                                    mDatabase?.description = oldDescription
                                    oldDescription
                                }
                        dbDescriptionPref?.summary = descriptionToShow
                    }
                    DatabaseTaskNotificationService.ACTION_DATABASE_UPDATE_DEFAULT_USERNAME_TASK -> {
                        val oldDefaultUsername = data.getString(DatabaseTaskNotificationService.OLD_ELEMENT_KEY)!!
                        val newDefaultUsername = data.getString(DatabaseTaskNotificationService.NEW_ELEMENT_KEY)!!
                        val defaultUsernameToShow =
                                if (result.isSuccess) {
                                    newDefaultUsername
                                } else {
                                    mDatabase?.defaultUsername = oldDefaultUsername
                                    oldDefaultUsername
                                }
                        dbDefaultUsername?.summary = defaultUsernameToShow
                    }
                    DatabaseTaskNotificationService.ACTION_DATABASE_UPDATE_COLOR_TASK -> {
                        val oldColor = data.getString(DatabaseTaskNotificationService.OLD_ELEMENT_KEY)!!
                        val newColor = data.getString(DatabaseTaskNotificationService.NEW_ELEMENT_KEY)!!

                        val defaultColorToShow =
                                if (result.isSuccess) {
                                    newColor
                                } else {
                                    mDatabase?.customColor = oldColor
                                    oldColor
                                }
                        dbCustomColorPref?.summary = defaultColorToShow
                    }
                    DatabaseTaskNotificationService.ACTION_DATABASE_UPDATE_COMPRESSION_TASK -> {
                        val oldCompression = data.getSerializable(DatabaseTaskNotificationService.OLD_ELEMENT_KEY) as CompressionAlgorithm
                        val newCompression = data.getSerializable(DatabaseTaskNotificationService.NEW_ELEMENT_KEY) as CompressionAlgorithm
                        val algorithmToShow =
                                if (result.isSuccess) {
                                    newCompression
                                } else {
                                    mDatabase?.compressionAlgorithm = oldCompression
                                    oldCompression
                                }
                        dbDataCompressionPref?.summary = algorithmToShow.getName(resources)
                    }
                    DatabaseTaskNotificationService.ACTION_DATABASE_UPDATE_RECYCLE_BIN_TASK -> {
                        val oldRecycleBin = data.getParcelable<Group?>(DatabaseTaskNotificationService.OLD_ELEMENT_KEY)
                        val newRecycleBin = data.getParcelable<Group?>(DatabaseTaskNotificationService.NEW_ELEMENT_KEY)
                        val recycleBinToShow =
                                if (result.isSuccess) {
                                    newRecycleBin
                                } else {
                                    oldRecycleBin
                                }
                        mDatabase?.setRecycleBin(recycleBinToShow)
                        refreshRecycleBinGroup()
                    }
                    DatabaseTaskNotificationService.ACTION_DATABASE_UPDATE_TEMPLATES_GROUP_TASK -> {
                        val oldTemplatesGroup = data.getParcelable<Group?>(DatabaseTaskNotificationService.OLD_ELEMENT_KEY)
                        val newTemplatesGroup = data.getParcelable<Group?>(DatabaseTaskNotificationService.NEW_ELEMENT_KEY)
                        val templatesGroupToShow =
                            if (result.isSuccess) {
                                newTemplatesGroup
                            } else {
                                oldTemplatesGroup
                            }
                        mDatabase?.setTemplatesGroup(templatesGroupToShow)
                        refreshTemplatesGroup()
                    }
                    DatabaseTaskNotificationService.ACTION_DATABASE_UPDATE_MAX_HISTORY_ITEMS_TASK -> {
                        val oldMaxHistoryItems = data.getInt(DatabaseTaskNotificationService.OLD_ELEMENT_KEY)
                        val newMaxHistoryItems = data.getInt(DatabaseTaskNotificationService.NEW_ELEMENT_KEY)
                        val maxHistoryItemsToShow =
                                if (result.isSuccess) {
                                    newMaxHistoryItems
                                } else {
                                    mDatabase?.historyMaxItems = oldMaxHistoryItems
                                    oldMaxHistoryItems
                                }
                        dbMaxHistoryItemsPref?.summary = maxHistoryItemsToShow.toString()
                    }
                    DatabaseTaskNotificationService.ACTION_DATABASE_UPDATE_MAX_HISTORY_SIZE_TASK -> {
                        val oldMaxHistorySize = data.getLong(DatabaseTaskNotificationService.OLD_ELEMENT_KEY)
                        val newMaxHistorySize = data.getLong(DatabaseTaskNotificationService.NEW_ELEMENT_KEY)
                        val maxHistorySizeToShow =
                                if (result.isSuccess) {
                                    newMaxHistorySize
                                } else {
                                    mDatabase?.historyMaxSize = oldMaxHistorySize
                                    oldMaxHistorySize
                                }
                        dbMaxHistorySizePref?.summary = maxHistorySizeToShow.toString()
                    }

                    /*
                    --------
                    Security
                    --------
                     */
                    DatabaseTaskNotificationService.ACTION_DATABASE_UPDATE_ENCRYPTION_TASK -> {
                        val oldEncryption = data.getSerializable(DatabaseTaskNotificationService.OLD_ELEMENT_KEY) as EncryptionAlgorithm
                        val newEncryption = data.getSerializable(DatabaseTaskNotificationService.NEW_ELEMENT_KEY) as EncryptionAlgorithm
                        val algorithmToShow =
                                if (result.isSuccess) {
                                    newEncryption
                                } else {
                                    mDatabase?.encryptionAlgorithm = oldEncryption
                                    oldEncryption
                                }
                        mEncryptionAlgorithmPref?.summary = algorithmToShow.toString()
                    }
                    DatabaseTaskNotificationService.ACTION_DATABASE_UPDATE_KEY_DERIVATION_TASK -> {
                        val oldKeyDerivationEngine = data.getSerializable(DatabaseTaskNotificationService.OLD_ELEMENT_KEY) as KdfEngine
                        val newKeyDerivationEngine = data.getSerializable(DatabaseTaskNotificationService.NEW_ELEMENT_KEY) as KdfEngine
                        val kdfEngineToShow =
                                if (result.isSuccess) {
                                    newKeyDerivationEngine
                                } else {
                                    mDatabase?.kdfEngine = oldKeyDerivationEngine
                                    oldKeyDerivationEngine
                                }
                        mKeyDerivationPref?.summary = kdfEngineToShow.toString()

                        mRoundPref?.summary = kdfEngineToShow.defaultKeyRounds.toString()
                        // Disable memory and parallelism if not available
                        mMemoryPref?.summary = kdfEngineToShow.defaultMemoryUsage.toString()
                        mParallelismPref?.summary = kdfEngineToShow.defaultParallelism.toString()
                    }
                    DatabaseTaskNotificationService.ACTION_DATABASE_UPDATE_ITERATIONS_TASK -> {
                        val oldIterations = data.getLong(DatabaseTaskNotificationService.OLD_ELEMENT_KEY)
                        val newIterations = data.getLong(DatabaseTaskNotificationService.NEW_ELEMENT_KEY)
                        val roundsToShow =
                                if (result.isSuccess) {
                                    newIterations
                                } else {
                                    mDatabase?.numberKeyEncryptionRounds = oldIterations
                                    oldIterations
                                }
                        mRoundPref?.summary = roundsToShow.toString()
                    }
                    DatabaseTaskNotificationService.ACTION_DATABASE_UPDATE_MEMORY_USAGE_TASK -> {
                        val oldMemoryUsage = data.getLong(DatabaseTaskNotificationService.OLD_ELEMENT_KEY)
                        val newMemoryUsage = data.getLong(DatabaseTaskNotificationService.NEW_ELEMENT_KEY)
                        val memoryToShow =
                                if (result.isSuccess) {
                                    newMemoryUsage
                                } else {
                                    mDatabase?.memoryUsage = oldMemoryUsage
                                    oldMemoryUsage
                                }
                        mMemoryPref?.summary = memoryToShow.toString()
                    }
                    DatabaseTaskNotificationService.ACTION_DATABASE_UPDATE_PARALLELISM_TASK -> {
                        val oldParallelism = data.getLong(DatabaseTaskNotificationService.OLD_ELEMENT_KEY)
                        val newParallelism = data.getLong(DatabaseTaskNotificationService.NEW_ELEMENT_KEY)
                        val parallelismToShow =
                                if (result.isSuccess) {
                                    newParallelism
                                } else {
                                    mDatabase?.parallelism = oldParallelism
                                    oldParallelism
                                }
                        mParallelismPref?.summary = parallelismToShow.toString()
                    }
                }
            }
        }
    }

    override fun onDisplayPreferenceDialog(preference: Preference?) {

        var otherDialogFragment = false

        var dialogFragment: DialogFragment? = null
        // Main Preferences
        when (preference?.key) {
            getString(R.string.database_name_key) -> {
                dialogFragment = DatabaseNamePreferenceDialogFragmentCompat.newInstance(preference.key)
            }
            getString(R.string.database_description_key) -> {
                dialogFragment = DatabaseDescriptionPreferenceDialogFragmentCompat.newInstance(preference.key)
            }
            getString(R.string.database_default_username_key) -> {
                dialogFragment = DatabaseDefaultUsernamePreferenceDialogFragmentCompat.newInstance(preference.key)
            }
            getString(R.string.database_custom_color_key) -> {
                dialogFragment = DatabaseColorPreferenceDialogFragmentCompat.newInstance(preference.key).apply {
                    onColorSelectedListener = colorSelectedListener
                }
            }
            getString(R.string.database_data_compression_key) -> {
                dialogFragment = DatabaseDataCompressionPreferenceDialogFragmentCompat.newInstance(preference.key)
            }
            getString(R.string.database_data_remove_unlinked_attachments_key) -> {
                dialogFragment = DatabaseRemoveUnlinkedDataPreferenceDialogFragmentCompat.newInstance(preference.key)
            }
            getString(R.string.recycle_bin_group_key) -> {
                dialogFragment = DatabaseRecycleBinGroupPreferenceDialogFragmentCompat.newInstance(preference.key)
            }
            getString(R.string.templates_group_uuid_key) -> {
                dialogFragment = DatabaseTemplatesGroupPreferenceDialogFragmentCompat.newInstance(preference.key)
            }
            getString(R.string.max_history_items_key) -> {
                dialogFragment = MaxHistoryItemsPreferenceDialogFragmentCompat.newInstance(preference.key)
            }
            getString(R.string.max_history_size_key) -> {
                dialogFragment = MaxHistorySizePreferenceDialogFragmentCompat.newInstance(preference.key)
            }

            // Security
            getString(R.string.encryption_algorithm_key) -> {
                dialogFragment = DatabaseEncryptionAlgorithmPreferenceDialogFragmentCompat.newInstance(preference.key)
            }
            getString(R.string.key_derivation_function_key) -> {
                val keyDerivationDialogFragment = DatabaseKeyDerivationPreferenceDialogFragmentCompat.newInstance(preference.key)
                // Add other prefs to manage
                keyDerivationDialogFragment.setRoundPreference(mRoundPref)
                keyDerivationDialogFragment.setMemoryPreference(mMemoryPref)
                keyDerivationDialogFragment.setParallelismPreference(mParallelismPref)
                dialogFragment = keyDerivationDialogFragment
            }
            getString(R.string.transform_rounds_key) -> {
                dialogFragment = RoundsPreferenceDialogFragmentCompat.newInstance(preference.key)
            }
            getString(R.string.memory_usage_key) -> {
                dialogFragment = MemoryUsagePreferenceDialogFragmentCompat.newInstance(preference.key)
            }
            getString(R.string.parallelism_key) -> {
                dialogFragment = ParallelismPreferenceDialogFragmentCompat.newInstance(preference.key)
            }
            else -> otherDialogFragment = true
        }

        if (dialogFragment != null && !mDatabaseReadOnly) {
            dialogFragment.setTargetFragment(this, 0)
            dialogFragment.show(parentFragmentManager, TAG_PREF_FRAGMENT)
        }
        // Could not be handled here. Try with the super method.
        else if (otherDialogFragment) {
            super.onDisplayPreferenceDialog(preference)
        }
    }

    override fun onResume() {
        super.onResume()

        context?.let { context ->
            mDatabaseAutoSaveEnabled = PreferencesUtil.isAutoSaveDatabaseEnabled(context)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater.inflate(R.menu.database, menu)
        if (mDatabaseReadOnly) {
            menu.findItem(R.id.menu_save_database)?.isVisible = false
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        val settingActivity = activity as SettingsActivity?

        return when (item.itemId) {
            R.id.menu_save_database -> {
                settingActivity?.mProgressDatabaseTaskProvider?.startDatabaseSave(!mDatabaseReadOnly)
                true
            }
            R.id.menu_reload_database -> {
                settingActivity?.apply {
                    keepCurrentScreen()
                    mProgressDatabaseTaskProvider?.startDatabaseReload(false)
                }
                return true
            }

            else -> {
                // Check the time lock before launching settings
                settingActivity?.let {
                    MenuUtil.onDefaultMenuOptionsItemSelected(it, item, mDatabaseReadOnly, true)
                }
                super.onOptionsItemSelected(item)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        ReadOnlyHelper.onSaveInstanceState(outState, mDatabaseReadOnly)
        super.onSaveInstanceState(outState)
    }

    companion object {
        private const val TAG_PREF_FRAGMENT = "TAG_PREF_FRAGMENT"
    }
}