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
 */
package com.kunzisoft.keepass.activities

import android.app.Activity
import android.app.DatePickerDialog
import android.app.SearchManager
import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.FragmentManager
import com.google.android.material.snackbar.Snackbar
import com.kunzisoft.keepass.R
import com.kunzisoft.keepass.activities.dialogs.*
import com.kunzisoft.keepass.activities.fragments.ListNodesFragment
import com.kunzisoft.keepass.activities.helpers.EntrySelectionHelper
import com.kunzisoft.keepass.activities.helpers.ReadOnlyHelper
import com.kunzisoft.keepass.activities.helpers.SpecialMode
import com.kunzisoft.keepass.activities.lock.LockingActivity
import com.kunzisoft.keepass.activities.lock.resetAppTimeoutWhenViewFocusedOrChanged
import com.kunzisoft.keepass.adapters.SearchEntryCursorAdapter
import com.kunzisoft.keepass.autofill.AutofillComponent
import com.kunzisoft.keepass.autofill.AutofillHelper
import com.kunzisoft.keepass.database.element.*
import com.kunzisoft.keepass.database.element.node.Node
import com.kunzisoft.keepass.database.element.node.NodeId
import com.kunzisoft.keepass.database.element.node.Type
import com.kunzisoft.keepass.database.search.SearchHelper
import com.kunzisoft.keepass.education.GroupActivityEducation
import com.kunzisoft.keepass.model.GroupInfo
import com.kunzisoft.keepass.model.RegisterInfo
import com.kunzisoft.keepass.model.SearchInfo
import com.kunzisoft.keepass.services.DatabaseTaskNotificationService.Companion.ACTION_DATABASE_COPY_NODES_TASK
import com.kunzisoft.keepass.services.DatabaseTaskNotificationService.Companion.ACTION_DATABASE_CREATE_GROUP_TASK
import com.kunzisoft.keepass.services.DatabaseTaskNotificationService.Companion.ACTION_DATABASE_DELETE_NODES_TASK
import com.kunzisoft.keepass.services.DatabaseTaskNotificationService.Companion.ACTION_DATABASE_MOVE_NODES_TASK
import com.kunzisoft.keepass.services.DatabaseTaskNotificationService.Companion.ACTION_DATABASE_RELOAD_TASK
import com.kunzisoft.keepass.services.DatabaseTaskNotificationService.Companion.ACTION_DATABASE_UPDATE_ENTRY_TASK
import com.kunzisoft.keepass.services.DatabaseTaskNotificationService.Companion.ACTION_DATABASE_UPDATE_GROUP_TASK
import com.kunzisoft.keepass.services.DatabaseTaskNotificationService.Companion.NEW_NODES_KEY
import com.kunzisoft.keepass.services.DatabaseTaskNotificationService.Companion.OLD_NODES_KEY
import com.kunzisoft.keepass.services.DatabaseTaskNotificationService.Companion.getListNodesFromBundle
import com.kunzisoft.keepass.settings.PreferencesUtil
import com.kunzisoft.keepass.timeout.TimeoutHelper
import com.kunzisoft.keepass.utils.MenuUtil
import com.kunzisoft.keepass.view.*
import org.joda.time.DateTime

