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
package com.kunzisoft.keepass.autofill

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.graphics.BlendMode
import android.graphics.drawable.Icon
import android.os.Build
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.service.autofill.InlinePresentation
import android.util.Log
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.view.inputmethod.InlineSuggestionsRequest
import android.widget.RemoteViews
import android.widget.Toast
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.v1.InlineSuggestionUi
import androidx.core.content.ContextCompat
import com.kunzisoft.keepass.R
import com.kunzisoft.keepass.activities.AutofillLauncherActivity
import com.kunzisoft.keepass.activities.helpers.EntrySelectionHelper
import com.kunzisoft.keepass.activities.helpers.SpecialMode
import com.kunzisoft.keepass.database.element.Database
import com.kunzisoft.keepass.database.element.icon.IconImage
import com.kunzisoft.keepass.model.EntryInfo
import com.kunzisoft.keepass.model.SearchInfo
import com.kunzisoft.keepass.settings.AutofillSettingsActivity
import com.kunzisoft.keepass.settings.PreferencesUtil


@RequiresApi(api = Build.VERSION_CODES.O)
object AutofillHelper {

    private const val AUTOFILL_RESPONSE_REQUEST_CODE = 8165

    private const val EXTRA_ASSIST_STRUCTURE = AutofillManager.EXTRA_ASSIST_STRUCTURE
    const val EXTRA_INLINE_SUGGESTIONS_REQUEST = "com.kunzisoft.keepass.autofill.INLINE_SUGGESTIONS_REQUEST"

