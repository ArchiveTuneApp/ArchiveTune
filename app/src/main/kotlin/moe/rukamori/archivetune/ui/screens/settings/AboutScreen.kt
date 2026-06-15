/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.viewmodels.AboutContributorUiCollection
import moe.rukamori.archivetune.viewmodels.AboutContributorsUiState
import moe.rukamori.archivetune.viewmodels.AboutLinkCollection
import moe.rukamori.archivetune.viewmodels.AboutScreenEffect
import moe.rukamori.archivetune.viewmodels.AboutScreenState
import moe.rukamori.archivetune.viewmodels.AboutUiModel
import moe.rukamori.archivetune.viewmodels.AboutViewModel
import moe.rukamori.archivetune.viewmodels.TeamMember

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: AboutViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(viewModel, uriHandler) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is AboutScreenEffect.OpenUri -> uriHandler.openUri(effect.uri)
            }
        }
    }

    AboutScreenContent(
        state = state,
        scrollBehavior = scrollBehavior,
        onNavigateUp = navController::navigateUp,
        onNavigateHome = navController::backToMain,
        onOpenUri = viewModel::openUri,
        onRetryContributors = viewModel::retryContributors,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutScreenContent(
    state: AboutScreenState,
    scrollBehavior: TopAppBarScrollBehavior,
    onNavigateUp: () -> Unit,
    onNavigateHome: () -> Unit,
    onOpenUri: (String) -> Unit,
    onRetryContributors: () -> Unit,
) {
    val listState = rememberLazyListState()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.about),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateUp,
                        onLongClick = onNavigateHome,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.back_button_desc),
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        val stateModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                ),
            )

        when (state) {
            AboutScreenState.Loading -> {
                AboutStateContent(
                    messageResId = R.string.loading,
                    showLoading = true,
                    modifier = stateModifier,
                )
            }

            AboutScreenState.Empty -> {
                AboutStateContent(
                    messageResId = R.string.no_results_found,
                    modifier = stateModifier,
                )
            }

            is AboutScreenState.Error -> {
                AboutStateContent(
                    messageResId = state.messageResId,
                    modifier = stateModifier,
                )
            }

            is AboutScreenState.Success -> {
                AboutSuccessContent(
                    model = state.model,
                    onOpenUri = onOpenUri,
                    onRetryContributors = onRetryContributors,
                    listState = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(
                            LocalPlayerAwareWindowInsets.current.only(
                                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                            ),
                        ),
                    contentPadding = PaddingValues(
                        top = innerPadding.calculateTopPadding() + AboutSpacing.sm,
                        bottom = AboutSpacing.xl,
                    ),
                )
            }
        }
    }
}

@Composable
private fun AboutStateContent(
    @StringRes messageResId: Int,
    modifier: Modifier = Modifier,
    showLoading: Boolean = false,
) {
    Column(
        modifier = modifier.padding(AboutSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (showLoading) {
            LoadingIndicator(modifier = Modifier.size(AboutDimensions.StateIndicatorSize))
            Spacer(modifier = Modifier.height(AboutSpacing.sm))
        }
        Text(
            text = stringResource(messageResId),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AboutSuccessContent(
    model: AboutUiModel,
    onOpenUri: (String) -> Unit,
    onRetryContributors: () -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(AboutSpacing.md),
    ) {
        item(key = "hero", contentType = "about_hero") {
            AboutContentContainer {
                AboutHeroCard(
                    model = model,
                    onOpenUri = onOpenUri,
                )
            }
        }

        item(key = "lead", contentType = "about_lead") {
            AboutContentContainer {
                AboutLeadSection(
                    member = model.leadDeveloper,
                    onOpenUri = onOpenUri,
                )
            }
        }

        item(key = "team", contentType = "about_member_section") {
            AboutContentContainer {
                TeamMemberSection(
                    titleResId = R.string.about_archive_tune_team,
                    membersCount = model.collaborators.size,
                    memberAt = { index -> model.collaborators[index] },
                    onOpenUri = onOpenUri,
                )
            }
        }

        item(key = "respecters", contentType = "about_member_section") {
            AboutContentContainer {
                TeamMemberSection(
                    titleResId = R.string.about_respecter,
                    membersCount = model.respecters.size,
                    memberAt = { index -> model.respecters[index] },
                    onOpenUri = onOpenUri,
                )
            }
        }

        item(key = "contributors", contentType = "about_contributors") {
            AboutContentContainer {
                ContributorsSection(
                    state = model.contributorsState,
                    onOpenProfile = onOpenUri,
                    onRetry = onRetryContributors,
                )
            }
        }
    }
}

@Composable
private fun AboutContentContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AboutSpacing.sm),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = AboutDimensions.ContentMaxWidth),
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AboutHeroCard(
    model: AboutUiModel,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AboutSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AboutSpacing.sm),
        ) {
            AppMark()

            Text(
                text = stringResource(model.appNameResId),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AboutSpacing.xs, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(AboutSpacing.xs),
            ) {
                AboutBadge(
                    labelResId = R.string.app_version,
                    value = model.versionName,
                )
                model.buildHash?.let { buildHash ->
                    AboutBadge(value = buildHash)
                }
                AboutBadge(value = model.buildVariant)
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = AboutSpacing.xs),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            LinkChipRow(
                links = model.primaryLinks,
                onOpenUri = onOpenUri,
            )
        }
    }
}

