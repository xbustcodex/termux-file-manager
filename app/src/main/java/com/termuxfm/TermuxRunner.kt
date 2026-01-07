package com.termuxfm

import android.content.Context
import android.content.Intent
import android.widget.Toast

object TermuxRunner {

    private const val TERMUX_PACKAGE = "com.termux"
    private const val RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
    private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"

    // Extra keys as documented in the Termux RUN_COMMAND wiki
    private const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
    private const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    private const val EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"

    /**
     * Ask Termux to run a script.
     *
     * @param absolutePath Absolute path to the script, e.g.
     *        /data/data/com.termux/files/home/myscript.sh
     * @param workDir Working directory (default: Termux home)
     * @param background If true, run without opening a session
     */
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
            // "0" = open new session & switch to it
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
}
