package com.oxygenxml.git.service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffConfig;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.BranchConfig;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revplot.PlotCommit;
import org.eclipse.jgit.revplot.PlotCommitList;
import org.eclipse.jgit.revplot.PlotWalk;
import org.eclipse.jgit.revwalk.FollowFilter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.RevWalkUtils;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.io.NullOutputStream;

import com.oxygenxml.git.service.entities.FileStatus;
import com.oxygenxml.git.service.entities.FileStatusOverDiffEntry;
import com.oxygenxml.git.service.entities.GitChangeType;
import com.oxygenxml.git.view.history.CommitCharacteristics;
import com.oxygenxml.git.view.history.CommitsAheadAndBehind;
import com.oxygenxml.git.view.history.RenameTracker;
import com.oxygenxml.git.view.history.graph.GraphColorUtil;
import com.oxygenxml.git.view.history.graph.VisualCommitsList;
import com.oxygenxml.git.view.history.graph.VisualCommitsList.VisualLane;

import ro.sync.exml.workspace.api.PluginWorkspaceProvider;

/**
 * Utility methods for working with commits.
 */
public class RevCommitUtil {
  /**
   * Maximum number of diff entries.
   */
  private static final int THREE_DIFF_ENTRIES = 3;
  /**
   * Logger for logging.
   */
  private static final Logger LOGGER = Logger.getLogger(RevCommitUtil.class);
  /**
   * Index of the parent commit which contains untracked changes.
   */
  public static final int PARENT_COMMIT_UNTRACKED = 2;


  /**
   * Utility class. Not indented to be instantiated.
   */
  private RevCommitUtil() {}


  /**
   * Get changed files as compared with the parent version.
   *
   * @param commitID The commit ID.
   *
   * @return A list with changed files. Never <code>null</code>.
   *
   * @throws IOException
   * @throws GitAPIException
   */
  public static List<FileStatus> getChangedFiles(String commitID) throws IOException, GitAPIException {
    List<FileStatus> changedFiles = Collections.emptyList();
    try {
      Repository repository = GitAccess.getInstance().getRepository();
      if (!GitAccess.UNCOMMITED_CHANGES.getCommitId().equals(commitID)) {
        ObjectId head = repository.resolve(commitID);

        try (RevWalk rw = new RevWalk(repository)) {
          RevCommit commit = rw.parseCommit(head);

          if (commit.getParentCount() > 0) {
            RevCommit oldC = rw.parseCommit(commit.getParent(0));
            changedFiles = RevCommitUtil.getChanges(repository, commit, oldC);
            if (commit.getParentCount() > 2) {
              addUntrackedFiles(changedFiles, repository, rw, commit);
            }
          } else {
            changedFiles = RevCommitUtil.getFiles(repository, commit);
          }
        }
      } else {
        changedFiles = GitAccess.getInstance().getUnstagedFiles();
      }
    } catch(MissingObjectException exc) {
    	LOGGER.debug(exc, exc);
    } catch (GitAPIException | RevisionSyntaxException | IOException | NoRepositorySelected e) {
      LOGGER.error(e, e);
    }

    return changedFiles;
  }


  /**
   * Add the untracked files to files list.
   *
   * @param changedFiles    The container to add files.
   * @param repository      The Git repository.
   * @param revWalk         The RevWalk.
   * @param commit          The stash commit.
   *
   * @throws IOException
   */
  private static void addUntrackedFiles(List<FileStatus> changedFiles, Repository repository, RevWalk revWalk, RevCommit commit) throws IOException {
    RevCommit oldC;
    oldC = revWalk.parseCommit(commit.getParent(PARENT_COMMIT_UNTRACKED));
    try (TreeWalk treeWalk = new TreeWalk(repository)) {
      treeWalk.addTree(commit.getTree());
      treeWalk.setRecursive(false);
      treeWalk.setFilter(null);
      treeWalk.reset(oldC.getTree().getId());
      while (treeWalk.next()) {
        if (treeWalk.isSubtree()) {
          treeWalk.enterSubtree();
        } else {
          String path = treeWalk.getPathString();
          changedFiles.add(new FileStatus(GitChangeType.UNTRACKED, path));
        }
      }
    }
  }


