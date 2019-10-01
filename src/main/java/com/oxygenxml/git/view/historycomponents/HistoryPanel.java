package com.oxygenxml.git.view.historycomponents;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.lang.reflect.Constructor;
import java.util.Date;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JToolBar;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

import org.apache.log4j.Logger;
import org.eclipse.jgit.revwalk.RevCommit;

import com.jidesoft.swing.JideSplitPane;
import com.oxygenxml.git.constants.ImageConstants;
import com.oxygenxml.git.service.GitAccess;
import com.oxygenxml.git.service.NoRepositorySelected;
import com.oxygenxml.git.translator.Tags;
import com.oxygenxml.git.translator.Translator;

import ro.sync.exml.workspace.api.standalone.ui.ToolbarButton;

/**
 * Presents the commits for a given resource. 
 */
public class HistoryPanel extends JPanel {
  /**
   * Logger for logging.
   */
  private static final Logger LOGGER = Logger.getLogger(HistoryPanel.class);
  /**
   * Table view that presents the commits.
   */
  private JTable historyTable;
  private JEditorPane commitDescriptionPane;
  /**
   * Toolbar in which the button will be placed
   */
  private JToolBar toolbar;
  /**
   * The label that shows the resource for which we present the history.
   */
  private JLabel showCurrentRepoLabel;
  private HistoryHyperlinkListener hyperlinkListener;
  private RowHistoryTableSelectionListener selectionListener;
  
