package com.adnet.webrtc

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences


fun sharedPrefGetString(
    intent: Intent, context: Context, sharedPref: SharedPreferences,
    attributeId: Int, intentName: String, defaultId: Int, useFromIntent: Boolean
): String {
    val defaultValue = context.getString(defaultId)
    return if (useFromIntent) {
        val value = intent.getStringExtra(intentName)
        value ?: defaultValue
    } else {
        val attributeName = context.getString(attributeId)
        sharedPref.getString(attributeName, defaultValue).toString()
    }
}

/**
 * Get a value from the shared preference or from the intent, if it does not
 * exist the default is used.
 */
fun sharedPrefGetBoolean(
    intent: Intent, context: Context, sharedPref: SharedPreferences,
    attributeId: Int, intentName: String, defaultId: Int, useFromIntent: Boolean
): Boolean {
    val defaultValue = java.lang.Boolean.parseBoolean(context.getString(defaultId))
    return if (useFromIntent) {
        intent.getBooleanExtra(intentName, defaultValue)
    } else {
        val attributeName = context.getString(attributeId)
        sharedPref!!.getBoolean(attributeName, defaultValue)
    }
}

/**
 * Get a value from the shared preference or from the intent, if it does not
 * exist the default is used.
 */
fun sharedPrefGetInteger(
    intent: Intent, context: Context, sharedPref: SharedPreferences,
    attributeId: Int, intentName: String, defaultId: Int, useFromIntent: Boolean
): Int {
    val defaultString = context.getString(defaultId)
    val defaultValue = defaultString.toInt()
    return if (useFromIntent) {
        intent.getIntExtra(intentName, defaultValue)
    } else {
        val attributeName = context.getString(attributeId)
        val value = sharedPref!!.getString(attributeName, defaultString)
        try {
            value!!.toInt()
        } catch (e: NumberFormatException) {
//                Log.e(
//                    ConnectActivity.TAG,
//                    "Wrong setting for: $attributeName:$value"
//                )
            defaultValue
        }
    }
}