@Composable
private fun AppMark(
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(AboutDimensions.AppMarkContainerSize),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        val iconTint = MaterialTheme.colorScheme.onPrimaryContainer
        val iconColorFilter = remember(iconTint) { ColorFilter.tint(iconTint) }
        Image(
            painter = painterResource(R.drawable.about_splash),
            contentDescription = null,
            colorFilter = iconColorFilter,
            modifier = Modifier
                .padding(AboutSpacing.sm)
                .size(AboutDimensions.AppMarkIconSize),
        )
    }
}

@Composable
private fun AboutBadge(
    value: String,
    modifier: Modifier = Modifier,
    @StringRes labelResId: Int? = null,
) {
    Surface(
        modifier = modifier.heightIn(min = AboutDimensions.MinTouchTarget),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AboutSpacing.sm, vertical = AboutSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(AboutSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            labelResId?.let {
                Text(
                    text = stringResource(it),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinkChipRow(
    links: AboutLinkCollection,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AboutSpacing.xs, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(AboutSpacing.xs),
    ) {
        repeat(links.size) { index ->
            val link = links[index]
            OutlinedIconChip(
                iconRes = link.iconResId,
                contentDescription = stringResource(link.labelResId),
                text = stringResource(link.labelResId),
                onClick = { onOpenUri(link.url) },
            )
        }
    }
}

@Composable
fun OutlinedIconChip(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    text: String? = null,
) {
    val contentPadding = remember {
        PaddingValues(horizontal = AboutSpacing.sm, vertical = AboutSpacing.xs)
    }

    OutlinedButton(
        onClick = onClick,
        contentPadding = contentPadding,
        modifier = Modifier.heightIn(min = AboutDimensions.MinTouchTarget),
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = if (text.isNullOrBlank()) contentDescription else null,
            modifier = Modifier.size(AboutDimensions.SmallIconSize),
        )
        if (!text.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(AboutSpacing.xs))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AboutLeadSection(
    member: TeamMember,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AboutSpacing.xs),
    ) {
        SectionHeader(titleResId = R.string.about_lead_developer)
        LeadDeveloperCard(
            member = member,
            onOpenUri = onOpenUri,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LeadDeveloperCard(
    member: TeamMember,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AboutSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(AboutSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProfileImage(
                imageUrl = member.avatarUrl,
                contentDescription = member.name,
                size = AboutDimensions.LeadAvatarSize,
                borderColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AboutSpacing.xs),
            ) {
                Text(
                    text = member.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(member.positionResId),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                MemberLinkButtons(
                    links = member.links,
                    onOpenUri = onOpenUri,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun TeamMemberSection(
    @StringRes titleResId: Int,
    membersCount: Int,
    memberAt: (Int) -> TeamMember,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AboutSpacing.xs),
    ) {
        SectionHeader(titleResId = titleResId)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                repeat(membersCount) { index ->
                    val member = memberAt(index)
                    TeamMemberRow(
                        member = member,
                        showDivider = index < membersCount - 1,
                        onOpenUri = onOpenUri,
                    )
                }
            }
        }
    }
}

@Composable
private fun TeamMemberRow(
    member: TeamMember,
    showDivider: Boolean,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        val rowContent: @Composable () -> Unit = {
            ListItem(
                leadingContent = {
                    ProfileImage(
                        imageUrl = member.avatarUrl,
                        contentDescription = member.name,
                        size = AboutDimensions.MemberAvatarSize,
                    )
                },
                headlineContent = {
                    Text(
                        text = member.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = {
                    Text(
                        text = stringResource(member.positionResId),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingContent = {
                    MemberLinkButtons(
                        links = member.links,
                        onOpenUri = onOpenUri,
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }

        member.profileUrl?.let { profileUrl ->
            Card(
                onClick = { onOpenUri(profileUrl) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                rowContent()
            }
        } ?: rowContent()

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = AboutSpacing.lg, end = AboutSpacing.sm),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemberLinkButtons(
    links: AboutLinkCollection,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(AboutSpacing.xxs),
        verticalArrangement = Arrangement.spacedBy(AboutSpacing.xxs),
    ) {
        repeat(links.size) { index ->
            val link = links[index]
            OutlinedIconChipMembers(
                iconRes = link.iconResId,
                contentDescription = stringResource(link.labelResId),
                onClick = { onOpenUri(link.url) },
                contentColor = contentColor,
            )
        }
    }
}

@Composable
fun OutlinedIconChipMembers(
    @DrawableRes iconRes: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    OutlinedIconButton(
        onClick = onClick,
        modifier = Modifier.size(AboutDimensions.MinTouchTarget),
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(AboutDimensions.SmallIconSize),
            tint = contentColor,
        )
    }
}

@Composable
private fun SectionHeader(
    @StringRes titleResId: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(titleResId),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = AboutSpacing.xs),
    )
}

@Composable
private fun ContributorsSection(
    state: AboutContributorsUiState,
    onOpenProfile: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AboutSpacing.xs),
    ) {
        SectionHeader(titleResId = R.string.about_contributors)

        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            ),
        ) {
            when (state) {
                AboutContributorsUiState.Loading -> {
                    ContributorStatusContent(
                        messageResId = R.string.loading,
                        showLoading = true,
                    )
                }

                AboutContributorsUiState.Empty -> {
                    ContributorStatusContent(
                        messageResId = R.string.no_results_found,
                        showRetry = true,
                        onRetry = onRetry,
                    )
                }

                is AboutContributorsUiState.Error -> {
                    ContributorStatusContent(
                        messageResId = state.messageResId,
                        showRetry = true,
                        onRetry = onRetry,
                    )
                }

                is AboutContributorsUiState.Success -> {
                    ContributorGrid(
                        contributors = state.contributors,
                        onOpenProfile = onOpenProfile,
                        modifier = Modifier.padding(AboutSpacing.sm),
                    )
                }
            }
        }
    }
}

@Composable
private fun ContributorStatusContent(
    @StringRes messageResId: Int,
    modifier: Modifier = Modifier,
    showLoading: Boolean = false,
    showRetry: Boolean = false,
    onRetry: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(AboutSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AboutSpacing.sm),
    ) {
        if (showLoading) {
            LoadingIndicator(modifier = Modifier.size(AboutDimensions.InlineIndicatorSize))
        }
        Text(
            text = stringResource(messageResId),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (showRetry) {
            FilledTonalButton(onClick = onRetry) {
                Text(text = stringResource(R.string.retry))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContributorGrid(
    contributors: AboutContributorUiCollection,
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val spacing = AboutSpacing.xs
        val columns = when {
            maxWidth >= AboutDimensions.ExpandedGridWidth -> 6
            maxWidth >= AboutDimensions.CompactGridWidth -> 4
            else -> 3
        }
        val itemWidth = (maxWidth - spacing * (columns - 1)) / columns

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = columns,
            horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            repeat(contributors.size) { index ->
                val contributor = contributors[index]
                ContributorTile(
                    login = contributor.login,
                    avatarUrl = contributor.avatarUrl,
                    profileUrl = contributor.profileUrl,
                    onOpenProfile = onOpenProfile,
                    modifier = Modifier.width(itemWidth),
                )
            }
        }
    }
}

@Composable
private fun ContributorTile(
    login: String,
    avatarUrl: String,
    profileUrl: String,
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = { onOpenProfile(profileUrl) },
        enabled = profileUrl.isNotBlank(),
        modifier = modifier.heightIn(min = AboutDimensions.ContributorTileMinHeight),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AboutSpacing.xs, vertical = AboutSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ProfileImage(
                imageUrl = avatarUrl,
                contentDescription = login,
                size = AboutDimensions.ContributorAvatarSize,
            )
            Spacer(modifier = Modifier.height(AboutSpacing.xs))
            Text(
                text = login,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ProfileImage(
    imageUrl: String,
    contentDescription: String,
    size: Dp,
    modifier: Modifier = Modifier,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
) {
    AsyncImage(
        model = imageUrl,
        contentDescription = contentDescription,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(
                width = AboutDimensions.AvatarBorderWidth,
                color = borderColor,
                shape = CircleShape,
            ),
    )
}

private object AboutSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 16.dp
    val md = 24.dp
    val lg = 32.dp
    val xl = 48.dp
}

private object AboutDimensions {
    val ContentMaxWidth = 840.dp
    val MinTouchTarget = 48.dp
    val SmallIconSize = 18.dp
    val AppMarkContainerSize = 104.dp
    val AppMarkIconSize = 72.dp
    val LeadAvatarSize = 88.dp
    val MemberAvatarSize = 56.dp
    val ContributorAvatarSize = 52.dp
    val ContributorTileMinHeight = 116.dp
    val StateIndicatorSize = 40.dp
    val InlineIndicatorSize = 32.dp
    val AvatarBorderWidth = 1.dp
    val CompactGridWidth = 360.dp
    val ExpandedGridWidth = 600.dp
}
