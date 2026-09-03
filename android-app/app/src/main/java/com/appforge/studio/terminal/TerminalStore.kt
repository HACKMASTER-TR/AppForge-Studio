package com.appforge.studio.terminal

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object TerminalHistoryStore {
    private const val PREFERENCES =
        "appforge_terminal_history"

    private const val MAX_HISTORY =
        100

    private const val MAX_COMMAND_LENGTH =
        16 * 1_024

    fun load(
        context: Context,
        workspaceId: String
    ): List<String> {
        val raw =
            context
                .getSharedPreferences(
                    PREFERENCES,
                    Context.MODE_PRIVATE
                )
                .getString(
                    workspaceId,
                    null
                )
                ?: return emptyList()

        return runCatching {
            val array =
                JSONArray(raw)

            buildList {
                for (index in 0 until array.length()) {
                    array
                        .optString(index)
                        .trim()
                        .take(MAX_COMMAND_LENGTH)
                        .takeIf {
                            it.isNotBlank()
                        }
                        ?.let {
                            add(it)
                        }
                }
            }.takeLast(MAX_HISTORY)
        }.getOrDefault(emptyList())
    }

    fun add(
        context: Context,
        workspaceId: String,
        command: String
    ): List<String> {
        val clean =
            command
                .trim()
                .take(MAX_COMMAND_LENGTH)

        if (clean.isBlank()) {
            return load(
                context,
                workspaceId
            )
        }

        val updated =
            load(
                context,
                workspaceId
            )
                .filterNot {
                    it == clean
                }
                .plus(clean)
                .takeLast(MAX_HISTORY)

        val array =
            JSONArray()

        updated.forEach {
            array.put(it)
        }

        context
            .getSharedPreferences(
                PREFERENCES,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                workspaceId,
                array.toString()
            )
            .apply()

        return updated
    }
}
object SshProfileStore {
    private const val PREFERENCES =
        "appforge_terminal_ssh"

    private const val PROFILES_KEY =
        "profiles"

    fun load(context: Context): List<SshProfile> {
        val raw =
            context
                .getSharedPreferences(
                    PREFERENCES,
                    Context.MODE_PRIVATE
                )
                .getString(
                    PROFILES_KEY,
                    null
                )
                ?: return emptyList()

        return runCatching {
            val array =
                JSONArray(raw)

            buildList {
                for (index in 0 until array.length()) {
                    val item =
                        array.optJSONObject(index)
                            ?: continue

                    val host =
                        item
                            .optString("host")
                            .trim()

                    val username =
                        item
                            .optString("username")
                            .trim()

                    if (
                        host.isBlank() ||
                        username.isBlank()
                    ) {
                        continue
                    }

                    add(
                        SshProfile(
                            id =
                                item
                                    .optString("id")
                                    .ifBlank {
                                        UUID.randomUUID()
                                            .toString()
                                    },
                            name =
                                item
                                    .optString("name")
                                    .ifBlank {
                                        "$username@$host"
                                    },
                            host = host,
                            port =
                                item
                                    .optInt(
                                        "port",
                                        22
                                    )
                                    .coerceIn(
                                        1,
                                        65_535
                                    ),
                            username = username,
                            workingDirectory =
                                item
                                    .optString(
                                        "workingDirectory",
                                        "~"
                                    )
                                    .ifBlank {
                                        "~"
                                    }
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(
        context: Context,
        profile: SshProfile
    ): List<SshProfile> {
        val profiles =
            load(context)
                .toMutableList()

        val index =
            profiles.indexOfFirst {
                it.id == profile.id
            }

        if (index >= 0) {
            profiles[index] =
                profile
        } else {
            profiles.add(profile)
        }

        persist(
            context,
            profiles
        )

        return profiles
    }

    fun delete(
        context: Context,
        profileId: String
    ): List<SshProfile> {
        val profiles =
            load(context)
                .filterNot {
                    it.id == profileId
                }

        persist(
            context,
            profiles
        )

        return profiles
    }

    private fun persist(
        context: Context,
        profiles: List<SshProfile>
    ) {
        val array =
            JSONArray()

        profiles.forEach { profile ->
            array.put(
                JSONObject()
                    .put(
                        "id",
                        profile.id
                    )
                    .put(
                        "name",
                        profile.name
                    )
                    .put(
                        "host",
                        profile.host
                    )
                    .put(
                        "port",
                        profile.port
                    )
                    .put(
                        "username",
                        profile.username
                    )
                    .put(
                        "workingDirectory",
                        profile.workingDirectory
                    )
            )
        }

        context
            .getSharedPreferences(
                PREFERENCES,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                PROFILES_KEY,
                array.toString()
            )
            .apply()
    }
}
