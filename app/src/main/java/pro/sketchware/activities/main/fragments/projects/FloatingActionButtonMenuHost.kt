package pro.sketchware.activities.main.fragments.projects

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import pro.sketchware.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
object FloatingActionButtonMenuHost {
    
    // Exposes safe state controls to the Java Fragment
    class MenuController(private val expandedState: MutableState<Boolean>) {
        fun isExpanded(): Boolean = expandedState.value
        fun collapse() { expandedState.value = false }
    }

    @JvmStatic
    fun setupFabMenu(
        composeView: ComposeView,
        onNewProject: Runnable,
        onRestoreBackup: Runnable,
        onImportProject: Runnable
    ): MenuController {
        val expandedState = mutableStateOf(false)
        val controller = MenuController(expandedState)

        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        composeView.setContent {
            val context = LocalContext.current
            val isDark = isSystemInDarkTheme()
            

            val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (isDark) darkColorScheme() else lightColorScheme()
            }

            MaterialTheme(colorScheme = colorScheme) {
                // Intercept back button when the menu is open
                BackHandler(enabled = expandedState.value) {
                    expandedState.value = false
                }

                FloatingActionButtonMenu(
                    modifier = Modifier,
                    expanded = expandedState.value,
                    button = {
                        ToggleFloatingActionButton(
                            checked = expandedState.value,
                            onCheckedChange = { expandedState.value = !expandedState.value }
                        ) {
                            val showClose = checkedProgress > 0.5f
                            val iconRes = if (showClose) R.drawable.ic_mtrl_close else R.drawable.ic_mtrl_add
                            
                            Icon(
                                painter = painterResource(id = iconRes),
                                contentDescription = if (expandedState.value) "Close menu" else "Create and Import",
                                modifier = Modifier.animateIcon({ checkedProgress })
                            )
                        }
                    }
                ) {
                    FloatingActionButtonMenuItem(
                        onClick = {
                            expandedState.value = false
                            onImportProject.run()
                        },
                        text = { Text("Import Android Studio Project") },
                        icon = { 
                            Icon(painterResource(id = R.drawable.ic_mtrl_folder_code), contentDescription = "Import") 
                        }
                    )

                    FloatingActionButtonMenuItem(
                        onClick = {
                            expandedState.value = false
                            onRestoreBackup.run()
                        },
                        text = { Text("Restore Sketchware Backup") },
                        icon = { 
                            Icon(painterResource(id = R.drawable.ic_mtrl_history), contentDescription = "Restore") 
                        }
                    )

                    FloatingActionButtonMenuItem(
                        onClick = {
                            expandedState.value = false
                            onNewProject.run()
                        },
                        text = { Text("New project") },
                        icon = { 
                            Icon(painterResource(id = R.drawable.ic_mtrl_add), contentDescription = "New") 
                        }
                    )
                }
            }
        }
        return controller
    }
}
