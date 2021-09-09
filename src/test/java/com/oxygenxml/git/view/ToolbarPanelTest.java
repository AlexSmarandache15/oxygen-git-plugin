package com.oxygenxml.git.view;

import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;

import com.oxygenxml.git.service.GitAccess;
import com.oxygenxml.git.service.GitTestBase;
import com.oxygenxml.git.service.TestUtil;
import com.oxygenxml.git.service.entities.FileStatus;
import com.oxygenxml.git.service.entities.GitChangeType;
import com.oxygenxml.git.translator.Tags;
import com.oxygenxml.git.view.event.GitController;
import com.oxygenxml.git.view.staging.StagingPanel;
import com.oxygenxml.git.view.staging.ToolbarPanel;
import com.oxygenxml.git.view.stash.FilesTableModel;
import com.oxygenxml.git.view.stash.ListStashesDialog;

import ro.sync.exml.workspace.api.standalone.ui.SplitMenuButton;

/**
 * Toolbar panel tests.
 */
public class ToolbarPanelTest extends GitTestBase {

  private final static String LOCAL_REPO = "target/test-resources/GitAccessCheckoutNewBranch/localRepository";
  private final static String REMOTE_REPO = "target/test-resources/GitAccessCheckoutNewBranch/remoteRepository";
  private final static String LOCAL_BRANCH = "LocalBranch";

  private GitAccess gitAccess;
  private StagingPanel stagingPanel;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    
    gitAccess = GitAccess.getInstance();

    //Creates the remote repository.
    createRepository(REMOTE_REPO);
    Repository remoteRepository = gitAccess.getRepository();

    //Creates the local repository.
    createRepository(LOCAL_REPO);
    Repository localRepository = gitAccess.getRepository();