  /**
   * Gets the Object ID for a file path at a given revision.
   * 
   * @param repository Git repository.
   * @param commitID Revision commit ID.
   * @param path File path, relative to the working tree directory.
   * 
   * @return The Object identifying the file at the given revision or <code>null</code> if the path is not found in the given commit.
   * 
   * @throws IOException Unable to identify the commit.
   */
  public static ObjectId getObjectID(Repository repository, String commitID, String path) throws IOException {
    ObjectId head = repository.resolve(commitID);

    try (RevWalk rw = new RevWalk(repository)) {
      RevCommit commit = rw.parseCommit(head);

      try (TreeWalk treeWalk = TreeWalk.forPath(repository, path, commit.getTree())) {
        return treeWalk != null ? treeWalk.getObjectId(0) : null;
      }
    }
  }

  /**
   * Gets all the files changed between two revisions.
   * 
   * @param repository Repository.
   * @param newCommit The new commit.
   * @param oldCommit The previous commit.
   * 
   * @return A list with changed files. Never <code>null</code>.
   * @throws IOException
   * @throws GitAPIException
   */
  private static List<FileStatus> getChanges(Repository repository, RevCommit newCommit, RevCommit oldCommit) throws IOException, GitAPIException {
    List<DiffEntry> diffs = diff(repository, newCommit, oldCommit);

    return diffs
        .stream()
        .map(t -> new FileStatusOverDiffEntry(t, newCommit.getId().name(), oldCommit.getId().name()))
        .collect(Collectors.toList());
  }


  /**
   * Gets all the files changed between two revisions.
   * 
   * @param repository Repository.
   * @param newCommit The new commit.
   * @param oldCommit The previous commit.
   * 
   * @return A list with changed files. Never <code>null</code>.
   * @throws IOException
   * @throws GitAPIException
   */
  private static List<DiffEntry> diff(
      Repository repository, 
      RevCommit newCommit, 
      RevCommit oldCommit) throws IOException, GitAPIException {
    List<DiffEntry> collect = Collections.emptyList();
    try (ObjectReader reader = repository.newObjectReader()) {
      CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
      newTreeIter.reset(reader, newCommit.getTree().getId());

      CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
      if (oldCommit != null) {
        oldTreeIter.reset(reader, oldCommit.getTree().getId());
      }


      // finally get the list of changed files
      try (Git git = new Git(repository)) {
        List<DiffEntry> diffs= git.diff()
            .setNewTree(newTreeIter)
            .setOldTree(oldTreeIter)
            .call();

        // Identify potential renames.
        RenameDetector rd = new RenameDetector(git.getRepository());
        rd.addAll(diffs);
        collect = rd.compute();
      }
    }

    return collect;
  }

  /**
   * Iterates over the entire tree of files inside a commit. <b>Note:</b> Not just hte changes, the entire tree.
   * 
   * @param repository Git repository.
   * @param commit Commit object.
   *  
   * @return All the files present in the repository at the time of that commit.
   * 
   * @throws IOException If it fails.
   */
  private static List<FileStatus> getFiles(Repository repository, RevCommit commit) throws IOException {
    List<FileStatus> collect = new LinkedList<>();

    try (DiffFormatter diffFmt = new DiffFormatter(NullOutputStream.INSTANCE)) {
      diffFmt.setRepository(repository);

      for(DiffEntry diff: diffFmt.scan(null, commit.getTree())) {
        collect.add(new FileStatusOverDiffEntry(diff, commit.getId().name(), null));
      }
    }

    return collect;
  }

  /**
   * Get changed files as compared with the parent version.
   * 
   * @param repository The repository.
   * @param commitID The commit ID.
   * 
   * @return A list with changed files. Can be <code>null</code>.
   * 
   * @throws IOException Unable to retrieve commit information.
   */
  public static RevCommit[] getParents(Repository repository, String commitID) throws IOException {
    if (!GitAccess.UNCOMMITED_CHANGES.getCommitId().equals(commitID)) {
      ObjectId head = repository.resolve(commitID);

      try (RevWalk rw = new RevWalk(repository)) {
        RevCommit commit = rw.parseCommit(head);

        return commit.getParents();
      }
    }

    return new RevCommit[0];
  }

