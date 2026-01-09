package com.termuxfm

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast

object TermuxRunner {

    private const val TERMUX_PACKAGE = "com.termux"
    private const val RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
    private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"

    private const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
    private const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    private const val EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"

    /** Simple check so we fail nicely if Termux isn't there. */
    private fun isTermuxInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(TERMUX_PACKAGE, PackageManager.GET_ACTIVITIES)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Low-level Termux RUN_COMMAND call.
     */
    fun runScript(
        context: Context,
        absolutePath: String,
        workDir: String? = null,
        background: Boolean = false
    ) {
        if (!isTermuxInstalled(context)) {
            Toast.makeText(
                context,
                "Termux is not installed.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val intent = Intent().apply {
            setClassName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE)
            action = ACTION_RUN_COMMAND
            putExtra(EXTRA_COMMAND_PATH, absolutePath)
            putExtra(EXTRA_ARGUMENTS, emptyArray<String>())
            putExtra(EXTRA_WORKDIR, workDir ?: "/data/data/com.termux/files/home")
            putExtra(EXTRA_BACKGROUND, background)
            // "0" = open / reuse session
            putExtra(EXTRA_SESSION_ACTION, "0")
        }

        try {
            context.startService(intent)
            Toast.makeText(
                context,
                "Sent to Termux: $absolutePath",
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Could not talk to Termux. Is Termux permission granted?",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /** Convenience: open a Termux terminal window and run the script. */
    fun runScriptInTerminal(
        context: Context,
        absolutePath: String,
        workDir: String? = null
    ) {
        runScript(
            context = context,
            absolutePath = absolutePath,
            workDir = workDir,
            background = false
        )
    }

    /** Convenience: run in the background without popping a terminal. */
    fun runScriptInBackground(
        context: Context,
        absolutePath: String,
        workDir: String? = null
    ) {
        runScript(
            context = context,
            absolutePath = absolutePath,
            workDir = workDir,
            background = true
        )
    }

    /**
     * NEW: Ask Termux to run the script via `su -c` so it executes as root.
     * (Requires your device to actually have working root + su.)
     */
    fun runScriptAsRoot(
        context: Context,
        absolutePath: String,
        workDir: String? = null
    ) {
        if (!isTermuxInstalled(context)) {
            Toast.makeText(
                context,
                "Termux is not installed.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // We launch /system/bin/su inside Termux and tell it to run the script.
        val intent = Intent().apply {
            setClassName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE)
            action = ACTION_RUN_COMMAND
            putExtra(EXTRA_COMMAND_PATH, "/system/bin/su")
            putExtra(EXTRA_ARGUMENTS, arrayOf("-c", "\"$absolutePath\""))
            putExtra(EXTRA_WORKDIR, workDir ?: "/data/data/com.termux/files/home")
            putExtra(EXTRA_BACKGROUND, false)
            putExtra(EXTRA_SESSION_ACTION, "0")
        }

        try {
            context.startService(intent)
            Toast.makeText(
                context,
                "Sent to Termux (root): $absolutePath",
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Root run failed. su not available or Termux permission issue.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
