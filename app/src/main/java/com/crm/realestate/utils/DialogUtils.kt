package com.crm.realestate.utils

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object DialogUtils {
    fun showConfirmation(
        context: Context,
        title: String,
        message: String,
        positiveAction: () -> Unit,
        negativeAction: (() -> Unit)? = null,
        positiveButtonText: String = "Yes",
        negativeButtonText: String = "No"
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveButtonText) { _, _ -> positiveAction() }
            .setNegativeButton(negativeButtonText) { dialog, _ ->
                dialog.dismiss()
                negativeAction?.invoke()
            }
            .setCancelable(false)
            .show()
    }
}