  /**
   * Checks if the given path was renamed between the two revisions.
   * 
   * @param repository The current repository.
   * @param parent     Parent commit.
   * @param commit     Current commit.
   * @param path       Resource path.
   * 
   * @return If the resource was renamed between the two revisions, it will return a diff information.
   * 
   * @throws IOException
   * @throws GitAPIException
   */
  public static Optional<DiffEntry> findRename(
      Repository repository,
      RevCommit parent, 
      RevCommit commit,
      String path) throws IOException, GitAPIException {
    DiffEntry toReturn = null;
    List<DiffEntry> diffs = diff(repository, commit, parent);
    for (DiffEntry diffEntry : diffs) {
      if (isRename(diffEntry) && diffEntry.getNewPath().equals(path)) {
        toReturn = diffEntry;
        break;
      }
    }

    return Optional.ofNullable(toReturn);
  }

  /**
   * Checks the change type to see if it represents a rename.
   * 
   * @param ent Diff entry.
   * 
   * @return <code>true</code> if this change represents a rename.
   */
  public static boolean isRename(DiffEntry ent) {
    return ent.getChangeType() == ChangeType.RENAME
        || ent.getChangeType() == ChangeType.COPY;
  }



  /**
   * Collects the revisions from the current branch and the remote branch linked to it.
   * 
   * @param filePath An optional resource path. If not null, only the revisions that changed this resource are collected.
   * @param revisions Revisions are collected in here.
   * @param repository Loaded repository.
   * 
   * @throws IOException 
   * @throws GitAPIException
   */
  public static void collectCurrentBranchRevisions(
      String filePath, 
      List<CommitCharacteristics> revisions, 
      Repository repository,
      RenameTracker renameTracker) throws IOException {

    // a RevWalk allows to walk over commits based on some filtering that is defined
    // EXM-44307 Show current branch commits only.
    String fullBranch = repository.getFullBranch();
    Ref branchHead = repository.exactRef(fullBranch);
    if (branchHead != null) {
      try (PlotWalk plotWalk = new PlotWalk(repository)) {
    	  RevCommit root = plotWalk.parseCommit(branchHead.getObjectId());
		  plotWalk.markStart(root);
		  if(filePath != null && renameTracker != null) {
			  renameTracker.reset(filePath);
			  plotWalk.setRevFilter(renameTracker.getFilter());
		  }
			
        // If we have a remote, put it as well.
        String fullRemoteBranchName = getUpstreamBranchName(repository, repository.getBranch());
        if (fullRemoteBranchName != null) {
          Ref fullRemoteBranchHead = repository.exactRef(fullRemoteBranchName);
          if (fullRemoteBranchHead != null) {
            plotWalk.markStart(plotWalk.parseCommit(fullRemoteBranchHead.getObjectId()));
          }
        }
		
        collectRevisions(filePath, revisions, repository, plotWalk, renameTracker);
      }

    } else {
      // Probably a new repository without any history. 
    }
  }