class GroupActivity : LockingActivity(),
        GroupEditDialogFragment.EditGroupListener,
        DatePickerDialog.OnDateSetListener,
        TimePickerDialog.OnTimeSetListener,
        ListNodesFragment.NodeClickListener,
        ListNodesFragment.NodesActionMenuListener,
        DeleteNodesDialogFragment.DeleteNodeListener,
        ListNodesFragment.OnScrollListener,
        SortDialogFragment.SortSelectionListener {

    // Views
    private var rootContainerView: ViewGroup? = null
    private var coordinatorLayout: CoordinatorLayout? = null
    private var lockView: View? = null
    private var toolbar: Toolbar? = null
    private var searchTitleView: View? = null
    private var toolbarAction: ToolbarAction? = null
    private var iconView: ImageView? = null
    private var numberChildrenView: TextView? = null
    private var addNodeButtonView: AddNodeButtonView? = null
    private var groupNameView: TextView? = null

    private var mListNodesFragment: ListNodesFragment? = null
    private var mRequestStartupSearch = true

    private var actionNodeMode: ActionMode? = null

    // To manage history in selection mode
    private var mSelectionModeCountBackStack = 0

    // Nodes
    private var mRootGroup: Group? = null
    private var mCurrentGroup: Group? = null
    private var mOldGroupToUpdate: Group? = null

    private var mSearchSuggestionAdapter: SearchEntryCursorAdapter? = null

    private var mIconColor: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Construct main view
        setContentView(layoutInflater.inflate(R.layout.activity_group, null))

        // Initialize views
        rootContainerView = findViewById(R.id.activity_group_container_view)
        coordinatorLayout = findViewById(R.id.group_coordinator)
        iconView = findViewById(R.id.group_icon)
        numberChildrenView = findViewById(R.id.group_numbers)
        addNodeButtonView = findViewById(R.id.add_node_button)
        toolbar = findViewById(R.id.toolbar)
        searchTitleView = findViewById(R.id.search_title)
        groupNameView = findViewById(R.id.group_name)
        toolbarAction = findViewById(R.id.toolbar_action)
        lockView = findViewById(R.id.lock_button)

        lockView?.setOnClickListener {
            lockAndExit()
        }

        toolbar?.title = ""
        setSupportActionBar(toolbar)

        // Retrieve the textColor to tint the icon
        val taTextColor = theme.obtainStyledAttributes(intArrayOf(R.attr.textColorInverse))
        mIconColor = taTextColor.getColor(0, Color.WHITE)
        taTextColor.recycle()

        // Focus view to reinitialize timeout
        rootContainerView?.resetAppTimeoutWhenViewFocusedOrChanged(this, mDatabase)

        // Retrieve elements after an orientation change
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(REQUEST_STARTUP_SEARCH_KEY))
                mRequestStartupSearch = savedInstanceState.getBoolean(REQUEST_STARTUP_SEARCH_KEY)
            if (savedInstanceState.containsKey(OLD_GROUP_TO_UPDATE_KEY))
                mOldGroupToUpdate = savedInstanceState.getParcelable(OLD_GROUP_TO_UPDATE_KEY)
        }

        try {
            mRootGroup = mDatabase?.rootGroup
        } catch (e: NullPointerException) {
            Log.e(TAG, "Unable to get rootGroup")
        }

        mCurrentGroup = retrieveCurrentGroup(intent, savedInstanceState)
        val currentGroupIsASearch = mCurrentGroup?.isVirtual == true

        Log.i(TAG, "Started creating tree")
        if (mCurrentGroup == null) {
            Log.w(TAG, "Group was null")
            return
        }

        var fragmentTag = LIST_NODES_FRAGMENT_TAG
        if (currentGroupIsASearch)
            fragmentTag = SEARCH_FRAGMENT_TAG

        // Initialize the fragment with the list
        mListNodesFragment = supportFragmentManager.findFragmentByTag(fragmentTag) as ListNodesFragment?
        if (mListNodesFragment == null)
            mListNodesFragment = ListNodesFragment.newInstance(mCurrentGroup, mReadOnly, currentGroupIsASearch)

        // Attach fragment to content view
        supportFragmentManager.beginTransaction().replace(
                R.id.nodes_list_fragment_container,
                mListNodesFragment!!,
                fragmentTag)
                .commit()

        // Update last access time.
        mCurrentGroup?.touch(modified = false, touchParents = false)

        // To relaunch the activity with ACTION_SEARCH
        if (manageSearchInfoIntent(intent)) {
            startActivity(intent)
        }

        // Add listeners to the add buttons
        addNodeButtonView?.setAddGroupClickListener {
            GroupEditDialogFragment.create(GroupInfo().apply {
                if (mCurrentGroup?.allowAddNoteInGroup == true) {
                    notes = ""
                }
            }).show(supportFragmentManager, GroupEditDialogFragment.TAG_CREATE_GROUP)
        }
        addNodeButtonView?.setAddEntryClickListener {
            mCurrentGroup?.let { currentGroup ->
                EntrySelectionHelper.doSpecialAction(intent,
                        {
                            EntryEditActivity.launch(this@GroupActivity, currentGroup)
                        },
                        {
                            // Search not used
                        },
                        { searchInfo ->
                            EntryEditActivity.launchForSave(this@GroupActivity,
                                    currentGroup, searchInfo)
                            onLaunchActivitySpecialMode()
                        },
                        { searchInfo ->
                            EntryEditActivity.launchForKeyboardSelectionResult(this@GroupActivity,
                                    currentGroup, searchInfo)
                            onLaunchActivitySpecialMode()
                        },
                        { searchInfo, autofillComponent ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                EntryEditActivity.launchForAutofillResult(this@GroupActivity,
                                        autofillComponent,
                                        currentGroup, searchInfo)
                                onLaunchActivitySpecialMode()
                            } else {
                                onCancelSpecialMode()
                            }
                        },
                        { searchInfo ->
                            EntryEditActivity.launchForRegistration(this@GroupActivity,
                                    currentGroup, searchInfo)
                            onLaunchActivitySpecialMode()
                        }
                )
            }
        }

        mDatabase?.let { database ->
            // Search suggestion
            mSearchSuggestionAdapter = SearchEntryCursorAdapter(this, database)

            // Init dialog thread
            mProgressDatabaseTaskProvider?.onActionFinish = { actionTask, result ->

                var oldNodes: List<Node> = ArrayList()
                result.data?.getBundle(OLD_NODES_KEY)?.let { oldNodesBundle ->
                    oldNodes = getListNodesFromBundle(database, oldNodesBundle)
                }
                var newNodes: List<Node> = ArrayList()
                result.data?.getBundle(NEW_NODES_KEY)?.let { newNodesBundle ->
                    newNodes = getListNodesFromBundle(database, newNodesBundle)
                }

                refreshSearchGroup()

                when (actionTask) {
                    ACTION_DATABASE_UPDATE_ENTRY_TASK -> {
                        if (result.isSuccess) {
                            mListNodesFragment?.updateNodes(oldNodes, newNodes)
                            EntrySelectionHelper.doSpecialAction(intent,
                                    {
                                        // Standard not used after task
                                    },
                                    {
                                        // Search not used
                                    },
                                    {
                                        // Save not used
                                    },
                                    {
                                        try {
                                            val entry = newNodes[0] as Entry
                                            entrySelectedForKeyboardSelection(entry)
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Unable to perform action for keyboard selection after entry update", e)
                                        }
                                    },
                                    { _, _ ->
                                        try {
                                            val entry = newNodes[0] as Entry
                                            entrySelectedForAutofillSelection(entry)
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Unable to perform action for autofill selection after entry update", e)
                                        }
                                    },
                                    {
                                        // Not use
                                    }
                            )
                        }
                    }
                    ACTION_DATABASE_UPDATE_GROUP_TASK -> {
                        if (result.isSuccess) {
                            mListNodesFragment?.updateNodes(oldNodes, newNodes)
                        }
                    }
                    ACTION_DATABASE_CREATE_GROUP_TASK,
                    ACTION_DATABASE_COPY_NODES_TASK,
                    ACTION_DATABASE_MOVE_NODES_TASK -> {
                        if (result.isSuccess) {
                            mListNodesFragment?.addNodes(newNodes)
                        }
                    }
                    ACTION_DATABASE_DELETE_NODES_TASK -> {
                        if (result.isSuccess) {

                            // Rebuild all the list to avoid bug when delete node from sort
                            try {
                                mListNodesFragment?.rebuildList()
                            } catch (e: Exception) {
                                Log.e(TAG, "Unable to rebuild the list after deletion")
                                e.printStackTrace()
                            }

                            // Add trash in views list if it doesn't exists
                            if (database.isRecycleBinEnabled) {
                                val recycleBin = database.recycleBin
                                val currentGroup = mCurrentGroup
                                if (currentGroup != null && recycleBin != null
                                        && currentGroup != recycleBin) {
                                    // Recycle bin already here, simply update it
                                    if (mListNodesFragment?.contains(recycleBin) == true) {
                                        mListNodesFragment?.updateNode(recycleBin)
                                    }
                                    // Recycle bin not here, verify if parents are similar to add it
                                    else if (currentGroup == recycleBin.parent) {
                                        mListNodesFragment?.addNode(recycleBin)
                                    }
                                }
                            }
                        }
                    }
                    ACTION_DATABASE_RELOAD_TASK -> {
                        // Reload the current activity
                        if (result.isSuccess) {
                            reload()
                        } else {
                            this.showActionErrorIfNeeded(result)
                            finish()
                        }
                    }
                }

                coordinatorLayout?.showActionErrorIfNeeded(result)

                finishNodeAction()

                refreshNumberOfChildren()
            }
        }

        Log.i(TAG, "Finished creating tree")
    }

    private fun reload() {
        // Reload the current activity
        startActivity(intent)
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        mDatabase?.wasReloaded = false
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        intent?.let { intentNotNull ->
            // To transform KEY_SEARCH_INFO in ACTION_SEARCH
            manageSearchInfoIntent(intentNotNull)
            Log.d(TAG, "setNewIntent: $intentNotNull")
            setIntent(intentNotNull)
            if (Intent.ACTION_SEARCH == intentNotNull.action) {
                finishNodeAction()
                // only one instance of search in backstack
                deletePreviousSearchGroup()
                openGroup(retrieveCurrentGroup(intentNotNull, null), true)
            }
        }
    }

    /**
     * Transform the KEY_SEARCH_INFO in ACTION_SEARCH, return true if KEY_SEARCH_INFO was present
     */
    private fun manageSearchInfoIntent(intent: Intent): Boolean {
        // To relaunch the activity as ACTION_SEARCH
        val searchInfo: SearchInfo? = EntrySelectionHelper.retrieveSearchInfoFromIntent(intent)
        val autoSearch = intent.getBooleanExtra(AUTO_SEARCH_KEY, false)
        if (searchInfo != null && autoSearch) {
            intent.action = Intent.ACTION_SEARCH
            intent.putExtra(SearchManager.QUERY, searchInfo.toString())
            return true
        }
        return false
    }

    private fun deletePreviousSearchGroup() {
        // Delete the previous search fragment
        try {
            val searchFragment = supportFragmentManager.findFragmentByTag(SEARCH_FRAGMENT_TAG)
            if (searchFragment != null) {
                if (supportFragmentManager
                                .popBackStackImmediate(SEARCH_FRAGMENT_TAG,
                                        FragmentManager.POP_BACK_STACK_INCLUSIVE))
                    supportFragmentManager.beginTransaction().remove(searchFragment).commit()
            }
        } catch (exception: Exception) {
            Log.e(TAG, "unable to remove previous search fragment", exception)
        }
    }

    private fun openGroup(group: Group?, isASearch: Boolean) {
        // Check TimeoutHelper
        TimeoutHelper.checkTimeAndLockIfTimeoutOrResetTimeout(this, mDatabase) {
            // Open a group in a new fragment
            val newListNodeFragment = ListNodesFragment.newInstance(group, mReadOnly, isASearch)
            val fragmentTransaction = supportFragmentManager.beginTransaction()
            // Different animation
            val fragmentTag: String = if (isASearch) {
                fragmentTransaction.setCustomAnimations(R.anim.slide_in_top, R.anim.slide_out_bottom,
                        R.anim.slide_in_bottom, R.anim.slide_out_top)
                SEARCH_FRAGMENT_TAG
            } else {
                fragmentTransaction.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left,
                        R.anim.slide_in_left, R.anim.slide_out_right)
                LIST_NODES_FRAGMENT_TAG
            }

            fragmentTransaction.replace(R.id.nodes_list_fragment_container,
                    newListNodeFragment,
                    fragmentTag)
            fragmentTransaction.addToBackStack(fragmentTag)
            fragmentTransaction.commit()

            if (mSpecialMode != SpecialMode.DEFAULT)
                mSelectionModeCountBackStack++

            // Update last access time.
            group?.touch(modified = false, touchParents = false)

            mListNodesFragment = newListNodeFragment
            mCurrentGroup = group
            assignGroupViewElements()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        mCurrentGroup?.let {
            outState.putParcelable(GROUP_ID_KEY, it.nodeId)
        }
        mOldGroupToUpdate?.let {
            outState.putParcelable(OLD_GROUP_TO_UPDATE_KEY, it)
        }
        outState.putBoolean(REQUEST_STARTUP_SEARCH_KEY, mRequestStartupSearch)
        super.onSaveInstanceState(outState)
    }

    private fun refreshSearchGroup() {
        deletePreviousSearchGroup()
        if (mCurrentGroup?.isVirtual == true)
            openGroup(retrieveCurrentGroup(intent, null), true)
    }

    private fun retrieveCurrentGroup(intent: Intent, savedInstanceState: Bundle?): Group? {
        // Force read only if the database is like that
        mReadOnly = mDatabase?.isReadOnly == true || mReadOnly

        // If it's a search
        if (Intent.ACTION_SEARCH == intent.action) {
            val searchString = intent.getStringExtra(SearchManager.QUERY)?.trim { it <= ' ' } ?: ""
            return mDatabase?.createVirtualGroupFromSearch(searchString,
                    PreferencesUtil.omitBackup(this))
        }
        // else a real group
        else {
            var pwGroupId: NodeId<*>? = null
            if (savedInstanceState != null && savedInstanceState.containsKey(GROUP_ID_KEY)) {
                pwGroupId = savedInstanceState.getParcelable(GROUP_ID_KEY)
            } else {
                if (getIntent() != null)
                    pwGroupId = intent.getParcelableExtra(GROUP_ID_KEY)
            }

            Log.w(TAG, "Creating tree view")
            val currentGroup: Group?
            currentGroup = if (pwGroupId == null) {
                mRootGroup
            } else {
                mDatabase?.getGroupById(pwGroupId)
            }

            return currentGroup
        }
    }

    private fun assignGroupViewElements() {
        // Assign title
        if (mCurrentGroup != null) {
            val title = mCurrentGroup?.title
            if (title != null && title.isNotEmpty()) {
                if (groupNameView != null) {
                    groupNameView?.text = title
                    groupNameView?.invalidate()
                }
            } else {
                if (groupNameView != null) {
                    groupNameView?.text = getText(R.string.root)
                    groupNameView?.invalidate()
                }
            }
        }

        if (mCurrentGroup?.isVirtual == true) {
            searchTitleView?.visibility = View.VISIBLE
            if (toolbar != null) {
                toolbar?.navigationIcon = null
            }
            iconView?.visibility = View.GONE
        } else {
            searchTitleView?.visibility = View.GONE
            // Assign the group icon depending of IconPack or custom icon
            iconView?.visibility = View.VISIBLE
            mCurrentGroup?.let { currentGroup ->
                iconView?.let { imageView ->
                    mDatabase?.iconDrawableFactory?.assignDatabaseIcon(imageView, currentGroup.icon, mIconColor)
                }

                if (toolbar != null) {
                    if (mCurrentGroup?.containsParent() == true)
                        toolbar?.setNavigationIcon(R.drawable.ic_arrow_up_white_24dp)
                    else {
                        toolbar?.navigationIcon = null
                    }
                }
            }
        }

        // Assign number of children
        refreshNumberOfChildren()

        // Hide button
        initAddButton()
    }

    private fun initAddButton() {
        addNodeButtonView?.apply {
            closeButtonIfOpen()
            // To enable add button
            val addGroupEnabled = !mReadOnly && mCurrentGroup?.isVirtual != true
            var addEntryEnabled = !mReadOnly && mCurrentGroup?.isVirtual != true
            mCurrentGroup?.let {
                if (!it.allowAddEntryIfIsRoot)
                    addEntryEnabled = it != mRootGroup && addEntryEnabled
            }
            enableAddGroup(addGroupEnabled)
            enableAddEntry(addEntryEnabled)
            if (mCurrentGroup?.isVirtual == true)
                hideButton()
            else if (actionNodeMode == null)
                showButton()
        }
    }

    private fun refreshNumberOfChildren() {
        numberChildrenView?.apply {
            if (PreferencesUtil.showNumberEntries(context)) {
                text = mCurrentGroup?.getNumberOfChildEntries(Group.ChildFilter.getDefaults(context))?.toString() ?: ""
                visibility = View.VISIBLE
            } else {
                visibility = View.GONE
            }
        }
    }

    override fun onScrolled(dy: Int) {
        if (actionNodeMode == null)
            addNodeButtonView?.hideOrShowButtonOnScrollListener(dy)
    }

    override fun onNodeClick(node: Node) {
        when (node.type) {
            Type.GROUP -> try {
                // Open child group
                openGroup(node as Group, false)
            } catch (e: ClassCastException) {
                Log.e(TAG, "Node can't be cast in Group")
            }

            Type.ENTRY -> try {
                val entryVersioned = node as Entry
                EntrySelectionHelper.doSpecialAction(intent,
                        {
                            EntryActivity.launch(this@GroupActivity, entryVersioned, mReadOnly)
                        },
                        {
                            // Nothing here, a search is simply performed
                        },
                        { searchInfo ->
                            if (!mReadOnly)
                                entrySelectedForSave(entryVersioned, searchInfo)
                            else
                                finish()
                        },
                        { searchInfo ->
                            // Recheck search, only to fix #783 because workflow allows to open multiple search elements
                            SearchHelper.checkAutoSearchInfo(this,
                                    mDatabase!!,
                                    searchInfo,
                                    { _ ->
                                        // Item in search, don't save
                                        entrySelectedForKeyboardSelection(entryVersioned)
                                    },
                                    {
                                        // Item not found, save it if required
                                        if (!mReadOnly
                                                && searchInfo != null
                                                && PreferencesUtil.isKeyboardSaveSearchInfoEnable(this@GroupActivity)) {
                                            updateEntryWithSearchInfo(entryVersioned, searchInfo)
                                        } else {
                                            entrySelectedForKeyboardSelection(entryVersioned)
                                        }
                                    },
                                    {
                                        // Normally not append
                                        finish()
                                    }
                            )
                        },
                        { searchInfo, _ ->
                            if (!mReadOnly
                                    && searchInfo != null
                                    && PreferencesUtil.isAutofillSaveSearchInfoEnable(this@GroupActivity)) {
                                updateEntryWithSearchInfo(entryVersioned, searchInfo)
                            } else {
                                entrySelectedForAutofillSelection(entryVersioned)
                            }
                        },
                        { registerInfo ->
                            if (!mReadOnly)
                                entrySelectedForRegistration(entryVersioned, registerInfo)
                            else
                                finish()
                        })
            } catch (e: ClassCastException) {
                Log.e(TAG, "Node can't be cast in Entry")
            }
        }
    }

    private fun entrySelectedForSave(entry: Entry, searchInfo: SearchInfo) {
        rebuildListNodes()
        // Save to update the entry
        EntryEditActivity.launchForSave(this@GroupActivity,
                entry, searchInfo)
        onLaunchActivitySpecialMode()
    }

    private fun entrySelectedForKeyboardSelection(entry: Entry) {
        rebuildListNodes()
        // Populate Magikeyboard with entry
        mDatabase?.let { database ->
            populateKeyboardAndMoveAppToBackground(this,
                    entry.getEntryInfo(database),
                    intent)
        }
        onValidateSpecialMode()
    }

    private fun entrySelectedForAutofillSelection(entry: Entry) {
        // Build response with the entry selected
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mDatabase != null) {
            mDatabase?.let { database ->
                AutofillHelper.buildResponseAndSetResult(this,
                        database,
                        entry.getEntryInfo(database))
            }
        }
        onValidateSpecialMode()
    }

    private fun entrySelectedForRegistration(entry: Entry, registerInfo: RegisterInfo?) {
        rebuildListNodes()
        // Registration to update the entry
        EntryEditActivity.launchForRegistration(this@GroupActivity,
                entry, registerInfo)
        onLaunchActivitySpecialMode()
    }

    private fun updateEntryWithSearchInfo(entry: Entry, searchInfo: SearchInfo) {
        val newEntry = Entry(entry)
        newEntry.setEntryInfo(mDatabase, newEntry.getEntryInfo(mDatabase, true).apply {
            saveSearchInfo(mDatabase, searchInfo)
        })
        // In selection mode, it's forced read-only, so update not allowed
        mProgressDatabaseTaskProvider?.startDatabaseUpdateEntry(
                entry,
                newEntry,
                !mReadOnly && mAutoSaveEnable
        )
    }

    override fun onDateSet(datePicker: DatePicker?, year: Int, month: Int, day: Int) {
        // To fix android 4.4 issue
        // https://stackoverflow.com/questions/12436073/datepicker-ondatechangedlistener-called-twice
        if (datePicker?.isShown == true) {
            val groupEditFragment = supportFragmentManager.findFragmentByTag(GroupEditDialogFragment.TAG_CREATE_GROUP) as? GroupEditDialogFragment
            groupEditFragment?.getExpiryTime()?.date?.let { expiresDate ->
                groupEditFragment.setExpiryTime(DateInstant(DateTime(expiresDate)
                        .withYear(year)
                        .withMonthOfYear(month + 1)
                        .withDayOfMonth(day)
                        .toDate()))
                // Launch the time picker
                val dateTime = DateTime(expiresDate)
                val defaultHour = dateTime.hourOfDay
                val defaultMinute = dateTime.minuteOfHour
                TimePickerFragment.getInstance(defaultHour, defaultMinute)
                        .show(supportFragmentManager, "TimePickerFragment")
            }
        }
    }

    override fun onTimeSet(view: TimePicker?, hours: Int, minutes: Int) {
        val groupEditFragment = supportFragmentManager.findFragmentByTag(GroupEditDialogFragment.TAG_CREATE_GROUP) as? GroupEditDialogFragment
        groupEditFragment?.getExpiryTime()?.date?.let { expiresDate ->
            // Save the date
            groupEditFragment.setExpiryTime(
                    DateInstant(DateTime(expiresDate)
                    .withHourOfDay(hours)
                    .withMinuteOfHour(minutes)
                    .toDate()))
        }
    }

    private fun finishNodeAction() {
        actionNodeMode?.finish()
    }

    override fun onNodeSelected(nodes: List<Node>): Boolean {
        if (nodes.isNotEmpty()) {
            if (actionNodeMode == null || toolbarAction?.getSupportActionModeCallback() == null) {
                mListNodesFragment?.actionNodesCallback(nodes, this, object: ActionMode.Callback {
                    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                        return true
                    }
                    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                        return true
                    }
                    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                        return false
                    }
                    override fun onDestroyActionMode(mode: ActionMode?) {
                        actionNodeMode = null
                        addNodeButtonView?.showButton()
                    }
                })?.let {
                    actionNodeMode = toolbarAction?.startSupportActionMode(it)
                }
            } else {
                actionNodeMode?.invalidate()
            }
            addNodeButtonView?.hideButton()
        } else {
            finishNodeAction()
        }
        return true
    }

    override fun onOpenMenuClick(node: Node): Boolean {
        finishNodeAction()
        onNodeClick(node)
        return true
    }

    override fun onEditMenuClick(node: Node): Boolean {
        finishNodeAction()
        when (node.type) {
            Type.GROUP -> {
                mOldGroupToUpdate = node as Group
                GroupEditDialogFragment.update(mOldGroupToUpdate!!.getGroupInfo())
                        .show(supportFragmentManager,
                                GroupEditDialogFragment.TAG_CREATE_GROUP)
            }
            Type.ENTRY -> EntryEditActivity.launch(this@GroupActivity, node as Entry)
        }
        return true
    }

    override fun onCopyMenuClick(nodes: List<Node>): Boolean {
        actionNodeMode?.invalidate()

        // Nothing here fragment calls onPasteMenuClick internally
        return true
    }

    override fun onMoveMenuClick(nodes: List<Node>): Boolean {
        actionNodeMode?.invalidate()

        // Nothing here fragment calls onPasteMenuClick internally
        return true
    }

    override fun onPasteMenuClick(pasteMode: ListNodesFragment.PasteMode?,
                                  nodes: List<Node>): Boolean {
        when (pasteMode) {
            ListNodesFragment.PasteMode.PASTE_FROM_COPY -> {
                // Copy
                mCurrentGroup?.let { newParent ->
                    mProgressDatabaseTaskProvider?.startDatabaseCopyNodes(
                            nodes,
                            newParent,
                            !mReadOnly && mAutoSaveEnable
                    )
                }
            }
            ListNodesFragment.PasteMode.PASTE_FROM_MOVE -> {
                // Move
                mCurrentGroup?.let { newParent ->
                    mProgressDatabaseTaskProvider?.startDatabaseMoveNodes(
                            nodes,
                            newParent,
                            !mReadOnly && mAutoSaveEnable
                    )
                }
            }
            else -> {}
        }
        finishNodeAction()
        return true
    }

    private fun eachNodeRecyclable(nodes: List<Node>): Boolean {
        mDatabase?.let { database ->
            return nodes.find { node ->
                var cannotRecycle = true
                if (node is Entry) {
                    cannotRecycle = !database.canRecycle(node)
                } else if (node is Group) {
                    cannotRecycle = !database.canRecycle(node)
                }
                cannotRecycle
            } == null
        }
        return false
    }

    private fun deleteNodes(nodes: List<Node>, recycleBin: Boolean = false): Boolean {
        mDatabase?.let { database ->

            // If recycle bin enabled, ensure it exists
            if (database.isRecycleBinEnabled) {
                database.ensureRecycleBinExists(resources)
            }

            // If recycle bin enabled and not in recycle bin, move in recycle bin
            if (eachNodeRecyclable(nodes)) {
                mProgressDatabaseTaskProvider?.startDatabaseDeleteNodes(
                        nodes,
                        !mReadOnly && mAutoSaveEnable
                )
            }
            // else open the dialog to confirm deletion
            else {
                val deleteNodesDialogFragment: DeleteNodesDialogFragment =
                        if (recycleBin) {
                            EmptyRecycleBinDialogFragment.getInstance(nodes)
                        } else {
                            DeleteNodesDialogFragment.getInstance(nodes)
                        }
                deleteNodesDialogFragment.show(supportFragmentManager, "deleteNodesDialogFragment")
            }
            finishNodeAction()
        }
        return true
    }

    override fun onDeleteMenuClick(nodes: List<Node>): Boolean {
        return deleteNodes(nodes)
    }

    override fun permanentlyDeleteNodes(nodes: List<Node>) {
        mProgressDatabaseTaskProvider?.startDatabaseDeleteNodes(
                nodes,
                !mReadOnly && mAutoSaveEnable
        )
    }

    override fun onResume() {
        super.onResume()

        if (mDatabase?.wasReloaded == true) {
            reload()
        }
        // Show the lock button
        lockView?.visibility = if (PreferencesUtil.showLockDatabaseButton(this)) {
            View.VISIBLE
        } else {
            View.GONE
        }
        // Refresh the elements
        assignGroupViewElements()
        // Refresh suggestions to change preferences
        mSearchSuggestionAdapter?.reInit(this)
        // Padding if lock button visible
        toolbarAction?.updateLockPaddingLeft()
    }

    override fun onPause() {
        super.onPause()

        finishNodeAction()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {

        val inflater = menuInflater
        inflater.inflate(R.menu.search, menu)
        inflater.inflate(R.menu.database, menu)
        if (mReadOnly) {
            menu.findItem(R.id.menu_save_database)?.isVisible = false
        }
        if (mSpecialMode == SpecialMode.DEFAULT) {
            MenuUtil.defaultMenuInflater(inflater, menu)
        } else {
            menu.findItem(R.id.menu_reload_database)?.isVisible = false
        }

        // Menu for recycle bin
        if (!mReadOnly
                && mDatabase?.isRecycleBinEnabled == true
                && mDatabase?.recycleBin == mCurrentGroup) {
            inflater.inflate(R.menu.recycle_bin, menu)
        }

        // Get the SearchView and set the searchable configuration
        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager?

        menu.findItem(R.id.menu_search)?.let {
            val searchView = it.actionView as SearchView?
            searchView?.apply {
                (searchManager?.getSearchableInfo(
                        ComponentName(this@GroupActivity, GroupActivity::class.java)))?.let { searchableInfo ->
                    setSearchableInfo(searchableInfo)
                }
                setIconifiedByDefault(false) // Do not iconify the widget; expand it by default
                suggestionsAdapter = mSearchSuggestionAdapter
                setOnSuggestionListener(object : SearchView.OnSuggestionListener {
                    override fun onSuggestionClick(position: Int): Boolean {
                        mSearchSuggestionAdapter?.let { searchAdapter ->
                            searchAdapter.getEntryFromPosition(position)?.let { entry ->
                                onNodeClick(entry)
                            }
                        }
                        return true
                    }

                    override fun onSuggestionSelect(position: Int): Boolean {
                        return true
                    }
                })
            }
            // Expand the search view if defined in settings
            if (mRequestStartupSearch
                    && PreferencesUtil.automaticallyFocusSearch(this@GroupActivity)) {
                // To request search only one time
                mRequestStartupSearch = false
                it.expandActionView()
            }
        }

        super.onCreateOptionsMenu(menu)

        // Launch education screen
        Handler(Looper.getMainLooper()).post { performedNextEducation(GroupActivityEducation(this), menu) }

        return true
    }

    private fun performedNextEducation(groupActivityEducation: GroupActivityEducation,
                                       menu: Menu) {

        // If no node, show education to add new one
        val addNodeButtonEducationPerformed = mListNodesFragment != null
                && mListNodesFragment!!.isEmpty
                && addNodeButtonView?.addButtonView != null
                && addNodeButtonView!!.isEnable
                && groupActivityEducation.checkAndPerformedAddNodeButtonEducation(
                        addNodeButtonView?.addButtonView!!,
                        {
                            addNodeButtonView?.openButtonIfClose()
                        },
                        {
                            performedNextEducation(groupActivityEducation, menu)
                        }
                )
        if (!addNodeButtonEducationPerformed) {

            val searchMenuEducationPerformed = toolbar != null
                    && toolbar!!.findViewById<View>(R.id.menu_search) != null
                    && groupActivityEducation.checkAndPerformedSearchMenuEducation(
                    toolbar!!.findViewById(R.id.menu_search),
                    {
                        menu.findItem(R.id.menu_search).expandActionView()
                    },
                    {
                        performedNextEducation(groupActivityEducation, menu)
                    })

            if (!searchMenuEducationPerformed) {

                val sortMenuEducationPerformed = toolbar != null
                        && toolbar!!.findViewById<View>(R.id.menu_sort) != null
                        && groupActivityEducation.checkAndPerformedSortMenuEducation(
                        toolbar!!.findViewById(R.id.menu_sort),
                        {
                            onOptionsItemSelected(menu.findItem(R.id.menu_sort))
                        },
                        {
                            performedNextEducation(groupActivityEducation, menu)
                        })

                if (!sortMenuEducationPerformed) {
                    // lockMenuEducationPerformed
                    val lockButtonView = findViewById<View>(R.id.lock_button_icon)
                    lockButtonView != null
                            && groupActivityEducation.checkAndPerformedLockMenuEducation(lockButtonView,
                            {
                                lockAndExit()
                            },
                            {
                                performedNextEducation(groupActivityEducation, menu)
                            })
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
            R.id.menu_search ->
                //onSearchRequested();
                return true
            R.id.menu_save_database -> {
                mProgressDatabaseTaskProvider?.startDatabaseSave(!mReadOnly)
                return true
            }
            R.id.menu_reload_database -> {
                mProgressDatabaseTaskProvider?.startDatabaseReload(false)
                return true
            }
            R.id.menu_empty_recycle_bin -> {
                mCurrentGroup?.getChildren()?.let { listChildren ->
                    // Automatically delete all elements
                    deleteNodes(listChildren, true)
                }
                return true
            }
            else -> {
                // Check the time lock before launching settings
                MenuUtil.onDefaultMenuOptionsItemSelected(this, item, mReadOnly, true)
                return super.onOptionsItemSelected(item)
            }
        }
    }

    override fun isValidGroupName(name: String): GroupEditDialogFragment.Error {
        if (name.isEmpty()) {
            return GroupEditDialogFragment.Error(true, R.string.error_no_name)
        }
        if (mDatabase?.groupNamesNotAllowed?.find { it.equals(name, ignoreCase = true) } != null) {
            return GroupEditDialogFragment.Error(true, R.string.error_word_reserved)
        }
        return GroupEditDialogFragment.Error(false, null)
    }

    override fun approveEditGroup(action: GroupEditDialogFragment.EditGroupDialogAction,
                                  groupInfo: GroupInfo) {

        if (groupInfo.title.isNotEmpty()) {
            when (action) {
                GroupEditDialogFragment.EditGroupDialogAction.CREATION -> {
                    // If group creation
                    mCurrentGroup?.let { currentGroup ->
                        // Build the group
                        mDatabase?.createGroup()?.let { newGroup ->
                            newGroup.setGroupInfo(groupInfo)
                            // Not really needed here because added in runnable but safe
                            newGroup.parent = currentGroup

                            mProgressDatabaseTaskProvider?.startDatabaseCreateGroup(
                                    newGroup,
                                    currentGroup,
                                    !mReadOnly && mAutoSaveEnable
                            )
                        }
                    }
                }
                GroupEditDialogFragment.EditGroupDialogAction.UPDATE -> {
                    // If update add new elements
                    mOldGroupToUpdate?.let { oldGroupToUpdate ->
                        val updateGroup = Group(oldGroupToUpdate).let { updateGroup ->
                            updateGroup.apply {
                                // WARNING remove parent and children to keep memory
                                removeParent()
                                removeChildren()
                                this.setGroupInfo(groupInfo)
                            }
                        }
                        // If group updated save it in the database
                        mProgressDatabaseTaskProvider?.startDatabaseUpdateGroup(
                                oldGroupToUpdate,
                                updateGroup,
                                !mReadOnly && mAutoSaveEnable
                        )
                    }
                }
                else -> {}
            }
        }
    }

    override fun cancelEditGroup(action: GroupEditDialogFragment.EditGroupDialogAction,
                                 groupInfo: GroupInfo) {
        // Do nothing here
    }

    override fun onSortSelected(sortNodeEnum: SortNodeEnum, sortNodeParameters: SortNodeEnum.SortNodeParameters) {
        mListNodesFragment?.onSortSelected(sortNodeEnum, sortNodeParameters)
    }

    override fun startActivity(intent: Intent) {
        // Get the intent, verify the action and get the query
        if (Intent.ACTION_SEARCH == intent.action) {
            // manually launch the same search activity
            val searchIntent = getIntent().apply {
                // add query to the Intent Extras
                action = Intent.ACTION_SEARCH
                putExtra(SearchManager.QUERY, intent.getStringExtra(SearchManager.QUERY))
            }
            setIntent(searchIntent)
            onNewIntent(searchIntent)
        } else {
            super.startActivity(intent)
        }
    }

    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        /*
         * ACTION_SEARCH automatically forces a new task. This occurs when you open a kdb file in
         * another app such as Files or GoogleDrive and then Search for an entry. Here we remove the
         * FLAG_ACTIVITY_NEW_TASK flag bit allowing search to open it's activity in the current task.
         */
        if (Intent.ACTION_SEARCH == intent.action) {
            var flags = intent.flags
            flags = flags and Intent.FLAG_ACTIVITY_NEW_TASK.inv()
            intent.flags = flags
        }

        super.startActivityForResult(intent, requestCode, options)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // To create tree dialog for icon
        IconPickerActivity.onActivityResult(requestCode, resultCode, data) { icon ->
            (supportFragmentManager
                    .findFragmentByTag(GroupEditDialogFragment.TAG_CREATE_GROUP) as GroupEditDialogFragment)
                    .setIcon(icon)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AutofillHelper.onActivityResultSetResultAndFinish(this, requestCode, resultCode, data)
        }

        // Directly used the onActivityResult in fragment
        mListNodesFragment?.onActivityResult(requestCode, resultCode, data)
    }

    private fun rebuildListNodes() {
        mListNodesFragment = supportFragmentManager.findFragmentByTag(LIST_NODES_FRAGMENT_TAG) as ListNodesFragment?
        // to refresh fragment
        try {
            mListNodesFragment?.rebuildList()
        } catch (e: Exception) {
            e.printStackTrace()
            coordinatorLayout?.let { coordinatorLayout ->
                Snackbar.make(coordinatorLayout,
                        R.string.error_rebuild_list,
                        Snackbar.LENGTH_LONG).asError().show()
            }
        }
        mCurrentGroup = mListNodesFragment?.mainGroup
        // Remove search in intent
        deletePreviousSearchGroup()
        if (Intent.ACTION_SEARCH == intent.action) {
            intent.action = Intent.ACTION_DEFAULT
            intent.removeExtra(SearchManager.QUERY)
        }
        assignGroupViewElements()
    }

    override fun onBackPressed() {
        if (mListNodesFragment?.nodeActionSelectionMode == true) {
            finishNodeAction()
        } else {
            // Normal way when we are not in root
            if (mRootGroup != null && mRootGroup != mCurrentGroup) {
                super.onRegularBackPressed()
                rebuildListNodes()
            }
            // Else in root, lock if needed
            else {
                intent.removeExtra(AUTO_SEARCH_KEY)
                EntrySelectionHelper.removeModesFromIntent(intent)
                EntrySelectionHelper.removeInfoFromIntent(intent)
                if (PreferencesUtil.isLockDatabaseWhenBackButtonOnRootClicked(this)) {
                    lockAndExit()
                    super.onRegularBackPressed()
                } else {
                    backToTheAppCaller()
                }
            }
        }
    }

    private fun removeFragmentHistory() {
        val fragmentManager = supportFragmentManager
        if (mSelectionModeCountBackStack > 0) {
            for (selectionMode in 0 .. mSelectionModeCountBackStack) {
                fragmentManager.popBackStack()
            }
        }
        // Reinit the counter for navigation history
        mSelectionModeCountBackStack = 0
    }

    override fun onValidateSpecialMode() {
        removeFragmentHistory()
        super.onValidateSpecialMode()
    }

    override fun onCancelSpecialMode() {
        removeFragmentHistory()
        super.onCancelSpecialMode()
    }

    companion object {

        private val TAG = GroupActivity::class.java.name

        private const val REQUEST_STARTUP_SEARCH_KEY = "REQUEST_STARTUP_SEARCH_KEY"
        private const val GROUP_ID_KEY = "GROUP_ID_KEY"
        private const val LIST_NODES_FRAGMENT_TAG = "LIST_NODES_FRAGMENT_TAG"
        private const val SEARCH_FRAGMENT_TAG = "SEARCH_FRAGMENT_TAG"
        private const val OLD_GROUP_TO_UPDATE_KEY = "OLD_GROUP_TO_UPDATE_KEY"
        private const val AUTO_SEARCH_KEY = "AUTO_SEARCH_KEY"

        private fun buildIntent(context: Context,
                                group: Group?,
                                readOnly: Boolean,
                                intentBuildLauncher: (Intent) -> Unit) {
            val intent = Intent(context, GroupActivity::class.java)
            if (group != null) {
                intent.putExtra(GROUP_ID_KEY, group.nodeId)
            }
            ReadOnlyHelper.putReadOnlyInIntent(intent, readOnly)
            intentBuildLauncher.invoke(intent)
        }

        private fun checkTimeAndBuildIntent(activity: Activity,
                                            group: Group?,
                                            readOnly: Boolean,
                                            intentBuildLauncher: (Intent) -> Unit) {
            if (TimeoutHelper.checkTimeAndLockIfTimeout(activity)) {
                buildIntent(activity, group, readOnly, intentBuildLauncher)
            }
        }

        private fun checkTimeAndBuildIntent(context: Context,
                                            group: Group?,
                                            readOnly: Boolean,
                                            intentBuildLauncher: (Intent) -> Unit) {
            if (TimeoutHelper.checkTime(context)) {
                buildIntent(context, group, readOnly, intentBuildLauncher)
            }
        }

        /*
         * -------------------------
         * 		Standard Launch
         * -------------------------
         */
        fun launch(context: Context,
                   readOnly: Boolean,
                   autoSearch: Boolean = false) {
            checkTimeAndBuildIntent(context, null, readOnly) { intent ->
                intent.putExtra(AUTO_SEARCH_KEY, autoSearch)
                context.startActivity(intent)
            }
        }

        /*
         * -------------------------
         * 		Search Launch
         * -------------------------
         */
        fun launchForSearchResult(context: Context,
                                  readOnly: Boolean,
                                  searchInfo: SearchInfo,
                                  autoSearch: Boolean = false) {
            checkTimeAndBuildIntent(context, null, readOnly) { intent ->
                intent.putExtra(AUTO_SEARCH_KEY, autoSearch)
                EntrySelectionHelper.addSearchInfoInIntent(
                        intent,
                        searchInfo)
                context.startActivity(intent)
            }
        }

        /*
         * -------------------------
         * 		Search save Launch
         * -------------------------
         */
        fun launchForSaveResult(context: Context,
                                searchInfo: SearchInfo,
                                autoSearch: Boolean = false) {
            checkTimeAndBuildIntent(context, null, false) { intent ->
                intent.putExtra(AUTO_SEARCH_KEY, autoSearch)
                EntrySelectionHelper.startActivityForSaveModeResult(context,
                        intent,
                        searchInfo)
            }
        }

        /*
         * -------------------------
         * 		Keyboard Launch
         * -------------------------
         */
        fun launchForKeyboardSelectionResult(context: Context,
                                             readOnly: Boolean,
                                             searchInfo: SearchInfo? = null,
                                             autoSearch: Boolean = false) {
            checkTimeAndBuildIntent(context, null, readOnly) { intent ->
                intent.putExtra(AUTO_SEARCH_KEY, autoSearch)
                EntrySelectionHelper.startActivityForKeyboardSelectionModeResult(context,
                        intent,
                        searchInfo)
            }
        }

        /*
         * -------------------------
         * 		Autofill Launch
         * -------------------------
         */
        @RequiresApi(api = Build.VERSION_CODES.O)
        fun launchForAutofillResult(activity: Activity,
                                    readOnly: Boolean,
                                    autofillComponent: AutofillComponent,
                                    searchInfo: SearchInfo? = null,
                                    autoSearch: Boolean = false) {
            checkTimeAndBuildIntent(activity, null, readOnly) { intent ->
                intent.putExtra(AUTO_SEARCH_KEY, autoSearch)
                AutofillHelper.startActivityForAutofillResult(activity,
                        intent,
                        autofillComponent,
                        searchInfo)
            }
        }

        /*
         * -------------------------
         * 		Registration Launch
         * -------------------------
         */
        fun launchForRegistration(context: Context,
                                  registerInfo: RegisterInfo? = null) {
            checkTimeAndBuildIntent(context, null, false) { intent ->
                intent.putExtra(AUTO_SEARCH_KEY, false)
                EntrySelectionHelper.startActivityForRegistrationModeResult(context,
                        intent,
                        registerInfo)
            }
        }

        /*
         * -------------------------
         * 		Global Launch
         * -------------------------
         */
        fun launch(activity: Activity,
                   database: Database,
                   readOnly: Boolean,
                   onValidateSpecialMode: () -> Unit,
                   onCancelSpecialMode: () -> Unit,
                   onLaunchActivitySpecialMode: () -> Unit) {
            EntrySelectionHelper.doSpecialAction(activity.intent,
                    {
                        GroupActivity.launch(activity,
                                readOnly,
                                true)
                    },
                    { searchInfo ->
                        SearchHelper.checkAutoSearchInfo(activity,
                                database,
                                searchInfo,
                                { _ ->
                                    // Response is build
                                    GroupActivity.launchForSearchResult(activity,
                                            readOnly,
                                            searchInfo,
                                            true)
                                    onLaunchActivitySpecialMode()
                                },
                                {
                                    // Here no search info found
                                    if (readOnly) {
                                        GroupActivity.launchForSearchResult(activity,
                                                readOnly,
                                                searchInfo,
                                                false)
                                    } else {
                                        GroupActivity.launchForSaveResult(activity,
                                                searchInfo,
                                                false)
                                    }
                                    onLaunchActivitySpecialMode()
                                },
                                {
                                    // Simply close if database not opened, normally not happened
                                    onCancelSpecialMode()
                                }
                        )
                    },
                    { searchInfo ->
                        // Save info used with OTP
                        if (!readOnly) {
                            GroupActivity.launchForSaveResult(activity,
                                    searchInfo,
                                    false)
                            onLaunchActivitySpecialMode()
                        }  else {
                            Toast.makeText(activity.applicationContext,
                                    R.string.autofill_read_only_save,
                                    Toast.LENGTH_LONG)
                                    .show()
                            onCancelSpecialMode()
                        }
                    },
                    { searchInfo ->
                        SearchHelper.checkAutoSearchInfo(activity,
                                database,
                                searchInfo,
                                { items ->
                                    // Response is build
                                    if (items.size == 1) {
                                        populateKeyboardAndMoveAppToBackground(activity,
                                                items[0],
                                                activity.intent)
                                        onValidateSpecialMode()
                                    } else {
                                        // Select the one we want
                                        GroupActivity.launchForKeyboardSelectionResult(activity,
                                                readOnly,
                                                searchInfo,
                                                true)
                                        onLaunchActivitySpecialMode()
                                    }
                                },
                                {
                                    // Here no search info found, disable auto search
                                    GroupActivity.launchForKeyboardSelectionResult(activity,
                                            readOnly,
                                            searchInfo,
                                            false)
                                    onLaunchActivitySpecialMode()
                                },
                                {
                                    // Simply close if database not opened, normally not happened
                                    onCancelSpecialMode()
                                }
                        )
                    },
                    { searchInfo, autofillComponent ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            SearchHelper.checkAutoSearchInfo(activity,
                                    database,
                                    searchInfo,
                                    { items ->
                                        // Response is build
                                        AutofillHelper.buildResponseAndSetResult(activity, database, items)
                                        onValidateSpecialMode()
                                    },
                                    {
                                        // Here no search info found, disable auto search
                                        GroupActivity.launchForAutofillResult(activity,
                                                readOnly,
                                                autofillComponent,
                                                searchInfo,
                                                false)
                                        onLaunchActivitySpecialMode()
                                    },
                                    {
                                        // Simply close if database not opened, normally not happened
                                        onCancelSpecialMode()
                                    }
                            )
                        } else {
                            onCancelSpecialMode()
                        }
                    },
                    { registerInfo ->
                        if (!readOnly) {
                            SearchHelper.checkAutoSearchInfo(activity,
                                    database,
                                    registerInfo?.searchInfo,
                                    { _ ->
                                        // No auto search, it's a registration
                                        GroupActivity.launchForRegistration(activity,
                                                registerInfo)
                                        onLaunchActivitySpecialMode()
                                    },
                                    {
                                        // Here no search info found, disable auto search
                                        GroupActivity.launchForRegistration(activity,
                                                registerInfo)
                                        onLaunchActivitySpecialMode()
                                    },
                                    {
                                        // Simply close if database not opened, normally not happened
                                        onCancelSpecialMode()
                                    }
                            )
                        } else {
                            Toast.makeText(activity.applicationContext,
                                    R.string.autofill_read_only_save,
                                    Toast.LENGTH_LONG)
                                    .show()
                            onCancelSpecialMode()
                        }
                    })
        }
    }
}
