package com.oxygenxml.git.view.staging;

import com.oxygenxml.git.view.event.GitController;

import junit.framework.TestCase;

public class ToolbarPanelTest extends TestCase {

  /**
   * <p><b>Description:</b> get the number of skipped commits in push/pull tooltips.</p>
   * <p><b>Bug ID:</b> EXM-44564</p>
   *
   * @author sorin_carbunaru
   *
   * @throws Exception
   */
  public void testNoOfSkippedCommitsInPushPullToolbars() throws Exception {
    ToolbarPanel toolbarPanel = new ToolbarPanel(new GitController(null), null, null, null);
    assertEquals(0, toolbarPanel.getNoOfSkippedCommits(5));
    assertEquals(1, toolbarPanel.getNoOfSkippedCommits(6));
    assertEquals(2, toolbarPanel.getNoOfSkippedCommits(7));
  }
  
}
