package nl.planernl.unraider.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Standaard schermopbouw: een lijst met 16 dp marge, 12 dp tussen blokken en
 * trek-om-te-verversen. De spinner verschijnt alleen als je zelf trekt, niet bij
 * het automatische verversen op de achtergrond.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenList(
    loading: Boolean,
    onRefresh: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    var pulling by remember { mutableStateOf(false) }
    LaunchedEffect(loading) { if (!loading) pulling = false }

    PullToRefreshBox(
        isRefreshing = pulling,
        onRefresh = { pulling = true; onRefresh() },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}
