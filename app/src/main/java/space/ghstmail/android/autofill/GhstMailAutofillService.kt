package space.ghstmail.android.autofill

import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Intent
import android.content.pm.PackageManager
import android.os.CancellationSignal
import android.service.autofill.*
import android.text.InputType
import android.util.Log
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import space.ghstmail.android.R
import space.ghstmail.android.auth.AuthManager

class GhstMailAutofillService : AutofillService() {

    companion object {
        private const val TAG = "GhstMailAutofill"
    }

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        Log.d(TAG, "onFillRequest called")

        val auth = AuthManager(applicationContext)
        if (!auth.isLoggedIn) {
            Log.d(TAG, "Not logged in, skipping")
            callback.onSuccess(null)
            return
        }

        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) {
            Log.d(TAG, "No structure, skipping")
            callback.onSuccess(null)
            return
        }

        val emailFields = mutableListOf<AutofillId>()
        val allTextFields = mutableListOf<AutofillId>()
        var isBrowser = false

        for (i in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            val title = windowNode.title?.toString() ?: ""
            Log.d(TAG, "Window: $title")

            // Detect if this is a browser
            val pkg = structure.activityComponent?.packageName?.lowercase() ?: ""
            val browserKeywords = listOf(
                "chrome", "firefox", "mozilla", "brave", "opera", "edge",
                "samsung.android.app.sbrowser", "browser", "silk", "vivaldi", "kiwi"
            )
            if (browserKeywords.any { pkg.contains(it) || title.lowercase().contains(it) }) {
                isBrowser = true
            }

            traverseNode(windowNode.rootViewNode, emailFields, allTextFields, depth = 0)
        }

        Log.d(TAG, "Found ${emailFields.size} email fields, ${allTextFields.size} text fields, isBrowser=$isBrowser")

        // In browsers, fields often have no metadata — fall back to all text fields
        val fieldsToFill = emailFields.ifEmpty {
            if (isBrowser) allTextFields else emptyList()
        }

        if (fieldsToFill.isEmpty()) {
            callback.onSuccess(null)
            return
        }

        // Create an intent to launch the alias generation activity
        val generateIntent = Intent(applicationContext, GenerateAliasActivity::class.java).apply {
            putExtra("autofill_ids", fieldsToFill.toTypedArray())
            val packageName = structure.activityComponent?.packageName.orEmpty()
            val label = aliasLabel(webDomainIn(structure), appName(packageName), packageName)
            Log.d(TAG, "Alias label: $label")
            putExtra("app_label", label)
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            1001,
            generateIntent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
        )

        // Show a static presentation — tapping it triggers the PendingIntent
        val presentation = RemoteViews(packageName, R.layout.autofill_suggestion)

        try {
            val intentSender = pendingIntent.intentSender

            val datasetBuilder = Dataset.Builder()
            for (fieldId in fieldsToFill) {
                datasetBuilder.setValue(
                    fieldId,
                    AutofillValue.forText("placeholder"),
                    presentation
                )
            }
            datasetBuilder.setAuthentication(intentSender)

            val response = FillResponse.Builder()
                .addDataset(datasetBuilder.build())
                .build()

            Log.d(TAG, "Returning fill response with ${fieldsToFill.size} fields")
            callback.onSuccess(response)
        } catch (e: Exception) {
            Log.e(TAG, "Error building response", e)
            callback.onFailure("Error: ${e.message}")
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onSuccess()
    }

    private fun traverseNode(
        node: AssistStructure.ViewNode,
        emailFields: MutableList<AutofillId>,
        allTextFields: MutableList<AutofillId>,
        depth: Int
    ) {
        val indent = "  ".repeat(depth)
        val className = node.className ?: ""
        val hint = node.hint ?: ""
        val idEntry = node.idEntry ?: ""
        val htmlTag = node.htmlInfo?.tag ?: ""
        val inputType = node.inputType
        val autofillHints = node.autofillHints?.joinToString(",") ?: ""
        val autofillType = node.autofillType

        // Log every interactive node for debugging
        if (autofillType != 0 || htmlTag.isNotEmpty()) {
            Log.v(
                TAG,
                "${indent}Node: class=$className hint=$hint id=$idEntry " +
                    "html=$htmlTag inputType=$inputType " +
                    "autofillHints=[$autofillHints] autofillType=$autofillType"
            )
        }

        // Collect all fillable text fields (for browser fallback)
        if (autofillType == android.view.View.AUTOFILL_TYPE_TEXT) {
            node.autofillId?.let { allTextFields.add(it) }
        }

        if (isEmailField(node)) {
            node.autofillId?.let {
                Log.d(TAG, "${indent}>> EMAIL FIELD FOUND: id=$idEntry hint=$hint html=$htmlTag")
                emailFields.add(it)
            }
        }

        for (i in 0 until node.childCount) {
            traverseNode(node.getChildAt(i), emailFields, allTextFields, depth + 1)
        }
    }

    private fun isEmailField(node: AssistStructure.ViewNode): Boolean {
        // Must be a fillable text field
        if (node.autofillType != android.view.View.AUTOFILL_TYPE_TEXT) return false

        // 1. Check autofill hints
        val hints = node.autofillHints
        if (hints != null) {
            for (hint in hints) {
                val h = hint.lowercase()
                if (h.contains("email") || h == "username" || h == "emailaddress") {
                    return true
                }
            }
        }

        // 2. Check HTML info (for Chrome/WebView)
        val htmlInfo = node.htmlInfo
        if (htmlInfo != null) {
            val tag = htmlInfo.tag?.lowercase() ?: ""
            if (tag == "input") {
                val attrs = htmlInfo.attributes
                if (attrs != null) {
                    var typeVal = ""
                    var nameVal = ""
                    var autocompleteVal = ""
                    var placeholderVal = ""
                    for (pair in attrs) {
                        when (pair.first.lowercase()) {
                            "type" -> typeVal = pair.second?.lowercase() ?: ""
                            "name" -> nameVal = pair.second?.lowercase() ?: ""
                            "autocomplete" -> autocompleteVal = pair.second?.lowercase() ?: ""
                            "placeholder" -> placeholderVal = pair.second?.lowercase() ?: ""
                            "id" -> {
                                val idVal = pair.second?.lowercase() ?: ""
                                if (idVal.contains("email")) return true
                            }
                        }
                    }
                    if (typeVal == "email") return true
                    if (autocompleteVal.contains("email")) return true
                    if (nameVal.contains("email")) return true
                    if (placeholderVal.contains("email")) return true
                }
            }
        }

        // 3. Check Android inputType
        val inputType = node.inputType
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        if (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
        ) {
            return true
        }

        // 4. Check hint text
        val hintText = node.hint?.lowercase() ?: ""
        if (hintText.contains("email") || hintText.contains("e-mail") ||
            hintText.contains("mail address")
        ) {
            return true
        }

        // 5. Check idEntry
        val idEntry = node.idEntry?.lowercase() ?: ""
        if (idEntry.contains("email")) return true

        // 6. Check text (for labels in webview — risky but helps)
        val text = node.text?.toString()?.lowercase() ?: ""
        if (text.isEmpty()) {
            // Empty text fields with "text" type on a login/signup page
            // Check the resource id or web attributes for broader matches
        }

        return false
    }

    /** The site a browser or web view is showing, when it says. */
    private fun webDomainIn(structure: AssistStructure): String? {
        for (i in 0 until structure.windowNodeCount) {
            webDomainIn(structure.getWindowNodeAt(i).rootViewNode)?.let { return it }
        }
        return null
    }

    private fun webDomainIn(node: AssistStructure.ViewNode): String? {
        node.webDomain?.takeIf { it.isNotBlank() }?.let { return it }
        for (i in 0 until node.childCount) {
            webDomainIn(node.getChildAt(i))?.let { return it }
        }
        return null
    }

    /** Null when the system keeps the app hidden from us, which it may on Android 11 and later. */
    private fun appName(packageName: String): String? {
        if (packageName.isEmpty()) return null
        return try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }
}