    bindLocalToRemote(localRepository, remoteRepository);
  }
  

  /**
   * <p><b>Description:</b> when trying to switch to another branch from the branches menu
   * and the checkout fails, keep the previous branch selected.</p>
   * <p><b>Bug ID:</b> EXM-46826</p>
   *
   * @author sorin_carbunaru
   *
   * @throws Exception
   */
  public void testKeepCurrentBranchSelectedWhenSwitchFails() throws Exception {
    //Make the first commit for the local repository
    File file = new File(LOCAL_REPO, "local.txt");
    file.createNewFile();
    setFileContent(file, "local content");
    gitAccess.add(new FileStatus(GitChangeType.ADD, "local.txt"));
    gitAccess.commit("First local commit.");

    //Make the first commit for the remote repository and create a branch for it.
    gitAccess.setRepositorySynchronously(REMOTE_REPO);
    file = new File(REMOTE_REPO, "remote1.txt");
    file.createNewFile();
    setFileContent(file, "remote content");
    gitAccess.add(new FileStatus(GitChangeType.ADD, "remote1.txt"));
    gitAccess.commit("First remote commit.");

    // Switch back to local repo and create local branch
    gitAccess.setRepositorySynchronously(LOCAL_REPO);
    gitAccess.createBranch(LOCAL_BRANCH);
    gitAccess.fetch();

    JFrame frame = new JFrame();
    try {
      // Init UI
      GitController gitCtrl = new GitController(GitAccess.getInstance());
      stagingPanel = new StagingPanel(refreshSupport, gitCtrl, null, null);
      JComboBox<String> wcCombo = stagingPanel.getWorkingCopySelectionPanel().getWorkingCopyCombo();
      String wcPath = new File(LOCAL_REPO).getAbsolutePath();
      wcCombo.addItem(wcPath);
      wcCombo.setSelectedItem(wcPath);
      frame.getContentPane().add(stagingPanel);
      frame.pack();
      frame.setVisible(true);
      stagingPanel.getToolbarPanel().refresh();
      refreshSupport.call();
      flushAWT();
      
      // Commit a change
      file = new File(LOCAL_REPO, "local.txt");
      setFileContent(file, "new 2");
      gitAccess.add(new FileStatus(GitChangeType.ADD, "local.txt"));
      gitAccess.commit("First remote commit.");
      flushAWT();
      
      // Create local change
      setFileContent(file, "new 3");
      refreshSupport.call();
      flushAWT();
      
      // Try to switch to another branch
      SplitMenuButton branchSplitMenuButton = stagingPanel.getToolbarPanel().getBranchSplitMenuButton();
      branchSplitMenuButton.setPopupMenuVisible(true);
      final JRadioButtonMenuItem firstItem = (JRadioButtonMenuItem) branchSplitMenuButton.getMenuComponent(0);
      
      SwingUtilities.invokeLater(() -> {
        firstItem.setSelected(true);
        firstItem.getAction().actionPerformed(null);
      });
      
      sleep(500);
      Window focusedWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
      
      JButton yesButton = TestUtil.findButton(focusedWindow, translator.getTranslation(Tags.MOVE_CHANGES));
      yesButton.doClick();
      
      sleep(500);
      
      branchSplitMenuButton.setPopupMenuVisible(false);
      flushAWT();
      
      // The switch should have failed, and the selected branch shouldn't have changed
      branchSplitMenuButton.setPopupMenuVisible(true);
      flushAWT();
      JRadioButtonMenuItem firstItem2 = (JRadioButtonMenuItem) branchSplitMenuButton.getMenuComponent(0);
      assertFalse(firstItem2.isSelected());
      
    } finally {
      frame.setVisible(false);
      frame.dispose();
    }
  }
  
  
  /**
   * <p><b>Description:</b> Tests the "Stash" button basic characteristics and the "Stash Changes" functionality.</p>
   * <p><b>Bug ID:</b> EXM-45983</p>
   *
   * @author Alex_Smarandache
   *
   * @throws Exception
   */ 
  public void testStashChanges() throws Exception {
  //Make the first commit for the local repository
    File file = new File(LOCAL_REPO, "local.txt");
    file.createNewFile();
    setFileContent(file, "local content");
    gitAccess.add(new FileStatus(GitChangeType.ADD, "local.txt"));
    gitAccess.commit("First local commit.");

    //Make the first commit for the remote repository and create a branch for it.
    gitAccess.setRepositorySynchronously(REMOTE_REPO);
    file = new File(REMOTE_REPO, "remote1.txt");
    file.createNewFile();
    setFileContent(file, "remote content");
    gitAccess.add(new FileStatus(GitChangeType.ADD, "remote1.txt"));
    gitAccess.commit("First remote commit.");

    // Switch back to local repo and create local branch
    gitAccess.setRepositorySynchronously(LOCAL_REPO);
    gitAccess.createBranch(LOCAL_BRANCH);
    gitAccess.fetch();
    
    
    JFrame frame = new JFrame();
   
    try {
      // Init UI
      GitController gitCtrl = new GitController(GitAccess.getInstance());
      stagingPanel = new StagingPanel(refreshSupport, gitCtrl, null, null);
      ToolbarPanel toolbarPanel = stagingPanel.getToolbarPanel();
      frame.getContentPane().add(stagingPanel);
      frame.pack();
      frame.setVisible(true);
      flushAWT();
      toolbarPanel.refresh();
      refreshSupport.call();
      flushAWT();
      
      SplitMenuButton stashButton = toolbarPanel.getStashButton();
      // Test the "Stash" button tooltip text
      assertEquals(Tags.STASH, stashButton.getToolTipText());
      
      // Test if the button is disabled if none actions are possible.
      assertFalse(stashButton.isEnabled());
      
      makeLocalChange("new 2");
      
      JMenuItem stashChangesItem =  stashButton.getItem(0);
      
      SwingUtilities.invokeLater(() -> {
        stashChangesItem.setSelected(true);
        stashChangesItem.getAction().actionPerformed(null);
      });
      
      sleep(500);
      
      // Stash changes and test if the actions become disabled.
      JDialog stashChangesDialog = findDialog(Tags.STASH_CHANGES);
      assertNotNull(stashChangesDialog);
      flushAWT();
      JButton doStashButton = findFirstButton(stashChangesDialog, Tags.STASH);
      assertNotNull(doStashButton);
      doStashButton.doClick();
      refreshSupport.call();
      flushAWT();
      toolbarPanel.refreshStashButton();
      flushAWT();
      assertFalse(stashChangesItem.isEnabled());
      
      // Test if the stash were created.
      List<RevCommit> stashes = new ArrayList<>(gitAccess.listStashes());
      assertEquals(1, stashes.size());
      
      makeLocalChange("new 3");
      
      SwingUtilities.invokeLater(() -> {
        stashChangesItem.setSelected(true);
        stashChangesItem.getAction().actionPerformed(null);
      });
      
      sleep(500);
      
      // Test if the user can add a custom text
      stashChangesDialog = findDialog(Tags.STASH_CHANGES);
      assertNotNull(stashChangesDialog);
      flushAWT();
      JTextField textField = TestUtil.findFirstTextField(stashChangesDialog);
      assertNotNull(textField);
      textField.setText("Some custom text by user.");
      flushAWT();
      doStashButton = findFirstButton(stashChangesDialog, Tags.STASH);
      assertNotNull(doStashButton);
      doStashButton.doClick();
      refreshSupport.call();
      flushAWT();
      toolbarPanel.refreshStashButton();
      stashes = new ArrayList<>(gitAccess.listStashes());
      assertEquals(2, stashes.size());
      assertEquals("Some custom text by user.", stashes.get(0).getFullMessage());
      
      makeLocalChange("new 4");
      
      SwingUtilities.invokeLater(() -> {
        stashChangesItem.setSelected(true);
        stashChangesItem.getAction().actionPerformed(null);
      });
      
      sleep(500);
      
      // Stash changes and test if the actions become disabled.
      stashChangesDialog = findDialog(Tags.STASH_CHANGES);
      assertNotNull(stashChangesDialog);
      flushAWT();
      JButton cancelStashButton = findFirstButton(stashChangesDialog, Tags.CANCEL);
      assertNotNull(cancelStashButton);
      cancelStashButton.doClick();
      refreshSupport.call();
      flushAWT();
      toolbarPanel.refreshStashButton();
      flushAWT();
      assertTrue(stashChangesItem.isEnabled());
      
      // Test if the stash wasn't created.
      stashes = new ArrayList<>(gitAccess.listStashes());
      assertEquals(2, stashes.size());
      
    } finally {
      frame.setVisible(false);
      frame.dispose();
    }
  }
  
  
  /**
   * <p><b>Description:</b> Tests the "List stash" delete all action</p>
   * <p><b>Bug ID:</b> EXM-45983</p>
   *
   * @author Alex_Smarandache
   *
   * @throws Exception
   */ 
  public void testListStashDeleteAllAction() throws Exception {
    // Make the first commit for the local repository
    File file = new File(LOCAL_REPO, "local.txt");
    file.createNewFile();
    setFileContent(file, "local content");
    gitAccess.add(new FileStatus(GitChangeType.ADD, "local.txt"));
    gitAccess.commit("First local commit.");

    // Make the first commit for the remote repository and create a branch for it.
    gitAccess.setRepositorySynchronously(REMOTE_REPO);
    file = new File(REMOTE_REPO, "remote1.txt");
    file.createNewFile();
    setFileContent(file, "remote content");
    gitAccess.add(new FileStatus(GitChangeType.ADD, "remote1.txt"));
    gitAccess.commit("First remote commit.");

    // Switch back to local repo and create local branch
    gitAccess.setRepositorySynchronously(LOCAL_REPO);
    gitAccess.createBranch(LOCAL_BRANCH);
    gitAccess.fetch();
    
    
    JFrame frame = new JFrame();
   
    try {
      // Init UI
      GitController gitCtrl = new GitController(GitAccess.getInstance());
      stagingPanel = new StagingPanel(refreshSupport, gitCtrl, null, null);
      ToolbarPanel toolbarPanel = stagingPanel.getToolbarPanel();
      frame.getContentPane().add(stagingPanel);
      frame.pack();
      frame.setVisible(true);
      flushAWT();
      toolbarPanel.refresh();
      refreshSupport.call();
      flushAWT();
      
      SplitMenuButton stashButton = toolbarPanel.getStashButton();
    
      initStashes(toolbarPanel);
      
      List<RevCommit> stashes = new ArrayList<>(gitAccess.listStashes());
      assertEquals(3, stashes.size());
      
      JMenuItem[] listStashesItem =  new JMenuItem[1];
      listStashesItem[0] = stashButton.getItem(1);

      SwingUtilities.invokeLater(() -> {
        listStashesItem[0].setSelected(true);
        listStashesItem[0].getAction().actionPerformed(null);
      });

      ListStashesDialog listStashesDialog = (ListStashesDialog)findDialog(Tags.STASHES);
      assertNotNull(listStashesDialog);
      assertEquals(3, listStashesDialog.getStashesTable().getModel().getRowCount());
      assertEquals(1, listStashesDialog.getAffectedFilesTable().getModel().getRowCount());
      JButton[] deleteAllStashesButton = new JButton[1];
      deleteAllStashesButton[0] = findFirstButton(listStashesDialog, Tags.DELETE_ALL);
      assertNotNull(deleteAllStashesButton);
      SwingUtilities.invokeLater(() -> deleteAllStashesButton[0].doClick());
      
      flushAWT();
      
      // Test the no button.
      JDialog deleteAllStashesDialog = findDialog(Tags.DELETE_ALL_STASHES);
      assertNotNull(deleteAllStashesDialog);
      JButton[] noButton = new JButton[1];
      flushAWT();
      noButton[0] = findFirstButton(deleteAllStashesDialog, Tags.NO);
      assertNotNull(noButton[0]);
      SwingUtilities.invokeLater(() -> noButton[0].doClick());
      flushAWT();
      stashes = new ArrayList<>(gitAccess.listStashes());
      assertEquals(3, stashes.size());
      flushAWT();
      JButton cancelButton = findFirstButton(listStashesDialog, Tags.CLOSE);
      assertNotNull(cancelButton);
      cancelButton.doClick();
      
      listStashesItem[0] =  stashButton.getItem(1);

      SwingUtilities.invokeLater(() -> {
        listStashesItem[0].setSelected(true);
        listStashesItem[0].getAction().actionPerformed(null);
      });
      
      flushAWT();
      listStashesDialog = (ListStashesDialog)findDialog(Tags.STASHES);
      assertNotNull(listStashesDialog);
      deleteAllStashesButton[0] = findFirstButton(listStashesDialog, Tags.DELETE_ALL);
      assertNotNull(deleteAllStashesButton);
      SwingUtilities.invokeLater(() -> deleteAllStashesButton[0].doClick());
      flushAWT();
      
      // Test the yes button.
      deleteAllStashesDialog = findDialog(Tags.DELETE_ALL_STASHES);
      assertNotNull(deleteAllStashesDialog);
      flushAWT();
      JButton yesButton = findFirstButton(deleteAllStashesDialog, Tags.YES);
      assertNotNull(yesButton);
      SwingUtilities.invokeLater(yesButton::doClick);
      flushAWT();
      stashes = new ArrayList<>(gitAccess.listStashes());
      assertEquals(0, stashes.size());
      
      cancelButton = findFirstButton(listStashesDialog, Tags.CLOSE);
      assertNotNull(cancelButton);
      cancelButton.doClick();
      
    } finally {
      frame.setVisible(false);
      frame.dispose();
    }
  }
  
  
  /**
   * <p><b>Description:</b> Tests the "List stash" delete action</p>
   * <p><b>Bug ID:</b> EXM-45983</p>
   *
   * @author Alex_Smarandache
   *
   * @throws Exception
   */ 
  public void testListStashDeleteAction() throws Exception {
    // Make the first commit for the local repository
    File file = new File(LOCAL_REPO, "local.txt");
    file.createNewFile();
    setFileContent(file, "local content");
    gitAccess.add(new FileStatus(GitChangeType.ADD, "local.txt"));
    gitAccess.commit("First local commit.");

    // Make the first commit for the remote repository and create a branch for it.
    gitAccess.setRepositorySynchronously(REMOTE_REPO);
    file = new File(REMOTE_REPO, "remote1.txt");
    file.createNewFile();
    setFileContent(file, "remote content");
    gitAccess.add(new FileStatus(GitChangeType.ADD, "remote1.txt"));
    gitAccess.commit("First remote commit.");

    // Switch back to local repo and create local branch
    gitAccess.setRepositorySynchronously(LOCAL_REPO);
    gitAccess.createBranch(LOCAL_BRANCH);
    gitAccess.fetch();
    
    
    JFrame frame = new JFrame();
   
    try {
      // Init UI
      GitController gitCtrl = new GitController(GitAccess.getInstance());
      stagingPanel = new StagingPanel(refreshSupport, gitCtrl, null, null);
      ToolbarPanel toolbarPanel = stagingPanel.getToolbarPanel();
      frame.getContentPane().add(stagingPanel);
      frame.pack();
      frame.setVisible(true);
      flushAWT();
      toolbarPanel.refresh();
      refreshSupport.call();
      flushAWT();
      
      SplitMenuButton stashButton = toolbarPanel.getStashButton();
    
      initStashes(toolbarPanel);
      
      List<RevCommit> stashes = new ArrayList<>(gitAccess.listStashes());
      assertEquals(3, stashes.size());
      
      JMenuItem[] listStashesItem =  new JMenuItem[1];
      listStashesItem[0] = stashButton.getItem(1);

      SwingUtilities.invokeLater(() -> {
        listStashesItem[0].setSelected(true);
        listStashesItem[0].getAction().actionPerformed(null);
      });
      
      ListStashesDialog listStashesDialog = (ListStashesDialog) findDialog(Tags.STASHES);
      assertNotNull(listStashesDialog);
      assertEquals(3, listStashesDialog.getStashesTable().getModel().getRowCount());
      JButton[] deleteSelectedStashButton = new JButton[1];
      flushAWT();
      deleteSelectedStashButton[0] = findFirstButton(listStashesDialog, Tags.DELETE);
      assertNotNull(deleteSelectedStashButton);
      SwingUtilities.invokeLater(() -> deleteSelectedStashButton[0].doClick());
      
      flushAWT();
      
      // Test the no button.
      JDialog deleteSelectedStashDialog = findDialog(Tags.DELETE_STASH);
      assertNotNull(deleteSelectedStashDialog);
      JButton[] noButton = new JButton[1];
      flushAWT();
      noButton[0] = findFirstButton(deleteSelectedStashDialog, Tags.NO);
      assertNotNull(noButton[0]);
      SwingUtilities.invokeLater(() -> noButton[0].doClick());
      flushAWT();
      stashes = new ArrayList<>(gitAccess.listStashes());
      assertEquals(3, stashes.size());
      
     listStashesItem[0] =  stashButton.getItem(1);

      SwingUtilities.invokeLater(() -> {
        listStashesItem[0].setSelected(true);
        listStashesItem[0].getAction().actionPerformed(null);
      });
      
      // Test the yes button.
      String[] stashesMessages = {
          "Stash1", "Stash0"
      };
      // Delete all stashes one by one
      for(int i = 0; i < 3; i++) {
        flushAWT();
        deleteSelectedStashButton[0] = findFirstButton(listStashesDialog, Tags.DELETE);
        assertNotNull(deleteSelectedStashButton);
        SwingUtilities.invokeLater(() -> deleteSelectedStashButton[0].doClick());
        flushAWT();
        deleteSelectedStashDialog = findDialog(Tags.DELETE_STASH);
        assertNotNull(deleteSelectedStashDialog);
        flushAWT();
        JButton yesButton = findFirstButton(deleteSelectedStashDialog, Tags.YES);
        assertNotNull(yesButton);
        SwingUtilities.invokeLater(yesButton::doClick);
        flushAWT();
        stashes = new ArrayList<>(gitAccess.listStashes());
        assertEquals(3 - i - 1, stashes.size());
        assertEquals(3 - i - 1, listStashesDialog.getStashesTable().getRowCount());
        int stashIndex = 0;
        for(int j = i + 1; j < 3; j++) {
          assertEquals(stashesMessages[i + stashIndex], 
              stashes.get(stashIndex).getFullMessage());
          assertEquals(stashesMessages[i + stashIndex], 
              listStashesDialog.getStashesTable().getValueAt(stashIndex++, 1));
        }
      }
      
      flushAWT();
      JButton cancelButton = findFirstButton(listStashesDialog, Tags.CLOSE);
      assertNotNull(cancelButton);
      cancelButton.doClick();
      
    } finally {
      frame.setVisible(false);
      frame.dispose();
    }
  }
  
  
  /**
   * <p><b>Description:</b> Tests the "List stash" apply and pop actions</p>
   * <p><b>Bug ID:</b> EXM-45983</p>
   *
   * @author Alex_Smarandache
   *
   * @throws Exception
   */ 
  public void testListStashApplyAndPopAction() throws Exception {
    // Make the first commit for the local repository
    File file = new File(LOCAL_REPO, "local.txt");
    file.createNewFile();
    setFileContent(file, "local content");
    gitAccess.add(new FileStatus(GitChangeType.ADD, "local.txt"));
    gitAccess.commit("First local commit.");

    // Make the first commit for the remote repository and create a branch for it.
    gitAccess.setRepositorySynchronously(REMOTE_REPO);
    file = new File(REMOTE_REPO, "remote1.txt");
    file.createNewFile();
    setFileContent(file, "remote content");
    gitAccess.add(new FileStatus(GitChangeType.ADD, "remote1.txt"));
    gitAccess.commit("First remote commit.");

    // Switch back to local repo and create local branch
    gitAccess.setRepositorySynchronously(LOCAL_REPO);
    gitAccess.createBranch(LOCAL_BRANCH);
    gitAccess.fetch();
    
    
    JFrame frame = new JFrame();
   
    try {
      // Init UI
      GitController gitCtrl = new GitController(GitAccess.getInstance());
      stagingPanel = new StagingPanel(refreshSupport, gitCtrl, null, null);
      ToolbarPanel toolbarPanel = stagingPanel.getToolbarPanel();
      frame.getContentPane().add(stagingPanel);
      frame.pack();
      frame.setVisible(true);
      flushAWT();
      toolbarPanel.refresh();
      refreshSupport.call();
      flushAWT();
      
      SplitMenuButton stashButton = toolbarPanel.getStashButton();
      
      makeLocalChange("some_modification");
      toolbarPanel.refreshStashButton();
      flushAWT();
      
      JMenuItem[] stashChangesItem = new JMenuItem[1];
      stashChangesItem[0] = stashButton.getItem(0);
      
      SwingUtilities.invokeLater(() -> {
        stashChangesItem[0].setSelected(true);
        stashChangesItem[0].getAction().actionPerformed(null);
      });
      
      sleep(500);
      
      // Stash changes and test if the actions become disabled.
      JDialog stashChangesDialog = findDialog(Tags.STASH_CHANGES);
      flushAWT();
      assertNotNull(stashChangesDialog);
      JButton doStashButton = findFirstButton(stashChangesDialog, Tags.STASH);
      assertNotNull(doStashButton);
      doStashButton.doClick();
      refreshSupport.call();
      toolbarPanel.refreshStashButton();
      flushAWT();
      
      assertEquals("local content", getFileContent(LOCAL_REPO + "/local.txt"));
      
      JMenuItem[] listStashesItem =  new JMenuItem[1];
      listStashesItem[0] = stashButton.getItem(1);

      SwingUtilities.invokeLater(() -> {
        listStashesItem[0].setSelected(true);
        listStashesItem[0].getAction().actionPerformed(null);
      });
      
      flushAWT();
      ListStashesDialog listStashesDialog = (ListStashesDialog) findDialog(Tags.STASHES);
      flushAWT();
      assertNotNull(listStashesDialog);
      JCheckBox[] popStashCheckBox = new JCheckBox[1];
      flushAWT();
      popStashCheckBox[0] = findCheckBox(listStashesDialog, Tags.DELETE_STASH_AFTER_APPLIED);
      assertNotNull(popStashCheckBox[0]);
      SwingUtilities.invokeLater(() -> popStashCheckBox[0].setSelected(true));
      flushAWT();
      
      List<RevCommit> stashes = new ArrayList<>(gitAccess.listStashes());
      assertFalse(stashes.isEmpty());
      assertEquals(1, listStashesDialog.getStashesTable().getRowCount());
      
      JButton[] applyButton = new JButton[1];
      flushAWT();
      applyButton[0] = findFirstButton(listStashesDialog, Tags.APPLY);
      assertNotNull(applyButton[0]);
      SwingUtilities.invokeLater(() -> applyButton[0].doClick());
      flushAWT();
      
      // Check if the stash was been deleted and applied
      stashes = new ArrayList<>(gitAccess.listStashes());
      assertTrue(stashes.isEmpty());
      assertEquals(0, listStashesDialog.getStashesTable().getRowCount());
      assertEquals("some_modification", getFileContent(LOCAL_REPO + "/local.txt"));
      
      JButton cancelButton = findFirstButton(listStashesDialog, Tags.CLOSE);
      assertNotNull(cancelButton);
      cancelButton.doClick();

      stashChangesItem[0] = stashButton.getItem(0);
      
      SwingUtilities.invokeLater(() -> {
        stashChangesItem[0].setSelected(true);
        stashChangesItem[0].getAction().actionPerformed(null);
      });
      
      sleep(500);
      
      stashChangesDialog = findDialog(Tags.STASH_CHANGES);
      assertNotNull(stashChangesDialog);
      doStashButton = findFirstButton(stashChangesDialog, Tags.STASH);
      assertNotNull(doStashButton);
      doStashButton.doClick();
      refreshSupport.call();
      toolbarPanel.refreshStashButton();
      flushAWT();
      
      assertEquals("local content", getFileContent(LOCAL_REPO + "/local.txt"));
      listStashesItem[0] = stashButton.getItem(1);

      SwingUtilities.invokeLater(() -> {
        listStashesItem[0].setSelected(true);
        listStashesItem[0].getAction().actionPerformed(null);
      });
      
      listStashesDialog = (ListStashesDialog) findDialog(Tags.STASHES);
      assertNotNull(listStashesDialog);
      popStashCheckBox[0] = findCheckBox(listStashesDialog, Tags.DELETE_STASH_AFTER_APPLIED);
      assertNotNull(popStashCheckBox[0]);
      SwingUtilities.invokeLater(() -> popStashCheckBox[0].setSelected(false));
      flushAWT();
      
      applyButton[0] = findFirstButton(listStashesDialog, Tags.APPLY);
      assertNotNull(applyButton[0]);
      SwingUtilities.invokeLater(() -> applyButton[0].doClick());
      flushAWT();
      
      stashes = new ArrayList<>(gitAccess.listStashes());
      assertFalse(stashes.isEmpty());
      assertEquals(1, listStashesDialog.getStashesTable().getRowCount());
      assertEquals("some_modification", getFileContent(LOCAL_REPO + "/local.txt"));
      
      cancelButton = findFirstButton(listStashesDialog, Tags.CLOSE);
      assertNotNull(cancelButton);
      cancelButton.doClick();
           
    } finally {
      frame.setVisible(false);
      frame.dispose();
    }
  }
  
  
  /**
   * <p><b>Description:</b> Tests the "List stash" affected files table</p>
   * <p><b>Bug ID:</b> EXM-45983</p>
   *
   * @author Alex_Smarandache
   *
   * @throws Exception
   */ 
  public void testListStashAffectedFilesTable() throws Exception {
    // Make the first commit for the local repository
    File file = new File(LOCAL_REPO, "local.txt");
    file.createNewFile();
    setFileContent(file, "local content");
    gitAccess.add(new FileStatus(GitChangeType.ADD, "local.txt"));
    gitAccess.commit("First local commit.");

    // Make the first commit for the remote repository and create a branch for it.
    gitAccess.setRepositorySynchronously(REMOTE_REPO);
    file = new File(REMOTE_REPO, "remote1.txt");
    file.createNewFile();
    setFileContent(file, "remote content");
    gitAccess.add(new FileStatus(GitChangeType.ADD, "remote1.txt"));
    gitAccess.commit("First remote commit.");

    // Switch back to local repo and create local branch
    gitAccess.setRepositorySynchronously(LOCAL_REPO);
    gitAccess.createBranch(LOCAL_BRANCH);
    gitAccess.fetch();
    
    
    JFrame frame = new JFrame();
   
    try {
      // Init UI
      GitController gitCtrl = new GitController(GitAccess.getInstance());
      stagingPanel = new StagingPanel(refreshSupport, gitCtrl, null, null);
      ToolbarPanel toolbarPanel = stagingPanel.getToolbarPanel();
      frame.getContentPane().add(stagingPanel);
      frame.pack();
      frame.setVisible(true);
      flushAWT();
      toolbarPanel.refresh();
      refreshSupport.call();
      flushAWT();
      
      SplitMenuButton stashButton = toolbarPanel.getStashButton();
      
      String[] filesNames = {
          "local.txt",
          "local1.txt",
          "local2.txt",
          "local3.txt",
          "local4.txt",
      };
      
      makeLocalChange("some_modification");
      for (int i = 1; i <= 4; i++) {
        file = new File(LOCAL_REPO, filesNames[i]);
        file.createNewFile();
        setFileContent(file, "local content" + i);
        gitAccess.add(new FileStatus(GitChangeType.ADD, filesNames[i]));
      }
      toolbarPanel.refreshStashButton();
      flushAWT();
      
      JMenuItem[] stashChangesItem = new JMenuItem[1];
      stashChangesItem[0] = stashButton.getItem(0);
      
      SwingUtilities.invokeLater(() -> {
        stashChangesItem[0].setSelected(true);
        stashChangesItem[0].getAction().actionPerformed(null);
      });
      
      sleep(500);
      
      JDialog stashChangesDialog = findDialog(Tags.STASH_CHANGES);
      flushAWT();
      assertNotNull(stashChangesDialog);
      JButton doStashButton = findFirstButton(stashChangesDialog, Tags.STASH);
      flushAWT();
      assertNotNull(doStashButton);
      doStashButton.doClick();
      refreshSupport.call();
      toolbarPanel.refreshStashButton();
      flushAWT();
      
      makeLocalChange("another_modification");
      toolbarPanel.refreshStashButton();
      flushAWT();
      
      stashChangesItem[0] = stashButton.getItem(0);
      
      SwingUtilities.invokeLater(() -> {
        stashChangesItem[0].setSelected(true);
        stashChangesItem[0].getAction().actionPerformed(null);
      });
      
      sleep(500);
      
      stashChangesDialog = findDialog(Tags.STASH_CHANGES);
      flushAWT();
      assertNotNull(stashChangesDialog);
      doStashButton = findFirstButton(stashChangesDialog, Tags.STASH);
      assertNotNull(doStashButton);
      doStashButton.doClick();
      refreshSupport.call();
      toolbarPanel.refreshStashButton();
      flushAWT();
      
      JMenuItem[] listStashesItem =  new JMenuItem[1];
      listStashesItem[0] = stashButton.getItem(1);

      SwingUtilities.invokeLater(() -> {
        listStashesItem[0].setSelected(true);
        listStashesItem[0].getAction().actionPerformed(null);
      });
      
      ListStashesDialog listStashesDialog = (ListStashesDialog) findDialog(Tags.STASHES);
      flushAWT();
      assertNotNull(listStashesDialog);
      FilesTableModel filesTableModel = (FilesTableModel) listStashesDialog.getAffectedFilesTable().getModel();
      assertEquals(GitChangeType.CHANGED, filesTableModel.getValueAt(0, 0));
      assertEquals(filesNames[0], ((FileStatus)filesTableModel.getValueAt(0, 1)).getFileLocation());

      filesTableModel = (FilesTableModel) listStashesDialog.getAffectedFilesTable().getModel();
      SwingUtilities.invokeLater(() -> {
        listStashesDialog.getStashesTable().setRowSelectionInterval(1, 1);
      });
      flushAWT();
      assertEquals(GitChangeType.CHANGED, filesTableModel.getValueAt(0, 0));
      assertEquals(filesNames[0], ((FileStatus)filesTableModel.getValueAt(0, 1)).getFileLocation());
      for (int i = 1; i < filesNames.length; i++) {
        assertEquals(GitChangeType.ADD, filesTableModel.getValueAt(i, 0));
        assertEquals(filesNames[i], ((FileStatus)filesTableModel.getValueAt(i, 1)).getFileLocation());
      }
      flushAWT();

      filesTableModel = (FilesTableModel) listStashesDialog.getAffectedFilesTable().getModel();
      SwingUtilities.invokeLater(() -> {
        listStashesDialog.getStashesTable().setRowSelectionInterval(0, 0);
      });
      flushAWT();
      assertEquals(GitChangeType.CHANGED, filesTableModel.getValueAt(0, 0));
      assertEquals(filesNames[0], ((FileStatus)filesTableModel.getValueAt(0, 1)).getFileLocation());
            
      JButton cancelButton = findFirstButton(listStashesDialog, Tags.CLOSE);
      assertNotNull(cancelButton);
      cancelButton.doClick();
           
    } finally {
      frame.setVisible(false);
      frame.dispose();
    }
  }
  
  
  /**
   * Make local change in file "local.txt" file with the "text" that will be the new file content. 
   * 
   * @param text         The new file text.
   * 
   * @author Alex_Smarandache
   * 
   * @throws Exception
   */
  private void makeLocalChange(String text) throws Exception {
    File file = new File(LOCAL_REPO, "local.txt");
    setFileContent(file, text);
    gitAccess.add(new FileStatus(GitChangeType.ADD, "local.txt"));
    refreshSupport.call();
    stagingPanel.getToolbarPanel().refreshStashButton();
    flushAWT();    
  }

  
  /**
   * Initialise 3 stashes.
   * 
   * @param toolbarPanel The toolbar Panel.
   * 
   * @author Alex_Smarandache
   * 
   * @throws Exception
   */
  private void initStashes(ToolbarPanel toolbarPanel) throws Exception {
    SplitMenuButton stashButton = toolbarPanel.getStashButton();
    
    String[] stashesMessages = {
        "Stash0", "Stash1", "Stash2",
    };
    
    for (String stashMessage : stashesMessages) {
      makeLocalChange(stashMessage);
      
      JMenuItem stashChangesItem =  stashButton.getItem(0);
      
      SwingUtilities.invokeLater(() -> {
        stashChangesItem.setSelected(true);
        stashChangesItem.getAction().actionPerformed(null);
      });
      
      flushAWT();
      
      // Test if the user can add a custom text
      JDialog stashChangesDialog = findDialog(Tags.STASH_CHANGES);
      assertNotNull(stashChangesDialog);
      JTextField textField = TestUtil.findFirstTextField(stashChangesDialog);
      assertNotNull(textField);
      textField.setText(stashMessage);
      flushAWT();
      JButton doStashButton = findFirstButton(stashChangesDialog, Tags.STASH);
      assertNotNull(doStashButton);
      doStashButton.doClick();
      refreshSupport.call();
      flushAWT();
      toolbarPanel.refreshStashButton();
    }
  }
  
  
  /**
   * Get the content for a file.
   * 
   * @return The content.
   * 
   * @author Alex_Smarandache
   * 
   * @throws Exception
   */
  private String getFileContent(String path)  {
    
    String content = "";
    
    try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
      content = reader.lines().collect(Collectors.joining(System.lineSeparator()));
    } catch (IOException e) {
      e.printStackTrace();
    } 
    
    return content;     
  }
  
}
