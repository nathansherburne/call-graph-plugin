import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class CallGraphToolWindow {
    private JButton runButton;
    private JPanel callGraphToolWindowContent;
    private JPanel canvasPanel;
    private JRadioButton projectScopeButton;
    private JRadioButton moduleScopeButton;
    private JRadioButton directoryScopeButton;
    private JTextField directoryScopeTextField;
    private JComboBox<String> moduleScopeComboBox;
    private JTabbedPane mainTabbedPanel;
    private JCheckBox includeTestFilesCheckBox;
    private JLabel buildTypeLabel;
    private JProgressBar loadingProgressBar;
    private JButton showOnlyUpstreamButton;
    private JButton showOnlyDownstreamButton;
    private JButton showOnlyUpstreamDownstreamButton;
    private JCheckBox upstreamDownstreamScopeCheckbox;
    private JCheckBox viewPackageNameCheckBox;
    private JCheckBox viewFilePathCheckBox;

    private CanvasBuilder canvasBuilder;
    private Node clickedNode;

    @SuppressWarnings("WeakerAccess")
    public CallGraphToolWindow() {
        this.canvasBuilder = new CanvasBuilder();

        // click handlers for buttons
        this.projectScopeButton.addActionListener(e -> projectScopeButtonHandler());
        this.moduleScopeButton.addActionListener(e -> moduleScopeButtonHandler());
        this.directoryScopeButton.addActionListener(e -> directoryScopeButtonHandler());
        this.runButton.addActionListener(e -> run(getSelectedBuildType()));
        this.showOnlyUpstreamButton.addActionListener(e -> run(CanvasConfig.BuildType.UPSTREAM));
        this.showOnlyDownstreamButton.addActionListener(e -> run(CanvasConfig.BuildType.DOWNSTREAM));
        this.showOnlyUpstreamDownstreamButton.addActionListener(e -> run(CanvasConfig.BuildType.UPSTREAM_DOWNSTREAM));
    }

    @SuppressWarnings("WeakerAccess")
    @NotNull
    public JPanel getContent() {
        return this.callGraphToolWindowContent;
    }

    void setClickedNode(@Nullable Node node) {
        this.clickedNode = node;
        boolean isEnabled = node != null;
        this.showOnlyUpstreamButton.setEnabled(isEnabled);
        this.showOnlyDownstreamButton.setEnabled(isEnabled);
        this.showOnlyUpstreamDownstreamButton.setEnabled(isEnabled);
    }

    void resetIndeterminateProgressBar() {
        this.loadingProgressBar.setIndeterminate(true);
        this.loadingProgressBar.setStringPainted(false);
    }

    void resetDeterminateProgressBar(int maximum) {
        this.loadingProgressBar.setIndeterminate(false);
        this.loadingProgressBar.setMaximum(maximum);
        this.loadingProgressBar.setValue(0);
        this.loadingProgressBar.setStringPainted(true);
    }

    void incrementDeterminateProgressBar() {
        int newValue = this.loadingProgressBar.getValue() + 1;
        this.loadingProgressBar.setValue(newValue);
        this.loadingProgressBar.setString(String.format("%d / %d", newValue, this.loadingProgressBar.getMaximum()));
    }

    boolean isRenderFunctionPackageName() {
        return this.viewPackageNameCheckBox.isSelected();
    }

    boolean isRenderFunctionFilePath() {
        return this.viewFilePathCheckBox.isSelected();
    }

    private void run(@NotNull CanvasConfig.BuildType buildType) {
        Project project = Utils.getActiveProject();
        if (project != null) {
            Utils.runBackgroundTask(project, () -> {
                // set up the config object
                String maybeModuleName = (String) this.moduleScopeComboBox.getSelectedItem();
                CanvasConfig canvasConfig = new CanvasConfig()
                        .setProject(project)
                        .setBuildType(buildType)
                        .setSelectedModuleName(maybeModuleName == null ? "" : maybeModuleName)
                        .setSelectedDirectoryPath(this.directoryScopeTextField.getText())
                        .setFocusedNode(this.clickedNode)
                        .setCallGraphToolWindow(this);
                // start building graph
                setupUiBeforeRun(canvasConfig);
                Canvas canvas = this.canvasBuilder.build(canvasConfig);
                setupUiAfterRun(canvas);
            });
        }
    }

    private void disableAllSecondaryOptions() {
        this.includeTestFilesCheckBox.setEnabled(false);
        this.moduleScopeComboBox.setEnabled(false);
        this.directoryScopeTextField.setEnabled(false);
    }

    private void projectScopeButtonHandler() {
        disableAllSecondaryOptions();
        this.includeTestFilesCheckBox.setEnabled(true);
    }

    private void moduleScopeButtonHandler() {
        Project project = Utils.getActiveProject();
        if (project != null) {
            // set up modules drop-down
            this.moduleScopeComboBox.removeAllItems();
            Utils.getActiveModules(project)
                    .forEach(module -> this.moduleScopeComboBox.addItem(module.getName()));
            disableAllSecondaryOptions();
            this.moduleScopeComboBox.setEnabled(true);
        }
    }

    private void directoryScopeButtonHandler() {
        Project project = Utils.getActiveProject();
        if (project != null) {
            // set up directory option text field
            this.directoryScopeTextField.setText(project.getBasePath());
            disableAllSecondaryOptions();
            this.directoryScopeTextField.setEnabled(true);
        }
    }

    private void setupUiBeforeRun(@NotNull CanvasConfig canvasConfig) {
        // focus on the 'graph tab
        this.mainTabbedPanel.getComponentAt(1).setEnabled(true);
        this.mainTabbedPanel.setSelectedIndex(1);
        // build-type label
        String buildTypeText = canvasConfig.getBuildType().getLabel();
        switch (canvasConfig.getBuildType()) {
            case WHOLE_PROJECT_WITH_TEST_LIMITED:
            case WHOLE_PROJECT_WITH_TEST:
            case WHOLE_PROJECT_WITHOUT_TEST_LIMITED:
            case WHOLE_PROJECT_WITHOUT_TEST:
                this.buildTypeLabel.setText(buildTypeText);
                break;
            case MODULE_LIMITED:
            case MODULE:
                String moduleName = (String) this.moduleScopeComboBox.getSelectedItem();
                this.buildTypeLabel.setText(String.format("%s [%s]", buildTypeText, moduleName));
                break;
            case DIRECTORY_LIMITED:
            case DIRECTORY:
                String path = this.directoryScopeTextField.getText();
                this.buildTypeLabel.setText(String.format("%s [%s]", buildTypeText, path));
                break;
            case UPSTREAM:
            case DOWNSTREAM:
            case UPSTREAM_DOWNSTREAM:
                this.buildTypeLabel.setText(
                        String.format("%s of function [%s]", buildTypeText, this.clickedNode.getMethod().getName()));
            default:
                break;
        }
        // view 'package name' or 'file path' checkbox
        this.viewPackageNameCheckBox.setEnabled(false);
        this.viewFilePathCheckBox.setEnabled(false);
        // upstream/downstream buttons
        this.showOnlyUpstreamButton.setEnabled(false);
        this.showOnlyDownstreamButton.setEnabled(false);
        this.showOnlyUpstreamDownstreamButton.setEnabled(false);
        // progress bar
        this.loadingProgressBar.setVisible(true);
        // clear the canvas panel, ready for new graph
        this.canvasPanel.removeAll();
    }

    private void setupUiAfterRun(@NotNull Canvas canvas) {
        // show the rendered canvas
        canvas.setCanvasPanel(this.canvasPanel)
                .setCallGraphToolWindow(this);
        this.canvasPanel.add(canvas);
        this.canvasPanel.updateUI();
        // hide progress bar
        this.loadingProgressBar.setVisible(false);
        // reset some checkboxes
        this.viewPackageNameCheckBox.setEnabled(true);
        this.viewPackageNameCheckBox.setSelected(true);
        this.viewFilePathCheckBox.setEnabled(true);
        this.viewFilePathCheckBox.setSelected(false);
    }

    @NotNull
    private CanvasConfig.BuildType getSelectedBuildType() {
        boolean isLimitedScope = this.upstreamDownstreamScopeCheckbox.isSelected();
        if (this.projectScopeButton.isSelected()) {
            if (this.includeTestFilesCheckBox.isSelected()) {
                return isLimitedScope ?
                        CanvasConfig.BuildType.WHOLE_PROJECT_WITH_TEST_LIMITED :
                        CanvasConfig.BuildType.WHOLE_PROJECT_WITH_TEST;
            }
            return isLimitedScope ?
                    CanvasConfig.BuildType.WHOLE_PROJECT_WITHOUT_TEST_LIMITED :
                    CanvasConfig.BuildType.WHOLE_PROJECT_WITHOUT_TEST;
        } else if (this.moduleScopeButton.isSelected()) {
            return isLimitedScope ? CanvasConfig.BuildType.MODULE_LIMITED : CanvasConfig.BuildType.MODULE;
        } else if (this.directoryScopeButton.isSelected()) {
            return isLimitedScope ? CanvasConfig.BuildType.DIRECTORY_LIMITED : CanvasConfig.BuildType.DIRECTORY;
        }
        return CanvasConfig.BuildType.WHOLE_PROJECT_WITH_TEST;
    }
}