  /**
   * Gets the full remote-tracking branch name or null is the local branch is not tracking a remote branch.
   * 
   * ex: refs/remotes/origin/dev
   * @param repository 
   * 
   * @param localBranchShortName The short branch name.
   * 
   * @return The full remote-tracking branch name or null is the local branch is not tracking a remote branch.
   */
  public static String getUpstreamBranchName(Repository repository, String localBranchShortName) {
    BranchConfig branchConfig = new BranchConfig(repository.getConfig(), localBranchShortName);
    return branchConfig.getRemoteTrackingBranch();
  }
 
  
  /**
   * Collects all the revisions by waking the revision iterator.
   * 
   * @param filePath An optional resource path. If not null, only the revisions that changed this resource are collected.
   * @param commits Revisions/Commits are collected in here.
   * @param repository Loaded repository.
   * @param plotWalk Revision iterator.
   * 
   * @throws IOException
   * @throws GitAPIException
   */
  private static void collectRevisions(
		  String filePath,
		  List<CommitCharacteristics> commits,
		  Repository repository,
		  PlotWalk plotWalk, RenameTracker renameTracker) throws IOException {

	  if (filePath != null) {
		  FollowFilter filter = FollowFilter.create(filePath, repository.getConfig().get(DiffConfig.KEY));
		  if(renameTracker != null) {
			  filter.setRenameCallback(renameTracker.getCallback());
		  }
		  plotWalk.setTreeFilter(filter);
	  }

	  boolean isDarkTheme = PluginWorkspaceProvider.getPluginWorkspace().getColorTheme().isDarkTheme();
	  PlotCommitList<VisualLane> plotCommitList = new VisualCommitsList(GraphColorUtil.createColorDispatcher(isDarkTheme));
	  plotCommitList.source(plotWalk);
	  plotCommitList.fillTo(Integer.MAX_VALUE);

	  Iterator<PlotCommit<VisualLane>> commitListIterator = plotCommitList.iterator();

	  while (commitListIterator.hasNext()) {
		  PlotCommit<VisualLane> commit = commitListIterator.next();
		  commits.add(new CommitCharacteristics(commit));
	  }

  }
  
  
  /**
   * Collects all renames for the current file path.
   * 
   * @param filePath An optional resource path. 
   * @param repository Loaded repository.
   * 
   * @return <code>null</code> if filePath are null or the following map:
   * <br>
   * The keys are the time of the next commit in which the file whose history 
   *   is shown is involved(or even the commit if this is the last one). 
   * <br>
   * The values ​​have the path of the file that was before it changed.
   * 
   * @throws IOException 
   * @throws GitAPIException
   */
  public static Map<Integer, String> getCurrentFileOldPaths(
      String filePath, 
      Repository repository) throws IOException, GitAPIException {
      
	  if(filePath == null) {
		  return null;
	  }
	  
	  Map<Integer, String> renames = new TreeMap<>();
	  
    // a RevWalk allows to walk over commits based on some filtering that is defined
    // EXM-44307 Show current branch commits only.
    String fullBranch = repository.getFullBranch();
    Ref branchHead = repository.exactRef(fullBranch);
    if (branchHead != null) {
      try (RevWalk revWalk = new RevWalk(repository)) {

        RevCommit revCommit = revWalk.parseCommit(branchHead.getObjectId());
        revWalk.markStart(revCommit);

        // If we have a remote, put it as well.
        String fullRemoteBranchName = getUpstreamBranchName(repository, repository.getBranch());
        if (fullRemoteBranchName != null) {
          Ref fullRemoteBranchHead = repository.exactRef(fullRemoteBranchName);
          if (fullRemoteBranchHead != null) {
            revWalk.markStart(revWalk.parseCommit(fullRemoteBranchHead.getObjectId()));
          }
        }

        collectRenames(filePath, repository, renames, revWalk);
      }

    } else {
      // Probably a new repository without any history. 
    }
    
    return renames;
  }

  
  /**
   * Collects all file path renames by waking the revision iterator.
   * 
   * @param filePath     An optional resource path. If not null, only the revisions that changed this resource are collected.
   * @param renamesFile  Map when collect the renames and time when are made.
   * @param repository   Loaded repository.
   * @param revWalk      Revision iterator.
   * 
   * @throws IOException
   * @throws GitAPIException
   */
  private static void collectRenames(
		  String filePath,
		  Repository repository,
		  Map<Integer, String> renamesFile,
		  RevWalk revWalk) throws IOException, GitAPIException {

	  if (filePath != null) {
		  revWalk.setTreeFilter(
		          AndTreeFilter.create(
		              PathFilterGroup.createFromStrings(filePath),
		              TreeFilter.ANY_DIFF)
		          );
	  }

	  RevCommit lastProcessedRevision = null;
	    for (RevCommit commit : revWalk) {
	      if(lastProcessedRevision == null) {
	    	  renamesFile.put(commit.getCommitTime(), filePath);
	      }
	      lastProcessedRevision = commit;
	    }

	    // If we are following a resource, check for rename events.
	    if (filePath != null && lastProcessedRevision != null) {
	      handleRename(filePath, repository, renamesFile, lastProcessedRevision);
	    }
	  
  }
  
  
  /**
   * Checks for a rename operation. If the resource was renamed between the current revision and the previous one,
   * it will continue collecting revisions based on the previous resource name/path.  
   * 
   * @param filePath An optional resource path. If not null, only the revisions that changed this resource are collected.
   * @param commits Revisions are collected in here.
   * @param repository Loaded repository.
   * @param currentCommit The last revision encountered for the file path.
   * 
   * @throws IOException
   * @throws GitAPIException
   */
  private static void handleRename(
      String filePath,  
      Repository repository,
      Map<Integer, String> renamesFile,
      RevCommit currentCommit)
          throws IOException, GitAPIException {
    try (RevWalk plotWalk = new RevWalk(repository)) {
      RevCommit commit = plotWalk.parseCommit(currentCommit.getId());

      if (commit.getParentCount() > 0) {
        RevCommit parent = commit.getParent(0);
        plotWalk.parseHeaders(parent);

        Optional<DiffEntry> renameRev = RevCommitUtil.findRename(repository, parent, commit, filePath);
        if (renameRev.isPresent()) {
          String oldPath = renameRev.get().getOldPath();
          
          plotWalk.markStart(commit);

          collectRenames(oldPath, repository, renamesFile, plotWalk);
        } 
      }
    }
  }
  
  
  /**
   * Get a list with all the parent IDs of the current commit.
   * 
   * @param commit The current commit.
   * @return The list with parents commit IDs.
   */
  public static List<String> getParentsId(RevCommit commit) {
    List<String> parentsIds = null;

    // add list of parent commits.
    if (commit.getParentCount() > 0) {
      parentsIds = new ArrayList<>();
      for (RevCommit parentCommit : commit.getParents()) {
        parentsIds.add(parentCommit.getId().abbreviate(RevCommitUtilBase.ABBREVIATED_COMMIT_LENGTH).name());
      }
    }
    return parentsIds;
  }


