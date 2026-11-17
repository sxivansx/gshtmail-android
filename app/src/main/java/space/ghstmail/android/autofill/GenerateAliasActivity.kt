package space.ghstmail.android.autofill

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.service.autofill.Dataset
import android.util.Log
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import space.ghstmail.android.R
import space.ghstmail.android.api.GhstMailApi
import space.ghstmail.android.auth.AuthManager
import kotlinx.coroutines.*

class GenerateAliasActivity : Activity() {

    companion object {
        private const val TAG = "GhstMailGenerate"
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val autofillIds = intent.getParcelableArrayExtra("autofill_ids")
            ?.filterIsInstance<AutofillId>()
        val appLabel = intent.getStringExtra("app_label")

        if (autofillIds.isNullOrEmpty()) {
            Log.e(TAG, "No autofill IDs passed")
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        val auth = AuthManager(applicationContext)
        val token = auth.token
        if (token == null) {
            Log.e(TAG, "Not logged in")
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                GhstMailApi.createAlias(token, label = appLabel?.takeIf { it.isNotBlank() })
            }

            result.onSuccess { response ->
                val alias = response.alias
                Log.d(TAG, "Created alias: ${alias.address}")

                val presentation = RemoteViews(packageName, R.layout.autofill_suggestion).apply {
                    setTextViewText(R.id.autofill_title, "GhstMail")
                    setTextViewText(R.id.autofill_subtitle, alias.address)
                }

                val datasetBuilder = Dataset.Builder()
                for (fieldId in autofillIds) {
                    datasetBuilder.setValue(
                        fieldId,
                        AutofillValue.forText(alias.address),
                        presentation
                    )
                }

                val replyIntent = Intent().apply {
                    putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, datasetBuilder.build())
                }
                setResult(RESULT_OK, replyIntent)
                finish()
            }.onFailure { error ->
                Log.e(TAG, "Failed to create alias: ${error.message}")
                setResult(RESULT_CANCELED)
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
