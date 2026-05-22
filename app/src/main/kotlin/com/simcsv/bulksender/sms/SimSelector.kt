package com.simcsv.bulksender.sms

import android.content.Context
import android.os.Build
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.annotation.RequiresPermission

data class SimInfo(
    val slotIndex: Int,
    val subscriptionId: Int,
    val displayName: String,
    val carrierName: String,
    val number: String
)

object SimSelector {

    fun getAvailableSims(context: Context): List<SimInfo> {
        return try {
            val subscriptionManager =
                context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val subs: List<SubscriptionInfo>? = subscriptionManager.activeSubscriptionInfoList
            subs?.map { info ->
                SimInfo(
                    slotIndex = info.simSlotIndex,
                    subscriptionId = info.subscriptionId,
                    displayName = info.displayName?.toString() ?: "SIM ${info.simSlotIndex + 1}",
                    carrierName = info.carrierName?.toString() ?: "Unknown",
                    number = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        info.number ?: ""
                    } else {
                        @Suppress("DEPRECATION")
                        info.number ?: ""
                    }
                )
            } ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun isSimAvailable(context: Context): Boolean {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return tm.simState == TelephonyManager.SIM_STATE_READY
    }

    fun getSimCountLabel(context: Context): String {
        val sims = getAvailableSims(context)
        return when (sims.size) {
            0 -> "No SIM detected"
            1 -> "Single SIM: ${sims[0].carrierName}"
            else -> "Dual SIM: ${sims.joinToString(" / ") { it.carrierName }}"
        }
    }
}
