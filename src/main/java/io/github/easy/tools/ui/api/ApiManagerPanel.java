package io.github.easy.tools.ui.api;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import io.github.easy.tools.entity.api.ApiInfo;
import io.github.easy.tools.service.api.ApiCacheService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingWorker;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JTabbedPane;

/**
 * API管理面板
 * 使用Tab切换方式展示API列表和测试功能
 * API列表按Controller分组展示
 *
 * @author iamxiaohaijun
 * @version 2.0.0
 * @since 1.0.0
 */
public class ApiManagerPanel extends JPanel {

    private static final Color COLOR_GET = new Color(78, 205, 196);
    private static final Color COLOR_POST = new Color(69, 183, 209);
    private static final Color COLOR_PUT = new Color(150, 206, 180);
    private static final Color COLOR_DELETE = new Color(255, 107, 107);
    private static final Color COLOR_PATCH = new Color(255, 193, 7);
    private static final Color COLOR_DEFAULT = new Color(108, 117, 125);

    private final Project project;
    private final ApiCacheService apiCacheService;
    private final ApiTestPanel apiTestPanel;

    private JTree apiTree;
    private DefaultTreeModel treeModel;
    private JTextField searchField;
    private JComboBox<String> methodFilter;
    private JLabel statusLabel;

    private List<ApiInfo> apiList = new ArrayList<>();
    private List<ApiInfo> filteredApiList = new ArrayList<>();
    private JTabbedPane tabbedPane;

    public ApiManagerPanel(Project project) {
        this.project = project;
        this.apiCacheService = project.getService(ApiCacheService.class);
        this.apiTestPanel = new ApiTestPanel(project);
        this.initializeUI();
    }

    private void initializeUI() {
        this.setLayout(new BorderLayout());

        // 创建Tab面板
        this.tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        this.tabbedPane.setFont(this.tabbedPane.getFont().deriveFont(Font.PLAIN, 13f));

        // Tab 1: API列表
        JPanel apiListPanel = this.createApiListPanel();
        this.tabbedPane.addTab("API列表", apiListPanel);

        // Tab 2: API测试
        this.tabbedPane.addTab("API测试", this.apiTestPanel);

        // 监听Tab切换
        this.tabbedPane.addChangeListener(e -> {
            if (this.tabbedPane.getSelectedIndex() == 1) {
                // 切换到测试面板时，如果有选中的API则更新
                TreePath path = this.apiTree.getSelectionPath();
                if (path != null && path.getLastPathComponent() instanceof ApiTreeNode) {
                    ApiTreeNode node = (ApiTreeNode) path.getLastPathComponent();
                    if (node.getApiInfo() != null) {
                        this.apiTestPanel.updateSelectedApi(node.getApiInfo());
                    }
                }
            }
        });

        this.add(this.tabbedPane, BorderLayout.CENTER);
    }

    private JPanel createApiListPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // 创建工具栏
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        // 左侧：刷新按钮
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton refreshButton = new JButton("刷新");
        refreshButton.addActionListener(e -> this.refreshApiList());
        refreshButton.setToolTipText("重新扫描API接口");
        leftPanel.add(refreshButton);
        toolbar.add(leftPanel, BorderLayout.WEST);

