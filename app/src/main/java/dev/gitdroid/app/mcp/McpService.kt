package dev.gitdroid.app.mcp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dev.gitdroid.app.MainActivity
import dev.gitdroid.app.R

/**
 * Keeps the MCP server alive while GitDroid is in the background.
 *
 * This is the piece that fixes the "background the app for two minutes and
 * everything reloads" problem: the server, the HTTP connection pool and the
 * token all stay warm in a foreground service, so an agent's request is
 * answered in milliseconds and the UI never has to re-authenticate.
 */
class McpService : android.app.Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val container = (application as? dev.gitdroid.app.GitDroidApp)?.container
        if (container == null) {
            stopSelf()
            return
        }
        container.startMcpServer()
        startForegroundCompat()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            (application as? dev.gitdroid.app.GitDroidApp)?.container?.stopMcpServer()
            stopSelf()
            return START_NOT_STICKY
        }
        // Restart if the system kills us: the agent should not have to
        // re-pair the phone after a memory squeeze.
        return START_STICKY
    }

    override fun onDestroy() {
        (application as? dev.gitdroid.app.GitDroidApp)?.container?.stopMcpServer()
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val container = (application as? dev.gitdroid.app.GitDroidApp)?.container
        val port = container?.mcpPort ?: McpServer.DEFAULT_PORT
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("GitDroid MCP server")
            .setContentText("Listening on port $port — agents can connect")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "MCP server",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps the GitDroid MCP endpoint reachable for AI agents"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "gitdroid_mcp"
        private const val NOTIFICATION_ID = 42
        const val ACTION_STOP = "dev.gitdroid.app.STOP_MCP"

        /** Starts the service (and therefore the server). */
        fun start(context: Context) {
            val intent = Intent(context, McpService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Stops the service and the server. */
        fun stop(context: Context) {
            context.startService(Intent(context, McpService::class.java).setAction(ACTION_STOP))
        }
    }
}