  /**
   * Checks if a resource was moved or renamed between two revisions. We know the path in the old revision and 
   * we want to find out the path in the new revision.
   * 
   * @param git Git access.
   * @param since Start of the interval.
   * @param until End of the interval.
   * @param filePath The new path of a resource.
   * 
   * @return The new path.
   * 
   * @throws GitAPIException
   * @throws IOException
   */
  public static String getNewPath(
      Git git, 
      RevCommit since, 
      RevCommit until,
      String filePath) throws GitAPIException, IOException {
    Iterable<RevCommit> revs = git.log().addRange(since, until).call();

    List<RevCommit> revisions = sort(revs, since, true);

    return findPath(git, filePath, revisions);
  }

  /**
   * Checks if a resource was moved or renamed between two revisions. We know the path in the NEW revision and 
   * we want to find out the path in the OLD revision.
   * 
   * @param git Git interaction.
   * @param since The old revision.
   * @param until The new revision.
   * @param newFilePath The original file path.
   *  
   * @return The new path of the resource.
   * 
   * @throws GitAPIException
   * @throws IOException
   */
  public static String getOldPath(
      Git git, 
      RevCommit since, 
      RevCommit until,
      String newFilePath) throws GitAPIException, IOException {
    Iterable<RevCommit> revs = git.log().addRange(since, until).call();

    List<RevCommit> sorted = sort(revs, since, false);

    return findPath(git, newFilePath, sorted);
  }


  /**
   * Checks if a resource was moved or renamed between HEAD and an older revision. We know the path in the HEAD revision and 
   * we want to find out the path in the OLD revision.
   * 
   * @param git Git interaction.
   * @param oldRevisionId The ID of the old revision in which the file might have had another
   * @param originalFilePath The original file path.
   *  
   * @return The new path of the resource.
   * 
   * @throws GitAPIException
   * @throws IOException
   */
  public static String getOldPathStartingFromHead(
      Git git, 
      String oldRevisionId, 
      String originalFilePath) throws GitAPIException, IOException {

    Repository repository = git.getRepository();
    RevCommit olderRevCommit = repository.parseCommit(repository.resolve(oldRevisionId));
    RevCommit headRevCommit = repository.parseCommit(repository.resolve("HEAD"));

    return getOldPath(git, olderRevCommit, headRevCommit, originalFilePath);
  }