        // 中间：搜索和过滤
        JPanel centerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));

        JLabel searchLabel = new JLabel("搜索:");
        centerPanel.add(searchLabel);

        this.searchField = new JTextField(20);
        this.searchField.setPreferredSize(new Dimension(180, 28));
        this.searchField.setToolTipText("支持按名称、URL、Controller搜索");
        this.searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                ApiManagerPanel.this.applyFilter();
            }
        });
        centerPanel.add(this.searchField);

        JLabel methodLabel = new JLabel("方法:");
        centerPanel.add(methodLabel);

        this.methodFilter = new JComboBox<>(new String[]{"全部", "GET", "POST", "PUT", "DELETE", "PATCH"});
        this.methodFilter.setPreferredSize(new Dimension(80, 28));
        this.methodFilter.addActionListener(e -> this.applyFilter());
        centerPanel.add(this.methodFilter);

        toolbar.add(centerPanel, BorderLayout.CENTER);

        // 右侧：状态
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        this.statusLabel = new JLabel("共 0 个API");
        this.statusLabel.setFont(this.statusLabel.getFont().deriveFont(Font.PLAIN, 12f));
        rightPanel.add(this.statusLabel);
        toolbar.add(rightPanel, BorderLayout.EAST);

        panel.add(toolbar, BorderLayout.NORTH);

        // 创建树形列表
        JPanel treePanel = this.createTreePanel();
        panel.add(treePanel, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createTreePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

        // 创建树模型
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("API接口");
        this.treeModel = new DefaultTreeModel(root);

        // 创建树
        this.apiTree = new Tree(this.treeModel);
        this.apiTree.setRootVisible(false);
        this.apiTree.setShowsRootHandles(true);
        this.apiTree.setCellRenderer(new ApiTreeCellRenderer());

        // 双击事件：跳转到源码
        this.apiTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    TreePath path = ApiManagerPanel.this.apiTree.getSelectionPath();
                    if (path != null && path.getLastPathComponent() instanceof ApiTreeNode) {
                        ApiTreeNode node = (ApiTreeNode) path.getLastPathComponent();
                        if (node.getApiInfo() != null) {
                            ApiManagerPanel.this.navigateToMethod(node.getApiInfo());
                        }
                    }
                }
            }
        });

        // 单击事件：更新测试面板
        this.apiTree.addTreeSelectionListener(e -> {
            TreePath path = ApiManagerPanel.this.apiTree.getSelectionPath();
            if (path != null && path.getLastPathComponent() instanceof ApiTreeNode) {
                ApiTreeNode node = (ApiTreeNode) path.getLastPathComponent();
                if (node.getApiInfo() != null) {
                    ApiManagerPanel.this.apiTestPanel.updateSelectedApi(node.getApiInfo());
                }
            }
        });

        // 添加滚动面板
        JBScrollPane scrollPane = new JBScrollPane(this.apiTree);
        panel.add(scrollPane, BorderLayout.CENTER);

        // 添加底部提示
        JPanel hintPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        JLabel hintLabel = new JLabel("提示：双击API跳转到源码，单击后在\"API测试\"标签页中测试");
        hintLabel.setFont(hintLabel.getFont().deriveFont(Font.PLAIN, 11f));
        hintLabel.setForeground(Color.GRAY);
        hintPanel.add(hintLabel);
        panel.add(hintPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void applyFilter() {
        String searchText = this.searchField.getText().trim().toLowerCase();
        String method = (String) this.methodFilter.getSelectedItem();

        this.filteredApiList.clear();

        for (ApiInfo api : this.apiList) {
            boolean match = true;

            // 方法过滤
            if (!"全部".equals(method) && !method.equalsIgnoreCase(api.getMethod())) {
                match = false;
            }

            // 搜索过滤
            if (match && !searchText.isEmpty()) {
                boolean searchMatch = false;
                if (api.getName() != null && api.getName().toLowerCase().contains(searchText)) {
                    searchMatch = true;
                }
                if (api.getUrl() != null && api.getUrl().toLowerCase().contains(searchText)) {
                    searchMatch = true;
                }
                if (api.getControllerDescription() != null && api.getControllerDescription().toLowerCase().contains(searchText)) {
                    searchMatch = true;
                }
                if (api.getClassName() != null && api.getClassName().toLowerCase().contains(searchText)) {
                    searchMatch = true;
                }
                match = searchMatch;
            }

            if (match) {
                this.filteredApiList.add(api);
            }
        }

        this.updateTreeStructure();
        this.updateStatusLabel();
    }

    private void updateStatusLabel() {
        int visibleCount = this.filteredApiList.size();
        int totalCount = this.apiList.size();
        if (visibleCount == totalCount) {
            this.statusLabel.setText("共 " + totalCount + " 个API");
        } else {
            this.statusLabel.setText("显示 " + visibleCount + " / " + totalCount + " 个API");
        }
    }

    private void updateTreeStructure() {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) this.treeModel.getRoot();
        root.removeAllChildren();

        // 按Controller分组
        Map<String, List<ApiInfo>> groupedApis = new HashMap<>();
        for (ApiInfo apiInfo : this.filteredApiList) {
            String controllerDesc = apiInfo.getControllerDescription();
            if (controllerDesc == null || controllerDesc.isEmpty()) {
                controllerDesc = apiInfo.getClassName();
                if (controllerDesc != null && controllerDesc.contains(".")) {
                    controllerDesc = controllerDesc.substring(controllerDesc.lastIndexOf('.') + 1);
                }
            }
            if (controllerDesc == null) {
                controllerDesc = "未知Controller";
            }
            groupedApis.computeIfAbsent(controllerDesc, k -> new ArrayList<>()).add(apiInfo);
        }

        // 为每个Controller创建节点
        for (Map.Entry<String, List<ApiInfo>> entry : groupedApis.entrySet()) {
            String controllerDesc = entry.getKey();
            List<ApiInfo> apis = entry.getValue();

            // 创建Controller节点
            DefaultMutableTreeNode controllerNode = new DefaultMutableTreeNode(controllerDesc);
            root.add(controllerNode);

            // 为每个API创建子节点
            for (ApiInfo apiInfo : apis) {
                ApiTreeNode apiNode = new ApiTreeNode(apiInfo);
                controllerNode.add(apiNode);
            }
        }

        this.treeModel.reload();

        // 展开所有节点
        for (int i = 0; i < this.apiTree.getRowCount(); i++) {
            this.apiTree.expandRow(i);
        }
    }

    public void refreshApiList() {
        if (DumbService.isDumb(this.project)) {
            DumbService.getInstance(this.project).runWhenSmart(this::refreshApiList);
            return;
        }

        this.apiList.clear();
        this.filteredApiList.clear();

        // 清空树结构
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) this.treeModel.getRoot();
        root.removeAllChildren();
        this.treeModel.reload();

        SwingWorker<List<ApiInfo>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<ApiInfo> doInBackground() {
                return ApiManagerPanel.this.apiCacheService.reloadApis();
            }

            @Override
            protected void done() {
                try {
                    List<ApiInfo> apis = this.get();
                    ApiManagerPanel.this.apiList.addAll(apis);
                    ApiManagerPanel.this.applyFilter();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        worker.execute();
    }

    private void navigateToMethod(ApiInfo apiInfo) {
        if (apiInfo == null || apiInfo.getVirtualFilePath() == null) {
            return;
        }

        com.intellij.openapi.vfs.VirtualFile virtualFile =
            com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(apiInfo.getVirtualFilePath());

        if (virtualFile != null) {
            FileEditorManager.getInstance(this.project).openFile(virtualFile, true);
            FileEditorManager.getInstance(this.project).openTextEditor(
                new com.intellij.openapi.fileEditor.OpenFileDescriptor(
                    this.project,
                    virtualFile,
                    apiInfo.getMethodOffset()
                ),
                true
            );
        }
    }

    /**
     * API树节点
     */
    private static class ApiTreeNode extends DefaultMutableTreeNode {
        private final ApiInfo apiInfo;

        public ApiTreeNode(ApiInfo apiInfo) {
            super(apiInfo);
            this.apiInfo = apiInfo;
        }

        public ApiInfo getApiInfo() {
            return this.apiInfo;
        }

        @Override
        public String toString() {
            if (this.apiInfo != null) {
                return String.format("%s %s", this.apiInfo.getMethod(), this.apiInfo.getUrl());
            }
            return super.toString();
        }
    }

    /**
     * 树单元格渲染器
     */
    private static class ApiTreeCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
                                                       boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

            // 启用HTML渲染
            this.putClientProperty("html.disable", null);

            if (value instanceof ApiTreeNode) {
                ApiTreeNode node = (ApiTreeNode) value;
                ApiInfo apiInfo = node.getApiInfo();
                if (apiInfo != null) {
                    String method = apiInfo.getMethod();
                    Color color = COLOR_DEFAULT;

                    switch (method.toUpperCase()) {
                        case "GET":
                            color = COLOR_GET;
                            break;
                        case "POST":
                            color = COLOR_POST;
                            break;
                        case "PUT":
                            color = COLOR_PUT;
                            break;
                        case "DELETE":
                            color = COLOR_DELETE;
                            break;
                        case "PATCH":
                            color = COLOR_PATCH;
                            break;
                    }

                    // 设置显示文本
                    String displayText = String.format("<html><span style='color: #%s; font-weight: bold;'>%s</span> " +
                        "<span style='color: #666666;'>%s</span></html>",
                        colorToHex(color), method, apiInfo.getUrl());
                    this.setText(displayText);
                }
            } else if (value instanceof DefaultMutableTreeNode) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
                if (node.getUserObject() instanceof String) {
                    // Controller节点
                    String text = (String) node.getUserObject();
                    this.setText("<html><span style='color: #4A90E2; font-weight: bold;'>" + text + "</span></html>");
                    this.setFont(this.getFont().deriveFont(Font.BOLD));
                }
            }

            return this;
        }

        private String colorToHex(Color color) {
            return String.format("%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
        }
    }
}
