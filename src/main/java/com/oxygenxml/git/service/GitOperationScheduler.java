package com.oxygenxml.git.service;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.log4j.Logger;

/**
 * Schedules git operations on a thread. The same thread is being used. 
 */
public class GitOperationScheduler {
  
  /**
   * Logger.
   */
  private static final Logger logger = Logger.getLogger(GitOperationScheduler.class);
  
  /**
   * Operation shutdown timeout in milliseconds.
   */
  private static final int OPERATION_SHUTDOWN_TIMEOUT_MS = 2000;
  
  /**
   * Refresh executor.
   */
  private ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1) {
    @Override
    protected void afterExecute(Runnable r, Throwable t) {
      if (t != null) {
        logger.error(t, t);
      }

      if (r instanceof Future) {
        try {
          ((Future<?>) r).get();
        } catch (CancellationException e) {
          logger.debug(e, e);
        } catch (InterruptedException e) { 
          logger.error(e, e);
          Thread.currentThread().interrupt();
        } catch (Exception e) {
          logger.error(e, e);
        }
      }
    }
  };
  
  /**
   * Singleton instance.
   */
  private static GitOperationScheduler instance;
  
  /**
   * Singleton private constructor.
   */
  private GitOperationScheduler() {}
  
  /**
   * @return The singleton instance.
   */
  public static GitOperationScheduler getInstance() {
    if (instance == null) {
      instance = new GitOperationScheduler();
    }
    
    return instance;
  }
  
  /**
   * Schedules a runnable for immediate execution.
   * 
   * @param r Code to be executed on thread.
   * 
   * @return a ScheduledFuture representing pending completion of the task 
   * and whose get() method will return null upon completion.
   */
  @SuppressWarnings("java:S1452")
  public ScheduledFuture<?> schedule(Runnable r) {
    if (executor.isShutdown()) {
      // A shutdown operation was canceled.
      executor = new ScheduledThreadPoolExecutor(1);
    }
    
    return executor.schedule(r, 0, TimeUnit.MILLISECONDS);
  }
  
  /**
   * Schedules a task.
   * 
   * @param <V> the type of the result returned by the future task.
   * @param task A task to run on the dedicated Git actions thread.
   * @param errorHandler Receives notifications when the task fails with an exception.
   * 
   * @return A future that monitors the task.
   */
  @SuppressWarnings("java:S1452")
  public <V> ScheduledFuture<?> schedule(Runnable task, Consumer<Throwable> errorHandler) {
    return schedule(new java.util.concurrent.FutureTask<V> (task, null) {
      @Override
      protected void done() {
        try {
          get();
        } catch (ExecutionException e) {
          errorHandler.accept(e.getCause());      
        } catch (InterruptedException e) {
          errorHandler.accept(e);      
          // Restore interrupted state...
          Thread.currentThread().interrupt();      
        }
      }
    });
  }
  
  /**
   * Schedules a task.
   * 
   * @param <V> the type of the result returned by the future task.
   * @param task A task to run on the dedicated Git actions thread.
   * @param resultHandler Result handler.
   * @param errorHandler Receives notifications when the task fails with an exception.
   * 
   * @return A future that monitors the task.
   */
  @SuppressWarnings("java:S1452")
  public <V> ScheduledFuture<?> schedule(Callable<V> task, Consumer<V> resultHandler,  Consumer<Throwable> errorHandler) {
    return schedule(new java.util.concurrent.FutureTask<V> (task) {
      @Override
      protected void done() {
        try {
          resultHandler.accept(get());
        } catch (ExecutionException e) {
          errorHandler.accept(e.getCause());      
        } catch (InterruptedException e) {
          errorHandler.accept(e);      
          // Restore interrupted state...
          Thread.currentThread().interrupt();      
        }
      }
    });
  }

  /**
   * Schedules a runnable for execution.
   * 
   * @param r Code to be executed on thread.
   * @param delayMillis Milliseconds after which to execute the runnable.
   * 
   * @return a ScheduledFuture representing pending completion of the task 
   * and whose get() method will return null upon completion.
   */
  @SuppressWarnings("java:S1452")
  public ScheduledFuture<?> schedule(Runnable r, int delayMillis) {
    if (executor.isShutdown()) {
      // A shutdown operation was canceled.
      executor = new ScheduledThreadPoolExecutor(1);
    }
    
    return executor.schedule(r, delayMillis, TimeUnit.MILLISECONDS);
  }
  
  /**
   * Attempts to shutdown any running tasks.
   * 
   * @return <code>true</code> if all tasks have been executed. <code>false</code>
   * if there are still tasks running.
   */
  public boolean shutdown() {
    executor.shutdown();
    try {
      return executor.awaitTermination(OPERATION_SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      logger.warn("Unable to stop task thread: " + e.getMessage(), e);
      // Restore interrupted state...
      Thread.currentThread().interrupt();

    }   
    return false;
  }
  
  /**
   * Returns the approximate number of threads that are actively
   * executing tasks.
   *
   * @return the number of threads
   */
  public int getActiveCount() {
    return executor.getActiveCount();
  }
}