  /**
   * Finds the new location of a resource that might have been moved / renamed across revisions.
   * 
   * @param git Git interaction.
   * @param filePath The known path .
   * @param revisions The list of revisions across which to follow the resource renames.
   *  
   * @return The path of the resource as present in the last revision from the list.
   * 
   * @throws IOException
   * @throws GitAPIException
   */
  private static String findPath(Git git, String filePath, List<RevCommit> revisions)
      throws IOException, GitAPIException {
    String path = filePath;
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("====SORTED===");
      revisions.forEach(r -> LOGGER.debug(r.getFullMessage()));
    }

    if (!revisions.isEmpty()) {
      RevCommit lastRev = revisions.get(revisions.size() - 1);
      List<FileStatus> targetRevFiles = getFiles(git.getRepository(), lastRev);
      Set<String> lastRevFiles = targetRevFiles.stream().map(FileStatus::getFileLocation).collect(Collectors.toSet());

      RevCommit previous = null;
      for (RevCommit revCommit : revisions) {
        if (previous != null) {

          // Fast stop.
          if (lastRevFiles.contains(path)) {
            // The current discovered path is the same as in the target revision.
            if (LOGGER.isDebugEnabled()) {
              LOGGER.info("Same path as in target. Stop. " + revCommit.getFullMessage());
            }
            break;
          }

          // Check if the current discovered path is also present in the new revision to consume.
          // TThis way we will avoid a time consuming diff with rename detection.
          List<FileStatus> currentRevisionFiles = getFiles(git.getRepository(), revCommit);
          final String fpath = path;
          boolean same = currentRevisionFiles.stream().anyMatch(t -> fpath.equals(t.getFileLocation()));

          if (!same) {
            // Do a diff with rename detection.
            if (LOGGER.isDebugEnabled()) {
              LOGGER.info("Search for a rename at revision " + revCommit.getFullMessage());
            }

            List<DiffEntry> diff = diff(git.getRepository(), revCommit, previous);
            for (DiffEntry diffEntry : diff) {
              if (isRename(diffEntry) 
                  && path.equals(diffEntry.getOldPath())) {
                // Match.
                path = diffEntry.getNewPath();
                break;
              }
            }
          }
        }

        previous = revCommit;
      }
    }

