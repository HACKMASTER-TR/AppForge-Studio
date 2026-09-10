package com.appforge.studio.terminal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.appforge.studio.security.OwnerAccessPolicy

@Composable
internal fun OwnerFilesPanel(
    accountEmail: String
) {
    val context =
        LocalContext.current

    val authorized =
        remember(accountEmail) {
            OwnerAccessPolicy
                .isActiveOwner(
                    context,
                    accountEmail
                )
        }

    if (!authorized) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(16.dp)
        ) {
            Text(
                "Erişim reddedildi."
            )
        }

        return
    }

    val root =
        remember(accountEmail) {
            /*
             * Alt klasörleri oluştur.
             * Token/API key burada saklanmaz.
             */
            OwnerAccessPolicy
                .apkRoot(
                    context,
                    accountEmail
                )

            OwnerAccessPolicy
                .githubRoot(
                    context,
                    accountEmail
                )

            OwnerAccessPolicy
                .filesRoot(
                    context,
                    accountEmail
                )
        }

    WorkspaceFilesPanel(
        workspace =
            root
    )
}
