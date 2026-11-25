package com.example.osai

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

/** 系统运行状态页:进程、线程、信号量、IPC 与各阶段耗时。 */
class SystemStatusActivity : AppCompatActivity() {

    private lateinit var snapshotText: TextView
    private val handler = Handler(Looper.getMainLooper())

    private val poll = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_status)

        snapshotText = findViewById(R.id.snapshotText)

        findViewById<Button>(R.id.threads1).setOnClickListener { setThreads(1) }
        findViewById<Button>(R.id.threads2).setOnClickListener { setThreads(2) }
        findViewById<Button>(R.id.threads4).setOnClickListener { setThreads(4) }
        findViewById<Button>(R.id.resetButton).setOnClickListener {
            runCatching { ApiHolder.current?.resetStats() }
            toast("计数已清零")
        }
        findViewById<Button>(R.id.crashButton).setOnClickListener {
            val api = ApiHolder.current
            if (api == null || !api.isRemote) {
                toast("单进程模式下没有独立推理进程可杀")
                return@setOnClickListener
            }
            runCatching { api.crashForTest() }
            toast("已请求杀死推理进程,返回主页观察重连")
        }
        findViewById<Button>(R.id.modeButton).setOnClickListener { toggleMode() }

        handler.post(poll)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    private fun setThreads(n: Int) {
        val api = ApiHolder.current ?: return toast("未连接")
        runCatching { api.setThreadCount(n) }
        toast("推理线程数:$n")
    }

    private fun toggleMode() {
        val prefs = getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
        val single = prefs.getBoolean(MainActivity.KEY_SINGLE_PROCESS, false)
        prefs.edit().putBoolean(MainActivity.KEY_SINGLE_PROCESS, !single).apply()
        toast(if (!single) "已切换为单进程,返回主页生效" else "已切换为双进程,返回主页生效")
        finish()
    }

    private fun refresh() {
        val api = ApiHolder.current
        if (api == null) {
            snapshotText.text = "未连接推理服务"
            return
        }
        val json = runCatching { JSONObject(api.debugSnapshot()) }.getOrNull()
        if (json == null) {
            snapshotText.text = "读取状态失败"
            return
        }
        val mode = if (api.isRemote) "双进程(Binder IPC)" else "单进程(基线)"
        snapshotText.text = buildString {
            appendLine("运行模式    $mode")
            appendLine("应用进程    pid=${android.os.Process.myPid()}")
            appendLine("推理进程    pid=${json.optInt("pid")}")
            appendLine("推理线程    ${json.optInt("threads")} 个  [${json.optString("threadNames")}]")
            appendLine("信号量许可  ${json.optInt("permits")} 可用 / 在途 ${json.optInt("inFlight")}")
            appendLine("提交/完成   ${json.optLong("submitted")} / ${json.optLong("completed")}")
            appendLine("信号量超时  ${json.optLong("semTimeout")}")
            appendLine("任务超时    ${json.optLong("taskTimeout")}")
            appendLine("端到端耗时  avg ${json.optLong("avg")}·P50 ${json.optLong("p50")}·P95 ${json.optLong("p95")} ms")
            appendLine("已注册人脸  ${json.optInt("faces")} 人")
            appendLine("识别/活体   ${if (json.optBoolean("identify")) "开" else "关"} / ${if (json.optBoolean("liveness")) "开" else "关"}")
            val err = json.optString("loadError")
            if (err.isNotEmpty()) appendLine("模型错误    $err")
        }
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}