    return path;
  }

  /**
   * Utility method  to put the revisions in a proper order.
   * 
   * @param <E> The type of the list elements.
   * @param new2old A list of already sorted revisions, from new to old.
   * @param oldestRev A revision older than all the others. It will be added in the resulting list.
   * @param ascending <code>true</code> to sort from older to newest.
   * 
   * @return The list of revisions sorted as requested.
   */
  static <E> List<E> sort(Iterable<E> new2old, E oldestRev, boolean ascending) {
    LinkedList<E> sorted = new LinkedList<>();
    if (!ascending) {
      for (E revCommit : new2old) {
        sorted.add(revCommit);
      }

      sorted.add(oldestRev);
    } else {
      for (E revCommit : new2old) {
        sorted.addFirst(revCommit);
      }

      sorted.addFirst(oldestRev);
    }

    return sorted;
  }

  /**
   * Checks if a resource was moved or renamed between the HEAD revision and an older revision. We know the path in 
   * the old revision and we want to find out the path in the HEAD revision.
   * 
   * @param git Git interaction.
   * @param filePath The known file path.
   * @param commitId The revision ID of the file path.
   *  
   * @return The new path of the resource.
   * 
   * @throws GitAPIException The resource was probably moved / renamed but its localization failed.
   * @throws IOException The local copy version was not found.
   */
  public static String getNewPathInWorkingCopy(
      Git git, 
      String filePath, 
      String commitId) throws IOException, GitAPIException {

    String originalPath = filePath;

    Repository repository = git.getRepository();

    File targetFile = new File(repository.getWorkTree(), originalPath);
    if (!targetFile.exists()) {
      // The file is not present in the working copy. Perhaps it was renamed.
      RevCommit older = repository.parseCommit(repository.resolve(commitId));
      RevCommit newer = repository.parseCommit(repository.resolve("HEAD"));

      originalPath = RevCommitUtil.getNewPath(
          git, 
          older, 
          newer,
          filePath);


      targetFile = new File(repository.getWorkTree(), originalPath);
      if (!targetFile.exists()) {
        // Still not present inside the WC. Probably a rename that wasn't committed yet.
        // One more try.
        originalPath = findWCRename(git, newer, originalPath);

        // One last try to see if we identified the new location.
        targetFile = new File(repository.getWorkTree(), originalPath);
        if (!targetFile.exists()) {
          throw new FileNotFoundException("File \"" + originalPath + "\" was probably removed from the working copy.");
        }
      }
    }

    return originalPath;
  }

  /**
   * Detects a rename between the head revision and the working copy.
   * 
   * @param git Git access.
   * @param head Head revision.
   * @param path The path from the HEAD revision that might have been renamed.
   * 
   * @return The new path if a rename was detected or the old path.
   * 
   * @throws IOException
   * @throws GitAPIException
   */
  private static String findWCRename(Git git, RevCommit head, String path)
      throws IOException, GitAPIException {
    String toReturn = path;
    Repository repository = git.getRepository();
    try (ObjectReader reader = repository.newObjectReader()) {
      // Head files.
      CanonicalTreeParser headTreeIter = new CanonicalTreeParser();
      headTreeIter.reset(reader, head.getTree().getId());

      // Working copy files.
      FileTreeIterator it = new FileTreeIterator(repository);

      // Compute diff.
      List<DiffEntry> diffs= git.diff()
          .setNewTree(it)
          .setOldTree(headTreeIter)
          .call();

      // Search for renames.
      RenameDetector rd = new RenameDetector(repository);
      rd.addAll(diffs);
      List<DiffEntry> collect = rd.compute();

      for (DiffEntry diffEntry : collect) {
        if (isRename(diffEntry) && diffEntry.getOldPath().equals(path)) {
          toReturn = diffEntry.getNewPath();
          break;
        }
      }

      return toReturn;
    }
  }

  /**
   * Get commits ahead and behind.
   * 
   * @param repository Current repo.
   * @param branchName Current branch.
   * 
   * @return a structure that contains the lists of commits ahead and behind or <code>null</code>.
   * 
   * @throws IOException
   */
  public static CommitsAheadAndBehind getCommitsAheadAndBehind(Repository repository, String branchName)
      throws IOException {

    String shortBranchName = Repository.shortenRefName(branchName);
    String fullBranchName = Constants.R_HEADS + shortBranchName;
    BranchConfig branchConfig = new BranchConfig(repository.getConfig(), shortBranchName);

    String trackingBranch = branchConfig.getTrackingBranch();
    if (trackingBranch == null) {
      return null;
    }

    Ref tracking = repository.exactRef(trackingBranch);
    if (tracking == null) {
      return null;
    }

    Ref local = repository.exactRef(fullBranchName);
    if (local == null) {
      return null;
    }

    try (RevWalk walk = new RevWalk(repository)) {
      RevCommit localCommit = walk.parseCommit(local.getObjectId());
      RevCommit trackingCommit = walk.parseCommit(tracking.getObjectId());

      walk.setRevFilter(RevFilter.MERGE_BASE);
      walk.markStart(localCommit);
      walk.markStart(trackingCommit);
      RevCommit mergeBase = walk.next();

      walk.reset();
      walk.setRevFilter(RevFilter.ALL);
      List<RevCommit> commitsAhead = RevWalkUtils.find(walk, localCommit, mergeBase);
      List<RevCommit> commitsBehind = RevWalkUtils.find(walk, trackingCommit, mergeBase);

      return new CommitsAheadAndBehind(commitsAhead, commitsBehind);
    }
  }


  /**
   * Returns the SHA-1 id for the BASE commit of a file. The BASE commit
   * only exits if there is a conflict on the current file.
   * 
   * @param git Git access.
   * @param filePath File path.
   * 
   * @return The SHA-1 commit ID or <code>null</code>.
   * 
   * @throws IOException
   * @throws GitAPIException 
   */
  public static ObjectId getBaseCommit(Git git, String filePath) throws IOException, GitAPIException {
    ObjectId toReturn;
    List<DiffEntry> entries = git.diff().setPathFilter(PathFilter.create(filePath)).call();
    if (!entries.isEmpty()) {
      toReturn = entries.get(0).getOldId().toObjectId();
    } else { 
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("No BASE commit for: '" + filePath + "'");
      }
      toReturn = getLastLocalCommitForPath(git, filePath);
    }
    return toReturn;
  }

  /**
   * Returns the SHA-1 id for their commit of a file. Their commit (THEIRS)
   * only exits if there is a conflict on the current file.
   * 
   * @param git Git access.
   * @param filePath File path.
   * 
   * @return The SHA-1 commit ID or <code>null</code>.
   * 
   * @throws IOException
   * @throws GitAPIException 
   */
  public static ObjectId getTheirCommit(Git git, String filePath) throws IOException, GitAPIException {
    ObjectId toReturn;
    List<DiffEntry> entries = git.diff().setPathFilter(PathFilter.create(filePath)).call();
    int noOfDiffEntries = entries.size();
    boolean isTwoWayDiff = noOfDiffEntries < THREE_DIFF_ENTRIES;
    int indexOfTheirs = isTwoWayDiff ? 1 : 2;
    if (indexOfTheirs < noOfDiffEntries) {
      toReturn =  entries.get(indexOfTheirs).getOldId().toObjectId();
    } else {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("No THEIRS commit available for: '" + filePath + "'. "
            + "Falling back to the last commit for this path.");
      }
      toReturn = getLastLocalCommitForPath(git, filePath);
    }
    return toReturn;
  }

  /**
   * Returns the SHA-1 id for my commit of a file. My commit (MINE)
   * only exits if there is a conflict on the current file.
   * 
   * @param git Git access.
   * @param path File path.
   * 
   * @return The SHA-1 commit ID or <code>null</code>.
   * 
   * @throws IOException
   * @throws GitAPIException 
   */
  public static ObjectId getMyCommit(Git git, String path) throws IOException, GitAPIException {
    ObjectId toReturn;
    List<DiffEntry> entries = git.diff().setPathFilter(PathFilter.create(path)).call();
    int noOfDiffEntries = entries.size();
    boolean isTwoWayDiff = noOfDiffEntries < THREE_DIFF_ENTRIES;
    int indexOfMine = isTwoWayDiff ? 0 : 1;
    if (indexOfMine < noOfDiffEntries) {
      toReturn =  entries.get(indexOfMine).getOldId().toObjectId();
    } else {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("No MINE commit available for: '" + path + "'."
            + " Falling back to the last commit for this path.");
      }
      toReturn = getLastLocalCommitForPath(git, path);
    }
    return toReturn;
  }

  /**
   * Get last local commit for resource path.
   * 
   * @param git Git access.
   * @param path The path.
   * 
   * @return the last local commit. Can be <code>null</code>.
   * 
   * @throws IOException
   */
  public static ObjectId getLastLocalCommitForPath(Git git, String path) throws IOException {
    ObjectId toReturn = null;

    ObjectId lastLocalCommit = getLastLocalCommitInRepo(git);
    RevWalk revWalk = new RevWalk(git.getRepository());
    RevCommit revCommit = revWalk.parseCommit(lastLocalCommit);
    RevTree tree = revCommit.getTree();
    TreeWalk treeWalk = new TreeWalk(git.getRepository());
    treeWalk.addTree(tree);
    treeWalk.setRecursive(true);
    treeWalk.setFilter(PathFilter.create(path));
    if (treeWalk.next()) {
      toReturn = treeWalk.getObjectId(0);
    }
    treeWalk.close();
    revWalk.close();

    return toReturn;
  }

  /**
   * Finds the last local commit in the repository
   * 
   * @param git The Git-API object.
   * 
   * @return the last local commit
   */
  public static ObjectId getLastLocalCommitInRepo(Git git) {
    Repository repo = git.getRepository();
    try {
      return repo.resolve("HEAD^{commit}");
    } catch (IOException e) {
      LOGGER.error(e, e);
    }
    return null;
  }
  /**
   * Finds the commit from the commitID 
   * 
   * @param commitID the commit id, string form of the SHA-1
   * 
   * @return a RevCommit
   * 
   * @throws NoRepositorySelected
   * @throws IOException
   */
  public static RevCommit getCommit(String commitID) throws NoRepositorySelected, IOException {
    ObjectId commitId = ObjectId.fromString(commitID);
    try (RevWalk revWalk = new RevWalk(GitAccess.getInstance().getRepository())) {
      return revWalk.parseCommit(commitId);
    }
  }


}