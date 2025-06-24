package org.fcitx.fcitx5.android.plugin.smsotp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import java.util.regex.Pattern
import org.fcitx.fcitx5.android.common.FcitxPluginService
import org.fcitx.fcitx5.android.common.ipc.bindFcitxRemoteService
import org.fcitx.fcitx5.android.common.ipc.IFcitxRemoteService

class MainService : FcitxPluginService() {

  override fun onCreate() {
    registerSmsReceiver(this)
  }

  override fun start() {
    // Your existing Fcitx connection logic
  }

  override fun stop() {
    // Your existing Fcitx unbind logic
    unregisterSmsReceiver(this)
    log("Unbind from fcitx remote")
  }
  private fun log(msg: String) {
    Log.d("SmsOtpService", msg)
  }

  private var receiver: BroadcastReceiver? = null

  private fun registerSmsReceiver(context: Context) {
    if (receiver != null) return
    receiver =
      object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
          val extras = intent.extras ?: return
          val pdus = extras["pdus"] as? Array<*> ?: return
          val format = extras.getString("format")
          for (pdu in pdus) {
            val sms =
              android.telephony.SmsMessage.createFromPdu(
                pdu as ByteArray,
                format
              )
            val message = sms.messageBody
            val code = extractOtp(message)
            if (code != null) {
              // showToast(context, "OTP Detected: $code")
              tryInputOtp(context, code);
            }
          }
        }
      }
    val filter = IntentFilter("android.provider.Telephony.SMS_RECEIVED")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
    }
    else {
      context.registerReceiver(receiver, filter)
    }

  }

  private fun unregisterSmsReceiver(context: Context) {
    receiver?.let {
      context.unregisterReceiver(it)
      receiver = null
    }
  }

  private fun extractOtp(message: String?): String? {
    if (message == null) return null
    // More robust regex might be needed for different OTP formats
    val pattern = Pattern.compile("(?<!\\d)(\\d{4,8})(?!\\d)") // Example: 4-8 digits
    val matcher = pattern.matcher(message)
    return if (matcher.find()) matcher.group(1) else null
  }

  private fun showToast(context: Context, text: String) {
    Handler(Looper.getMainLooper()).post {
      Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }
  }

  private fun tryInputOtp(context: Context, code: String) {
    context.bindFcitxRemoteService(BuildConfig.MAIN_APPLICATION_ID,
      onConnected = { remoteService ->
        try {
          remoteService.updateOtp(code)
        } catch (e: Exception) {
          log("updateOtp failed")
        }
      },
      onDisconnect = {}
    )
  }

}