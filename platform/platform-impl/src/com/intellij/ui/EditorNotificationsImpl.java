// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.progress.util.ReadTask;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.listeners.RefactoringElementAdapter;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.RefactoringElementListenerProvider;
import com.intellij.reference.SoftReference;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * @author peter
 */
public class EditorNotificationsImpl extends EditorNotifications {
  private static final Key<WeakReference<ProgressIndicator>> CURRENT_UPDATES = Key.create("CURRENT_UPDATES");
  private static final ExecutorService ourExecutor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor(
    "EditorNotificationsImpl Pool");
  private final MergingUpdateQueue myUpdateMerger;
  @NotNull private final Project myProject;

  public EditorNotificationsImpl(@NotNull Project project) {
    myUpdateMerger = new MergingUpdateQueue("EditorNotifications update merger", 100, true, null, project);
    myProject = project;
    MessageBusConnection connection = project.getMessageBus().connect(project);
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        updateNotifications(file);
      }
    });
    connection.subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
      @Override
      public void enteredDumbMode() {
        updateAllNotifications();
      }

      @Override
      public void exitDumbMode() {
        updateAllNotifications();
      }
    });
  }

  @Override
  public void updateNotifications(@NotNull final VirtualFile file) {
    UIUtil.invokeLaterIfNeeded(() -> {
      ProgressIndicator indicator = getCurrentProgress(file);
      if (indicator != null) {
        indicator.cancel();
      }
      file.putUserData(CURRENT_UPDATES, null);

      if (myProject.isDisposed() || !file.isValid()) {
        return;
      }

      indicator = new ProgressIndicatorBase();
      final ReadTask task = createTask(indicator, file);
      if (task == null) return;

      file.putUserData(CURRENT_UPDATES, new WeakReference<>(indicator));
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        ReadTask.Continuation continuation = task.performInReadAction(indicator);
        if (continuation != null) {
          continuation.getAction().run();
        }
      }
      else {
        ProgressIndicatorUtils.scheduleWithWriteActionPriority(indicator, ourExecutor, task);
      }
    });
  }

  @Nullable
  private ReadTask createTask(@NotNull final ProgressIndicator indicator, @NotNull final VirtualFile file) {
    List<FileEditor> editors = ContainerUtil.filter(FileEditorManager.getInstance(myProject).getAllEditors(file),
                                                    editor -> !(editor instanceof TextEditor) || AsyncEditorLoader.isEditorLoaded(((TextEditor)editor).getEditor()));
    if (editors.isEmpty()) return null;

    return new ReadTask() {
      private boolean isOutdated() {
        if (myProject.isDisposed() || !file.isValid() || indicator != getCurrentProgress(file)) {
          return true;
        }

        for (FileEditor editor : editors) {
          if (!editor.isValid()) {
            return true;
          }
        }

        return false;
      }

      @Nullable
      @Override
      public Continuation performInReadAction(@NotNull ProgressIndicator indicator) throws ProcessCanceledException {
        if (isOutdated()) return null;

        final List<Provider> providers = DumbService.getInstance(myProject).
          filterByDumbAwareness(EXTENSION_POINT_NAME.getExtensions(myProject));

        final List<Runnable> updates = new SmartList<>();
        for (final FileEditor editor : editors) {
          for (final Provider<?> provider : providers) {
            final JComponent component = provider.createNotificationPanel(file, editor);
            updates.add(() -> updateNotification(editor, provider.getKey(), component));
          }
        }

        return new Continuation(() -> {
          if (!isOutdated()) {
            file.putUserData(CURRENT_UPDATES, null);
            for (Runnable update : updates) {
              update.run();
            }
          }
        }, ModalityState.any());
      }

      @Override
      public void onCanceled(@NotNull ProgressIndicator ignored) {
        if (getCurrentProgress(file) == indicator) {
          updateNotifications(file);
        }
      }
    };
  }

  private static ProgressIndicator getCurrentProgress(VirtualFile file) {
    return SoftReference.dereference(file.getUserData(CURRENT_UPDATES));
  }

  private void updateNotification(@NotNull FileEditor editor, @NotNull Key<? extends JComponent> key, @Nullable JComponent component) {
    JComponent old = editor.getUserData(key);
    if (old != null) {
      FileEditorManager.getInstance(myProject).removeTopComponent(editor, old);
    }
    if (component != null) {
      FileEditorManager.getInstance(myProject).addTopComponent(editor, component);
      @SuppressWarnings("unchecked") Key<JComponent> _key = (Key<JComponent>)key;
      editor.putUserData(_key, component);
    }
    else {
      editor.putUserData(key, null);
    }
  }

  @Override
  public void updateAllNotifications() {
    myUpdateMerger.queue(new Update("update") {
      @Override
      public void run() {
        for (VirtualFile file : FileEditorManager.getInstance(myProject).getOpenFiles()) {
          updateNotifications(file);
        }
      }
    });
  }

  public static class RefactoringListenerProvider implements RefactoringElementListenerProvider {
    @Nullable
    @Override
    public RefactoringElementListener getListener(@NotNull final PsiElement element) {
      if (element instanceof PsiFile) {
        return new RefactoringElementAdapter() {
          @Override
          protected void elementRenamedOrMoved(@NotNull final PsiElement newElement) {
            if (newElement instanceof PsiFile) {
              final VirtualFile vFile = newElement.getContainingFile().getVirtualFile();
              if (vFile != null) {
                EditorNotifications.getInstance(element.getProject()).updateNotifications(vFile);
              }
            }
          }

          @Override
          public void undoElementMovedOrRenamed(@NotNull final PsiElement newElement, @NotNull final String oldQualifiedName) {
            elementRenamedOrMoved(newElement);
          }
        };
      }
      return null;
    }
  }
}
