package com.heyboard.teachingassistant.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 静态广播接收器，监听 H3C 一键下课广播。
 *
 * 监听两个 Action：
 * - com.h3c.action.FINISH_CLASS      → 下课流程开始前（杀 APP 之前）
 * - com.h3c.action.FINISH_CLASS_DONE → 下课流程完成后（兜底）
 *
 * 不启动 Service，直接在 onReceive 中通过 goAsync() + 后台线程执行串口指令，
 * 最大限度缩短执行时间，避免被一键下课的进程清理打断。
 */
class FinishClassReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "FinishClassReceiver"
        const val ACTION_FINISH_CLASS = "com.h3c.action.FINISH_CLASS"
        const val ACTION_FINISH_CLASS_DONE = "com.h3c.action.FINISH_CLASS_DONE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "Broadcast received: ${intent.action}")

        val pendingResult = goAsync()
        Thread {
            try {
                AutomationExecutor.executeOnClose(context.applicationContext)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to execute on_close scenarios", e)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}