  public HistoryPanel() {
    setLayout(new BorderLayout());

    historyTable = createTable();

    JScrollPane tableScrollPane = new JScrollPane(historyTable);
    historyTable.setFillsViewportHeight(true);

    // set Commit Description Pane with HTML content and hyperlink.
    commitDescriptionPane = new JEditorPane();
    init(commitDescriptionPane);

    historyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    
    JScrollPane commitDescriptionScrollPane = new JScrollPane(commitDescriptionPane);
    JScrollPane fileHierarchyScrollPane = new JScrollPane();

    Dimension minimumSize = new Dimension(500, 150);
    commitDescriptionScrollPane.setPreferredSize(minimumSize);
    fileHierarchyScrollPane.setPreferredSize(minimumSize);


    //----------
    // Top panel
    //----------
    
    showCurrentRepoLabel = new JLabel();
    JPanel topPanel = new JPanel(new BorderLayout());
    createToolbar(topPanel);

    JPanel infoBoxesSplitPane = createSplitPane(JideSplitPane.HORIZONTAL_SPLIT, commitDescriptionScrollPane,
        fileHierarchyScrollPane);
    JideSplitPane centerSplitPane = createSplitPane(JideSplitPane.VERTICAL_SPLIT, tableScrollPane, infoBoxesSplitPane);
    centerSplitPane.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
    
    //Customize the split pane.
    this.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentShown(ComponentEvent e) {
        
        int h = centerSplitPane.getHeight();
        centerSplitPane.setDividerLocation(0, (int)(h * 0.6));
        
        removeComponentListener(this);
      }
    });

    add(centerSplitPane, BorderLayout.CENTER);
  }
  
  /**
   * Creates a split pane and puts the two components in it.
   * 
   * @param splitType {@link JideSplitPane#HORIZONTAL_SPLIT} or {@link JideSplitPane#VERTICAL_SPLIT} 
   * @param firstComponent Fist component to add.
   * @param secondComponent Second component to add.
   * 
   * @return The split pane.
   */
  private JideSplitPane createSplitPane(int splitType, JComponent firstComponent, JComponent secondComponent) {
    JideSplitPane splitPane = new JideSplitPane(splitType);
    
    splitPane.add(firstComponent);
    splitPane.add(secondComponent);
    
    splitPane.setDividerSize(5);
    splitPane.setContinuousLayout(true);
    splitPane.setOneTouchExpandable(false);
    splitPane.setBorder(null);
    
    return splitPane;
  }
  
  /**
   * Initializes the split with the proper font and other properties.
   * 
   * @param editorPane Editor pane to initialize.
   */
  private static void init(JEditorPane editorPane) {
    // Forces the JEditorPane to take the font from the UI, rather than the HTML document.
    editorPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
    Font font = UIManager.getDefaults().getFont("TextArea.font");
    if(font != null){
      editorPane.setFont(font);
    }
    editorPane.setBorder(null);
    
    editorPane.setContentType("text/html");
    editorPane.setEditable(false);

  }

  /**
   * Creates the toolbar. 
   * 
   * @param topPanel Parent for the toolbar.
   */
  private void createToolbar(JPanel topPanel) {
    toolbar = new JToolBar();
    toolbar.setOpaque(false);
    toolbar.setFloatable(false);
    topPanel.add(showCurrentRepoLabel, BorderLayout.WEST);
    topPanel.add(toolbar, BorderLayout.EAST);
    
    Action refreshAction = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        showRepositoryHistory();
      }
    };
    refreshAction.putValue(Action.SMALL_ICON, ImageConstants.getIcon(ImageConstants.REFRESH_ICON));
    refreshAction.putValue(Action.SHORT_DESCRIPTION, "refresh");
    ToolbarButton refreshButton = new ToolbarButton(refreshAction, false);
    toolbar.add(refreshButton);
    
    add(topPanel, BorderLayout.NORTH);
  }

  /**
   * Tries to use Oxygen's API to create a table.
   * 
   * @return An Oxygen's API table or a generic one if we run into an old Oxygen.
   */
  private JTable createTable() {
    JTable table = null;
    try {
      Class tableClass = Class.forName("ro.sync.exml.workspace.api.standalone.ui.Table");
      Constructor tableConstructor = tableClass.getConstructor();
      table = (JTable) tableConstructor.newInstance();
    } catch (Exception e) {
      // Running in an Oxygen version that lacks this API.
      table = new JTable();
    }
    
    return table;
  }

  /**
   * Shows the commit history for the entire repository.
   */
  public void showRepositoryHistory() {
    GitAccess gitAccess = GitAccess.getInstance();
    try {
      File directory = gitAccess.getWorkingCopy();
      showCurrentRepoLabel.setText(
          Translator.getInstance().getTranslation(Tags.SHOWING_HISTORY_FOR) + " " + directory.getName());
      showCurrentRepoLabel.setToolTipText(directory.getAbsolutePath());
      showCurrentRepoLabel.setBorder(BorderFactory.createEmptyBorder(0,2,5,0));
      
      historyTable.setDefaultRenderer(CommitCharacteristics.class, new HistoryTableRenderer(gitAccess, gitAccess.getRepository()));
      historyTable.setDefaultRenderer(Date.class, new DateTableCellRenderer("d MMM yyyy HH:mm"));

      List<CommitCharacteristics> commitCharacteristicsVector = gitAccess.getCommitsCharacteristics();

      historyTable.setModel(new HistoryCommitTableModel(commitCharacteristicsVector));
      
      updateTableWidths();
      
      // Install selection listener.
      if (selectionListener != null) {
        historyTable.getSelectionModel().removeListSelectionListener(selectionListener);
      }
      selectionListener = new RowHistoryTableSelectionListener(historyTable, commitDescriptionPane, commitCharacteristicsVector);
      historyTable.getSelectionModel().addListSelectionListener(selectionListener);

      // Install hyperlink listener.
      if (hyperlinkListener != null) {
        commitDescriptionPane.removeHyperlinkListener(hyperlinkListener);  
      }
      
      hyperlinkListener = new HistoryHyperlinkListener(historyTable, commitCharacteristicsVector);
      commitDescriptionPane.addHyperlinkListener(hyperlinkListener);

      // preliminary select the first row in the historyTable
      historyTable.setRowSelectionInterval(0, 0);
    } catch (NoRepositorySelected e) {
      LOGGER.debug(e, e);
    }
  }

  /**
   * Distribute widths to the columns according to their content.
   */
  private void updateTableWidths() {
    TableColumnModel tcm = historyTable.getColumnModel();
    int available = historyTable.getWidth();
    TableColumn column = tcm.getColumn(0);
    column.setPreferredWidth(available - 3 * 100);
    
    column = tcm.getColumn(1);
    column.setPreferredWidth(100);

    column = tcm.getColumn(2);
    column.setPreferredWidth(120);

    column = tcm.getColumn(3);
    column.setPreferredWidth(80);
  }

  /**
   *  Shows the commit history for the given file.
   *  
   * @param filePath Path of the file, relative to the working copy.
   * @param activeRevCommit The commit to select in the view.
   */
  public void showCommit(String filePath, RevCommit activeRevCommit) {
    // TODO Present revisions just for the given resource.
    if (activeRevCommit != null) {
      HistoryCommitTableModel model =  (HistoryCommitTableModel) historyTable.getModel();
      List<CommitCharacteristics> commitVector = model.getCommitVector();
      for (int i = 0; i < commitVector.size(); i++) {
        CommitCharacteristics commitCharacteristics = commitVector.get(i);

        if (activeRevCommit.getId().getName().equals(commitCharacteristics.getCommitId())) {
          final int sel = i;
          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              historyTable.scrollRectToVisible(historyTable.getCellRect(sel, 0, true));
              historyTable.getSelectionModel().setSelectionInterval(sel, sel);
            }
          });
          break;
        }
      }
    }
  }
}
