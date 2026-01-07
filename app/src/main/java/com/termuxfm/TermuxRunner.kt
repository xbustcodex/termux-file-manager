package com.termuxfm

import android.content.Context
import android.content.Intent
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

    /**
     * Low-level Termux RUN_COMMAND call.
     */
    fun runScript(
        context: Context,
        absolutePath: String,
        workDir: String? = null,
        background: Boolean = false
    ) {
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
                "Could not talk to Termux. Is Termux installed and permission granted?",
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
}

