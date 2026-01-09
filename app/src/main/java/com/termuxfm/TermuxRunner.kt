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
     *
     * If [asRoot] = true, we launch:
     *   bash -lc "su -c \"<script_path>\""
     */
    fun runScript(
        context: Context,
        absolutePath: String,
        workDir: String? = null,
        background: Boolean = false,
        asRoot: Boolean = false
    ) {
        val intent = Intent().apply {
            setClassName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE)
            action = ACTION_RUN_COMMAND

            if (!asRoot) {
                // Normal run: execute script directly
                putExtra(EXTRA_COMMAND_PATH, absolutePath)
                putExtra(EXTRA_ARGUMENTS, emptyArray<String>())
            } else {
                // Root run: bash -lc "su -c \"<absolutePath>\""
                val bashPath = "/data/data/com.termux/files/usr/bin/bash"
                val cmd = "su -c \"$absolutePath\""

                putExtra(EXTRA_COMMAND_PATH, bashPath)
                putExtra(EXTRA_ARGUMENTS, arrayOf("-lc", cmd))
            }

            putExtra(EXTRA_WORKDIR, workDir ?: "/data/data/com.termux/files/home")
            putExtra(EXTRA_BACKGROUND, background)
            // "0" = open / reuse session
            putExtra(EXTRA_SESSION_ACTION, "0")
        }

        try {
            context.startService(intent)
            Toast.makeText(
                context,
                if (asRoot) "Sent to Termux (su): $absolutePath"
                else "Sent to Termux: $absolutePath",
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

    /** Convenience: open a Termux terminal window and run the script (normal user). */
    fun runScriptInTerminal(
        context: Context,
        absolutePath: String,
        workDir: String? = null
    ) {
        runScript(
            context = context,
            absolutePath = absolutePath,
            workDir = workDir,
            background = false,
            asRoot = false
        )
    }

    /** Convenience: run in the background without popping a terminal (normal user). */
    fun runScriptInBackground(
        context: Context,
        absolutePath: String,
        workDir: String? = null
    ) {
        runScript(
            context = context,
            absolutePath = absolutePath,
            workDir = workDir,
            background = true,
            asRoot = false
        )
    }

    /** New: run in Termux with su (interactive terminal). */
    fun runScriptWithSuInTerminal(
        context: Context,
        absolutePath: String,
        workDir: String? = null
    ) {
        runScript(
            context = context,
            absolutePath = absolutePath,
            workDir = workDir,
            background = false,
            asRoot = true
        )
    }

    /** Optional: run with su in the background (no terminal). */
    fun runScriptWithSuInBackground(
        context: Context,
        absolutePath: String,
        workDir: String? = null
    ) {
        runScript(
            context = context,
            absolutePath = absolutePath,
            workDir = workDir,
            background = true,
            asRoot = true
        )
    }
}
