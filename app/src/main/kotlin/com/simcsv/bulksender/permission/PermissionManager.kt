package com.simcsv.bulksender.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

object PermissionManager {

    const val REQUEST_CODE_SMS = 1001
    const val REQUEST_CODE_STORAGE = 1002
    const val REQUEST_CODE_NOTIFICATION = 1003
    const val REQUEST_CODE_ALL = 1004

    private val SMS_PERMISSIONS = arrayOf(
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS
    )

    private val NOTIFICATION_PERMISSIONS =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        else emptyArray()

    private val STORAGE_PERMISSIONS =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) emptyArray()
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    fun hasSmsPermissions(context: Context): Boolean =
        SMS_PERMISSIONS.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    fun hasStoragePermissions(context: Context): Boolean =
        STORAGE_PERMISSIONS.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    fun hasNotificationPermission(context: Context): Boolean =
        NOTIFICATION_PERMISSIONS.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    fun hasAllPermissions(context: Context): Boolean =
        hasSmsPermissions(context) && hasStoragePermissions(context) && hasNotificationPermission(context)

    fun requestSmsPermissions(fragment: Fragment) {
        fragment.requestPermissions(SMS_PERMISSIONS, REQUEST_CODE_SMS)
    }

    fun requestStoragePermissions(fragment: Fragment) {
        if (STORAGE_PERMISSIONS.isNotEmpty()) {
            fragment.requestPermissions(STORAGE_PERMISSIONS, REQUEST_CODE_STORAGE)
        }
    }

    fun requestNotificationPermission(fragment: Fragment) {
        if (NOTIFICATION_PERMISSIONS.isNotEmpty()) {
            fragment.requestPermissions(NOTIFICATION_PERMISSIONS, REQUEST_CODE_NOTIFICATION)
        }
    }

    fun requestAllPermissions(fragment: Fragment) {
        val all = (SMS_PERMISSIONS + NOTIFICATION_PERMISSIONS + STORAGE_PERMISSIONS).distinct().toTypedArray()
        fragment.requestPermissions(all, REQUEST_CODE_ALL)
    }

    fun getMissingPermissions(context: Context): List<String> {
        val all = SMS_PERMISSIONS + NOTIFICATION_PERMISSIONS + STORAGE_PERMISSIONS
        return all.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
    }

    fun shouldShowRationale(activity: Activity, permission: String): Boolean =
        ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
}
