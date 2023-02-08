package org.intellij.plugins.log;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

/**
 * @Description:
 * @Author: zhaijizhongActionManagerImpl
 * @Date: 2022/2/14 14:55
 */
public class GenerateLoggerAction extends AnAction {

    private static final Logger LOG = Logger.getInstance(GenerateLoggerAction.class);

    private EditorActionHandler handler;

    public GenerateLoggerAction() {
        handler = GenerateLoggerHandler.INSTANCE;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        DataContext dataContext = e.getDataContext();
        Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
        if (editor != null) {
            if (editor.isDisposed()) {
                VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
                LOG.error("Action " + this + " invoked on a disposed editor" + (file == null ? "" : " for file " + file));
            } else {
                Runnable command = () -> handler.execute(editor, (Caret) null, dataContext);
                if (!handler.executeInCommand(editor, dataContext)) {
                    command.run();
                } else {
                    String commandName = this.getTemplatePresentation().getText();
                    if (commandName == null) {
                        commandName = "";
                    }

                    CommandProcessor.getInstance().executeCommand(editor.getProject(), command, commandName, handler.getCommandGroupId(editor), UndoConfirmationPolicy.DEFAULT, editor.getDocument());
                }
            }
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Presentation presentation = e.getPresentation();
        DataContext dataContext = e.getDataContext();
        Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
        this.update(editor, presentation, dataContext);

    }


    public void update(Editor editor, Presentation presentation, DataContext dataContext) {
        final Project project = LangDataKeys.PROJECT.getData(dataContext);
        boolean visible = false;
        boolean enabled = false;

        if (project != null) {
            final VirtualFile virtualFile = LangDataKeys.VIRTUAL_FILE.getData(dataContext);
            final PsiManager psiManager = PsiManager.getInstance(project);
            final PsiClass psiClass = GenerateLoggerHandler.getPsiClass(virtualFile, psiManager, editor);

            visible = GenerateLoggerHandler.needsLoggerField(psiClass);
            enabled = (visible && !GenerateLoggerHandler.hasLoggerField(psiClass));
        }

        presentation.setVisible(visible);
        presentation.setEnabled(enabled);
    }

}