    fun retrieveAutofillComponent(intent: Intent?): AutofillComponent? {
        intent?.getParcelableExtra<AssistStructure?>(EXTRA_ASSIST_STRUCTURE)?.let { assistStructure ->
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                AutofillComponent(assistStructure,
                        intent.getParcelableExtra(EXTRA_INLINE_SUGGESTIONS_REQUEST))
            } else {
                AutofillComponent(assistStructure, null)
            }
        }
        return null
    }

    private fun makeEntryTitle(entryInfo: EntryInfo): String {
        if (entryInfo.title.isNotEmpty() && entryInfo.username.isNotEmpty())
            return String.format("%s (%s)", entryInfo.title, entryInfo.username)
        if (entryInfo.title.isNotEmpty())
            return entryInfo.title
        if (entryInfo.url.isNotEmpty())
            return entryInfo.url
        if (entryInfo.username.isNotEmpty())
            return entryInfo.username
        return ""
    }

    private fun newRemoteViews(context: Context,
                               remoteViewsText: String,
                               remoteViewsIcon: IconImage? = null): RemoteViews {
        val presentation = RemoteViews(context.packageName, R.layout.item_autofill_entry)
        presentation.setTextViewText(R.id.autofill_entry_text, remoteViewsText)
        if (remoteViewsIcon != null) {
            try {
                Database.getInstance().iconDrawableFactory.getBitmapFromIcon(context,
                        remoteViewsIcon, ContextCompat.getColor(context, R.color.green))?.let { bitmap ->
                    presentation.setImageViewBitmap(R.id.autofill_entry_icon, bitmap)
                }
            } catch (e: Exception) {
                Log.e(RemoteViews::class.java.name, "Unable to assign icon in remote view", e)
            }
        }
        return presentation
    }

    private fun buildDataset(context: Context,
                              entryInfo: EntryInfo,
                              struct: StructureParser.Result,
                              inlinePresentation: InlinePresentation?): Dataset? {
        val title = makeEntryTitle(entryInfo)
        val views = newRemoteViews(context, title, entryInfo.icon)
        val builder = Dataset.Builder(views)
        builder.setId(entryInfo.id)

        struct.usernameId?.let { usernameId ->
            builder.setValue(usernameId, AutofillValue.forText(entryInfo.username))
        }
        struct.passwordId?.let { password ->
            builder.setValue(password, AutofillValue.forText(entryInfo.password))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            inlinePresentation?.let {
                builder.setInlinePresentation(it)
            }
        }

        return try {
            builder.build()
        } catch (e: IllegalArgumentException) {
            // if not value be set
            null
        }
    }

    /**
     * Method to assign a drawable to a new icon from a database icon
     */
    private fun buildIconFromEntry(context: Context, entryInfo: EntryInfo): Icon? {
        try {
            Database.getInstance().iconDrawableFactory.getBitmapFromIcon(context,
                    entryInfo.icon, ContextCompat.getColor(context, R.color.green))?.let { bitmap ->
                return Icon.createWithBitmap(bitmap)
            }
        } catch (e: Exception) {
            Log.e(RemoteViews::class.java.name, "Unable to assign icon in remote view", e)
        }
        return null
    }

    @RequiresApi(Build.VERSION_CODES.R)
    @SuppressLint("RestrictedApi")
    private fun buildInlinePresentationForEntry(context: Context,
                                                inlineSuggestionsRequest: InlineSuggestionsRequest,
                                                positionItem: Int,
                                                entryInfo: EntryInfo): InlinePresentation? {
        val inlinePresentationSpecs = inlineSuggestionsRequest.inlinePresentationSpecs
        val maxSuggestion = inlineSuggestionsRequest.maxSuggestionCount

        if (positionItem <= maxSuggestion-1
                && inlinePresentationSpecs.size > positionItem) {
            val inlinePresentationSpec = inlinePresentationSpecs[positionItem]

            // Make sure that the IME spec claims support for v1 UI template.
            val imeStyle = inlinePresentationSpec.style
            if (!UiVersions.getVersions(imeStyle).contains(UiVersions.INLINE_UI_VERSION_1))
                return null

            // Build the content for IME UI
            val pendingIntent = PendingIntent.getActivity(context,
                    0,
                    Intent(context, AutofillSettingsActivity::class.java),
                    0)
            return InlinePresentation(
                    InlineSuggestionUi.newContentBuilder(pendingIntent).apply {
                        setContentDescription(context.getString(R.string.autofill_sign_in_prompt))
                        setTitle(entryInfo.title)
                        setSubtitle(entryInfo.username)
                        setStartIcon(Icon.createWithResource(context, R.mipmap.ic_launcher_round).apply {
                            setTintBlendMode(BlendMode.DST)
                        })
                        buildIconFromEntry(context, entryInfo)?.let { icon ->
                            setEndIcon(icon.apply {
                                setTintBlendMode(BlendMode.DST)
                            })
                        }
                    }.build().slice, inlinePresentationSpec, false)
        }
        return null
    }

    @RequiresApi(Build.VERSION_CODES.R)
    @SuppressLint("RestrictedApi")
    private fun buildInlinePresentationForManualSelection(context: Context,
                                                          inlinePresentationSpec: InlinePresentationSpec,
                                                          pendingIntent: PendingIntent): InlinePresentation? {
        // Make sure that the IME spec claims support for v1 UI template.
        val imeStyle = inlinePresentationSpec.style
        if (!UiVersions.getVersions(imeStyle).contains(UiVersions.INLINE_UI_VERSION_1))
            return null

        // Build the content for IME UI
        return InlinePresentation(
                InlineSuggestionUi.newContentBuilder(pendingIntent).apply {
                    setContentDescription(context.getString(R.string.autofill_sign_in_prompt))
                    setTitle(context.getString(R.string.autofill_manual_selection_prompt))
                    setStartIcon(Icon.createWithResource(context, R.mipmap.ic_launcher_round).apply {
                        setTintBlendMode(BlendMode.DST)
                    })
                }.build().slice, inlinePresentationSpec, false)
    }

    fun buildResponse(context: Context,
                      entriesInfo: List<EntryInfo>,
                      parseResult: StructureParser.Result,
                      inlineSuggestionsRequest: InlineSuggestionsRequest?): FillResponse {
        val responseBuilder = FillResponse.Builder()
        // Add Header
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val packageName = context.packageName
            parseResult.webDomain?.let { webDomain ->
                responseBuilder.setHeader(RemoteViews(packageName, R.layout.item_autofill_web_domain).apply {
                    setTextViewText(R.id.autofill_web_domain_text, webDomain)
                })
            } ?: kotlin.run {
                parseResult.applicationId?.let { applicationId ->
                    responseBuilder.setHeader(RemoteViews(packageName, R.layout.item_autofill_app_id).apply {
                        setTextViewText(R.id.autofill_app_id_text, applicationId)
                    })
                }
            }
        }

        // Add inline suggestion for new IME and dataset
        var numberInlineSuggestions = entriesInfo.size
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            inlineSuggestionsRequest?.let {
                numberInlineSuggestions = minOf(inlineSuggestionsRequest.maxSuggestionCount, entriesInfo.size)
                if (PreferencesUtil.isAutofillManualSelectionEnable(context)) {
                    if (entriesInfo.size >= inlineSuggestionsRequest.maxSuggestionCount) {
                        --numberInlineSuggestions
                    }
                }
            }

        }
        entriesInfo.forEachIndexed { _, entry ->
            val inlinePresentation = if (numberInlineSuggestions > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                inlineSuggestionsRequest?.let {
                    buildInlinePresentationForEntry(context, inlineSuggestionsRequest, numberInlineSuggestions--, entry)
                }
            } else {
                null
            }
            responseBuilder.addDataset(buildDataset(context, entry, parseResult, inlinePresentation))
        }

        if (PreferencesUtil.isAutofillManualSelectionEnable(context)) {
            val manualSelectionView = newRemoteViews(context, context.getString(R.string.autofill_manual_selection_prompt), null)
            val pendingIntent = AutofillLauncherActivity.getPendingIntentForSelection(context,
                    null, inlineSuggestionsRequest)
            val builder = Dataset.Builder(manualSelectionView)
            parseResult.usernameId?.let { autofillId ->
                builder.setValue(autofillId, null)
            }
            parseResult.passwordId?.let { autofillId ->
                builder.setValue(autofillId, null)
            }
            builder.setAuthentication(pendingIntent.intentSender)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                inlineSuggestionsRequest?.let {
                    val inlinePresentationSpec = inlineSuggestionsRequest.inlinePresentationSpecs[0]
                    val inlinePresentation = buildInlinePresentationForManualSelection(context, inlinePresentationSpec, pendingIntent)
                    inlinePresentation?.let {
                        builder.setInlinePresentation(it)
                    }
                }
            }
            responseBuilder.addDataset(builder.build())
        }

        return responseBuilder.build()
    }

    /**
     * Build the Autofill response for one entry
     */
    fun buildResponseAndSetResult(activity: Activity, entryInfo: EntryInfo) {
        buildResponseAndSetResult(activity, ArrayList<EntryInfo>().apply { add(entryInfo) })
    }

    /**
     * Build the Autofill response for many entry
     */
    fun buildResponseAndSetResult(activity: Activity, entriesInfo: List<EntryInfo>) {
        if (entriesInfo.isEmpty()) {
            activity.setResult(Activity.RESULT_CANCELED)
        } else {
            var setResultOk = false
            activity.intent?.getParcelableExtra<AssistStructure>(EXTRA_ASSIST_STRUCTURE)?.let { structure ->
                StructureParser(structure).parse()?.let { result ->
                    // New Response
                    val response = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val inlineSuggestionsRequest = activity.intent?.getParcelableExtra<InlineSuggestionsRequest?>(EXTRA_INLINE_SUGGESTIONS_REQUEST)
                        if (inlineSuggestionsRequest != null) {
                            Toast.makeText(activity.applicationContext, R.string.autofill_inline_suggestions_keyboard, Toast.LENGTH_SHORT).show()
                        }
                        buildResponse(activity, entriesInfo, result, inlineSuggestionsRequest)
                    } else {
                        buildResponse(activity, entriesInfo, result, null)
                    }
                    val mReplyIntent = Intent()
                    Log.d(activity.javaClass.name, "Successed Autofill auth.")
                    mReplyIntent.putExtra(
                            AutofillManager.EXTRA_AUTHENTICATION_RESULT,
                            response)
                    setResultOk = true
                    activity.setResult(Activity.RESULT_OK, mReplyIntent)
                }
            }
            if (!setResultOk) {
                Log.w(activity.javaClass.name, "Failed Autofill auth.")
                activity.setResult(Activity.RESULT_CANCELED)
            }
        }
    }

    /**
     * Utility method to start an activity with an Autofill for result
     */
    fun startActivityForAutofillResult(activity: Activity,
                                       intent: Intent,
                                       autofillComponent: AutofillComponent,
                                       searchInfo: SearchInfo?) {
        EntrySelectionHelper.addSpecialModeInIntent(intent, SpecialMode.SELECTION)
        intent.putExtra(EXTRA_ASSIST_STRUCTURE, autofillComponent.assistStructure)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                && PreferencesUtil.isAutofillInlineSuggestionsEnable(activity)) {
            autofillComponent.inlineSuggestionsRequest?.let {
                intent.putExtra(EXTRA_INLINE_SUGGESTIONS_REQUEST, it)
            }
        }
        EntrySelectionHelper.addSearchInfoInIntent(intent, searchInfo)
        activity.startActivityForResult(intent, AUTOFILL_RESPONSE_REQUEST_CODE)
    }

    /**
     * Utility method to loop and close each activity with return data
     */
    fun onActivityResultSetResultAndFinish(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == AUTOFILL_RESPONSE_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                activity.setResult(resultCode, data)
            }
            if (resultCode == Activity.RESULT_CANCELED) {
                activity.setResult(Activity.RESULT_CANCELED)
            }
            activity.finish()
        }
    }
}
