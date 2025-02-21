package com.oxygenxml.git.view.staging;

import java.awt.Dimension;
import java.io.File;

import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import com.oxygenxml.git.view.staging.ChangesPanel.ResourcesViewMode;

/**
 * Test cases for the Truncate filepaths in Git Staging 
 * 
 * @author gabriel_nedianu
 */
public class FlatView11Test extends FlatViewTestBase {

  /**
   * Logger for logging.
   */
  @SuppressWarnings("unused")
  private static final Logger logger = LogManager.getLogger(FlatView9Test.class.getName());

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();

    stagingPanel.getUnstagedChangesPanel().setResourcesViewMode(ResourcesViewMode.FLAT_VIEW);
    stagingPanel.getStagedChangesPanel().setResourcesViewMode(ResourcesViewMode.FLAT_VIEW);
  }

  /**
   * <p><b>Description:</b> Truncate filepaths in Git Staging (list view mode)</p>
   * <p><b>Bug ID:</b> EXM-48510</p>
   *
   * @throws Exception
   */
  @Test
  public void testTruncateFilenames() throws Exception {
    // Create repositories
    String localTestRepository = "target/test-resources/testAmendCommitThatWasPushed_1_local";
    String testFolder = localTestRepository +"/thisIsTheFirstSubFolder/thisIsTheSecondSubFolder/thisIsTheThirdSubFolder";
    createRepository(localTestRepository);

    // Create a new file
    new File(testFolder).mkdirs();
    createNewFile(testFolder, "test.txt", "content");

    JTable filesTable = stagingPanel.getUnstagedChangesPanel().getFilesTable();
    DefaultTableCellRenderer tableCellRenderer = (DefaultTableCellRenderer) filesTable.getCellRenderer(0, 1)
        .getTableCellRendererComponent(filesTable, filesTable.getModel().getValueAt(0, 1), false, false, 0, 1);
    
    // Large width
    SwingUtilities.invokeLater(() -> {
      mainFrame.setSize(new Dimension(555, 400));
      mainFrame.repaint();
    });
    flushAWT();
    sleep(3000);
    assertEquals(
        "thisIsTheFirstSubFolder/thisIsTheSecondSubFolder/thisIsTheThirdSubFolder/test.txt",
        tableCellRenderer.getText());

    // Shrink width
    SwingUtilities.invokeLater(() -> {
      mainFrame.setSize(new Dimension(444, 400));
      mainFrame.repaint();
    });
    flushAWT();
    sleep(3000);
    assertEquals(
        "thisIsTheFirstSubFolder/.../thisIsTheThirdSubFolder/test.txt",
        tableCellRenderer.getText());

    // Shrink even more
    SwingUtilities.invokeLater(() -> {
      mainFrame.setSize(new Dimension(333, 400));
      mainFrame.repaint();
    });
    flushAWT();
    sleep(3000);
    assertEquals(
        "thisIsTheFirstSubFolder/.../test.txt",
        tableCellRenderer.getText());

  }

}
