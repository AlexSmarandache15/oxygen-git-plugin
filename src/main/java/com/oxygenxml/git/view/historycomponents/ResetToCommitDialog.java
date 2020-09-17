package com.oxygenxml.git.view.historycomponents;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.ButtonGroup;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextArea;
import javax.swing.text.JTextComponent;
import javax.swing.text.View;

import org.eclipse.jgit.api.ResetCommand.ResetType;

import com.oxygenxml.git.constants.UIConstants;
import com.oxygenxml.git.translator.Tags;
import com.oxygenxml.git.translator.Translator;
import com.oxygenxml.git.view.dialog.UIUtil;

import ro.sync.exml.workspace.api.PluginWorkspaceProvider;
import ro.sync.exml.workspace.api.standalone.ui.OKCancelDialog;

/**
 * Dialog used to reset the current branch to a specified commit.
 * 
 * @author Bogdan Draghici
 *
 */
public class ResetToCommitDialog extends OKCancelDialog {
  /**
   * Soft reset option label.
   */
  private static final String SOFT_RESET = "Soft - ";
  /**
   * Mixed reset option label.
   */
  private static final String MIXED_RESET = "Mixed - ";
  /**
   * Hard reset option label.
   */
  private static final String HARD_RESET = "Hard - ";
  /**
   * The soft reset option button.
   */
  private JRadioButton softResetButton;
  /**
   * The hard reset option button.
   */
  private JRadioButton hardResetButton;
  /**
   * The translator instance.
   */
  private static final Translator translator = Translator.getInstance();

  /**
   * Public constructor.
   * 
   * @param branchName The name of the current branch.
   * @param commitCharacteristics   The commit id to which to reset the branch.
   */
  public ResetToCommitDialog(String branchName, CommitCharacteristics commitCharacteristics) {
    super(
        PluginWorkspaceProvider.getPluginWorkspace() != null ? (JFrame) PluginWorkspaceProvider.getPluginWorkspace().getParentFrame() : null,
        translator.getTranslation(Tags.RESET_BRANCH_TO_COMMIT),
        true);

    // Create GUI
    JPanel panel = new JPanel(new GridBagLayout());
    createGUI(panel, branchName, commitCharacteristics);
    getContentPane().add(panel);
    setResizable(true);
    pack();

    if (PluginWorkspaceProvider.getPluginWorkspace() != null) {
      setLocationRelativeTo((JFrame) PluginWorkspaceProvider.getPluginWorkspace().getParentFrame());
    }
    setMinimumSize(new Dimension(400, 240));
  }

  /**
   * Adds the elements to the user interface
   * 
   * @param panel      The panel in which the components are added.
   * @param branchName The name of the current branch.
   * @param commitId   The commit id to which to reset the branch.
   */
  private void createGUI(JPanel panel, String branchName, CommitCharacteristics commitCharacteristics) {
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 0;
    gbc.weighty = 0;
    gbc.fill = GridBagConstraints.NONE;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.insets = new Insets(
        0,
        UIConstants.COMPONENT_LEFT_PADDING,
        UIConstants.COMPONENT_BOTTOM_PADDING,
        UIConstants.COMPONENT_RIGHT_PADDING);

    // Reset branch tag label.
    JLabel branchTagLabel = new JLabel(translator.getTranslation(Tags.BRANCH_NAME) + ": ");
    panel.add(branchTagLabel, gbc);
    
    // Branch name label.
    gbc.gridx++;
    JLabel branchNameLabel = new JLabel(branchName);
    panel.add(branchNameLabel, gbc);

    
    //Commit label
    JLabel commitLabel = new JLabel(translator.getTranslation(Tags.COMMIT) + ": ");
    gbc.gridx = 0;
    gbc.gridy++;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    panel.add(commitLabel, gbc);
    
    //Commit message area
    JTextArea commitMessageArea = UIUtil.createMessageArea(
        commitCharacteristics.getCommitAbbreviatedId() 
        + " - " + commitCharacteristics.getCommitMessage());
    commitMessageArea.setPreferredSize(new Dimension(360, computeHeight(commitMessageArea, 370, 500)));
    gbc.gridx++;
    gbc.weightx = 1;
    gbc.weighty = 1;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    panel.add(commitMessageArea, gbc);
    
    // Reset type label.
    JLabel resetModeLabel = new JLabel(translator.getTranslation(Tags.RESET_MODE) + ":");
    gbc.gridx = 0;
    gbc.gridy++;
    gbc.fill = GridBagConstraints.NONE;
    gbc.weightx = 0;
    gbc.weighty = 0;
    panel.add(resetModeLabel, gbc);

    addRadioButtons(panel, gbc);

    getOkButton().setText(translator.getTranslation(Tags.RESET));
  }

  /**
   * Adds the radio buttons with the reset options to the panel.
   * 
   * @param panel The panel in which the components are added.
   * @param gbc   The GridBagConstraints instance.
   */
  private void addRadioButtons(JPanel panel, GridBagConstraints gbc) {
    ButtonGroup buttonGroup = new ButtonGroup();
    gbc.insets = new Insets(0, 0, 0, 0);
    gbc.gridwidth = 2;
    
    softResetButton = new JRadioButton(SOFT_RESET + translator.getTranslation(Tags.SOFT_RESET_INFO));
    gbc.gridy++;
    buttonGroup.add(softResetButton);
    panel.add(softResetButton, gbc);

    JRadioButton mixedResetButton = new JRadioButton(MIXED_RESET + translator.getTranslation(Tags.MIXED_RESET_INFO));
    gbc.gridy++;
    buttonGroup.add(mixedResetButton);
    panel.add(mixedResetButton, gbc);

    hardResetButton = new JRadioButton(HARD_RESET + translator.getTranslation(Tags.HARD_RESET_INFO));
    gbc.gridy++;
    buttonGroup.add(hardResetButton);
    panel.add(hardResetButton, gbc);

    mixedResetButton.setSelected(true);
  }

  /**
   * Compute necessary height for text area with <b>line wrap and wrap style word</b>
   *  to display contained all rows.
   *
   * @param ta The text area.
   * @param width The fixed width of the text area.
   * @param maxHeight The maximum allowed height.
   * @return The display height.
   */
  public static int computeHeight(JTextComponent ta, int width, int maxHeight) {
    View view = ta.getUI().getRootView(ta);
    view.setSize(width, 0);
    int height = (int) view.getPreferredSpan(View.Y_AXIS);
   
    Insets insets = ta.getInsets();
    if (insets != null) {
      height += insets.top + insets.bottom;      
    }
   
    if (maxHeight < height) {
      return maxHeight;
    }
    return height;
  }
  
  /**
   * Gets the reset type chosen.
   * 
   * @return The reset type. ResetType.MIXED by default.
   */
  public ResetType getResetType() {
    ResetType resetType = ResetType.MIXED;
    if (softResetButton.isSelected()) {
      resetType = ResetType.SOFT;
    } else if (hardResetButton.isSelected()) {
      resetType = ResetType.HARD;
    }
    return resetType;
  }
}
