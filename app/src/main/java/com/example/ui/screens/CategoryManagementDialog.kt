package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CategoryEntity
import com.example.data.model.CategoryGroup
import com.example.ui.theme.semanticColors

@Composable
fun CategoryManagementDialog(
    categories: List<CategoryEntity>,
    onCreateCategory: (name: String, group: CategoryGroup, keywords: String, monthlyLimitYuanStr: String) -> Unit,
    onUpdateCategory: (categoryId: String, name: String, group: CategoryGroup, keywords: String) -> Unit,
    onDeleteCategory: (categoryId: String) -> Unit,
    onDismiss: () -> Unit
) {
    val semantic = MaterialTheme.semanticColors
    var selectedGroupFilter by remember { mutableStateOf<CategoryGroup?>(null) }
    var showCreateEditor by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }

    val manageableGroups = listOf(
        CategoryGroup.ESSENTIAL_EXPENSE,
        CategoryGroup.FLEXIBLE_EXPENSE,
        CategoryGroup.INCOME
    )

    val filteredCategories = remember(categories, selectedGroupFilter) {
        categories
            .filter { it.group != CategoryGroup.NEUTRAL }
            .filter { selectedGroupFilter == null || it.group == selectedGroupFilter }
            .sortedWith(compareBy<CategoryEntity> { !it.isCustom }.thenBy { it.sortOrder })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Category, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("账单分类管理", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "内置 ${categories.count { !it.isCustom }} 项常用分类，支持自由添加自定义分类并配置自动匹配关键词。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = { showCreateEditor = true },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_create_category_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("新建自定义分类", fontSize = 14.sp)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedGroupFilter == null,
                        onClick = { selectedGroupFilter = null },
                        label = { Text("全部 (${categories.count { it.group != CategoryGroup.NEUTRAL }})", maxLines = 1) }
                    )
                    manageableGroups.forEach { grp ->
                        FilterChip(
                            selected = selectedGroupFilter == grp,
                            onClick = { selectedGroupFilter = if (selectedGroupFilter == grp) null else grp },
                            label = { Text(grp.displayName, maxLines = 1) }
                        )
                    }
                }

                HorizontalDivider(thickness = 0.5.dp, color = semantic.hairline)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filteredCategories.forEach { cat ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("category_manage_row_${cat.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = cat.name,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Surface(
                                            color = if (cat.isCustom) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainerHighest
                                            },
                                            shape = RoundedCornerShape(5.dp)
                                        ) {
                                            Text(
                                                text = if (cat.isCustom) "自定义 · ${cat.group.displayName}" else cat.group.displayName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (cat.isCustom) {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                maxLines = 1,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    if (cat.keywords.isNotBlank()) {
                                        Text(
                                            text = "关键词：${cat.keywords}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { editingCategory = cat },
                                        modifier = Modifier.testTag("edit_category_${cat.id}")
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "编辑${cat.name}",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    if (cat.isCustom) {
                                        IconButton(
                                            onClick = { onDeleteCategory(cat.id) },
                                            modifier = Modifier.testTag("delete_category_${cat.id}")
                                        ) {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = "删除${cat.name}",
                                                tint = semantic.danger,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )

    if (showCreateEditor) {
        CategoryEditorDialog(
            initialCategory = null,
            onDismiss = { showCreateEditor = false },
            onConfirm = { name, group, keywords, budgetStr ->
                onCreateCategory(name, group, keywords, budgetStr)
                showCreateEditor = false
            }
        )
    }

    editingCategory?.let { cat ->
        CategoryEditorDialog(
            initialCategory = cat,
            onDismiss = { editingCategory = null },
            onConfirm = { name, group, keywords, _ ->
                onUpdateCategory(cat.id, name, group, keywords)
                editingCategory = null
            }
        )
    }
}

@Composable
private fun CategoryEditorDialog(
    initialCategory: CategoryEntity?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, group: CategoryGroup, keywords: String, monthlyLimitYuanStr: String) -> Unit
) {
    val isCreating = initialCategory == null
    var name by remember(initialCategory) { mutableStateOf(initialCategory?.name.orEmpty()) }
    var group by remember(initialCategory) {
        mutableStateOf(initialCategory?.group ?: CategoryGroup.FLEXIBLE_EXPENSE)
    }
    var keywords by remember(initialCategory) { mutableStateOf(initialCategory?.keywords.orEmpty()) }
    var budgetStr by remember(initialCategory) { mutableStateOf("150") }

    val selectableGroups = listOf(
        CategoryGroup.ESSENTIAL_EXPENSE,
        CategoryGroup.FLEXIBLE_EXPENSE,
        CategoryGroup.INCOME
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(if (isCreating) "新建自定义分类" else "编辑分类「${initialCategory?.name}」") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("分类名称（1~16字）") },
                    placeholder = { Text("例如：宠物开销 / 社团活动 / 摄影器材") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_category_name_input")
                )

                if (isCreating || initialCategory?.isCustom == true) {
                    Text(
                        text = "所属类型",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        selectableGroups.forEach { grp ->
                            FilterChip(
                                selected = group == grp,
                                onClick = { group = grp },
                                label = { Text(grp.displayName, maxLines = 1) },
                                modifier = Modifier.testTag("custom_category_group_${grp.name.lowercase()}")
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = keywords,
                    onValueChange = { keywords = it },
                    label = { Text("自动归类关键词（选填，逗号分隔）") },
                    placeholder = { Text("例如：猫粮,宠物医院,猫砂") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_category_keywords_input")
                )

                if (isCreating && (group == CategoryGroup.ESSENTIAL_EXPENSE || group == CategoryGroup.FLEXIBLE_EXPENSE)) {
                    OutlinedTextField(
                        value = budgetStr,
                        onValueChange = { budgetStr = it },
                        label = { Text("初始月度预算（元）") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_category_budget_input")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, group, keywords, budgetStr) },
                enabled = name.trim().isNotEmpty(),
                modifier = Modifier.testTag("save_custom_category_button")
            ) {
                Text(if (isCreating) "创建" else "保存")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
