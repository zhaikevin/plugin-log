package org.intellij.plugins.log;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @Description:
 * @Author: zhaijizhong
 * @Date: 2022/2/14 14:58
 */
public class GenerateLoggerHandler extends EditorWriteActionHandler {

    private static final String LOGGER_FIELD_FORMAT = "private static final Logger LOGGER = LoggerFactory.getLogger(%s.class);";

    public static final EditorWriteActionHandler INSTANCE = new GenerateLoggerHandler();

    private static boolean _showing = false;

    private static final com.intellij.openapi.diagnostic.Logger LOGGER = com.intellij.openapi.diagnostic.Logger.getInstance(GenerateLoggerHandler.class.getName());

    private GenerateLoggerHandler() {

    }

    @Override
    public final void executeWriteAction(@Nullable Editor editor, @Nullable Caret caret, @Nullable DataContext dataContext) {
        if (editor == null) {
            displayMessage("No editor found.");
            return;
        }
        if (dataContext == null) {
            displayMessage("No data context.");
            return;
        }

        final Project project = LangDataKeys.PROJECT.getData(dataContext);
        final VirtualFile virtualFile = LangDataKeys.VIRTUAL_FILE.getData(dataContext);

        if (project == null) {
            displayMessage("No project found.");
            return;
        }
        if (virtualFile == null) {
            displayMessage("No file found.");
            return;
        }

        final PsiManager manager = PsiManager.getInstance(project);
        final PsiClass psiClass = getPsiClass(virtualFile, manager, editor);

        if (psiClass == null) {
            displayMessage("Not a Java class file.");
            return;
        }

        if (needsLoggerField(psiClass) && !hasLoggerField(psiClass)) {
            LOGGER.info("start insert logger");
            insertLogger(project, psiClass, manager);
        }
    }


    @Nullable
    public static PsiClass getPsiClass(@Nullable VirtualFile virtualFile,
                                       @NotNull PsiManager manager,
                                       @NotNull Editor editor) {
        final PsiFile psiFile = (virtualFile == null) ? null : manager.findFile(virtualFile);

        if (psiFile == null) {
            return null;
        }
        final PsiElement elementAtCaret = psiFile.findElementAt(editor.getCaretModel().getOffset());

        return findPsiClass(elementAtCaret);
    }

    private static PsiClass findPsiClass(@Nullable PsiElement element) {
        while (true) {
            final PsiClass psiClass = (element instanceof PsiClass) ? (PsiClass) element
                    : PsiTreeUtil.getParentOfType(element, PsiClass.class);

            if (psiClass == null || !(psiClass.getContainingClass() instanceof PsiAnonymousClass)) {
                return psiClass;
            }
            element = psiClass.getParent();
        }
    }

    private static PsiClass findPsiClass(@NotNull PsiManager psiManager, @NotNull String className) {
        final Project project = psiManager.getProject();
        final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);

        return psiFacade.findClass(className, GlobalSearchScope.allScope(project));
    }


    public static boolean needsLoggerField(@Nullable PsiClass aClass) {
        if (aClass == null) {
            return false;
        }
//        if (aClass.isInterface() || aClass.isAnnotationType()) {
//            return false;
//        }
        return true;
    }

    public static boolean hasLoggerField(@Nullable PsiClass psiClass) {
        if (psiClass != null) {
            PsiField[] psiFields = psiClass.getFields();
            int length = psiFields.length;

            for (int i = 0; i < length; i++) {
                PsiField field = psiFields[i];
                if (field != null) {
                    PsiType psiType = field.getType();
                    if ("org.slf4j.Logger".equals(psiType.getCanonicalText())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void displayMessage(@NotNull final String message) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!GenerateLoggerHandler._showing) {
                        GenerateLoggerHandler._showing = true;
                        Messages.showErrorDialog(message, "Error");
                    }
                } finally {
                    GenerateLoggerHandler._showing = false;
                }
            }
        });
    }

    private static void insertLogger(Project project, PsiClass psiClass, PsiManager manager) {
        final PsiElementFactory psiElementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
        final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
        if (psiElementFactory != null && codeStyleManager != null) {
            try {
                final String fullDeclaration = String.format(LOGGER_FIELD_FORMAT, psiClass.getName());
                final PsiField psiField = psiElementFactory.createFieldFromText(fullDeclaration, null);

                if (psiField != null) {
                    codeStyleManager.reformat(psiField);
                    psiClass.add(psiField);
                }
                addImport(psiClass, psiElementFactory, manager, "org.slf4j.Logger");
                addImport(psiClass, psiElementFactory, manager, "org.slf4j.LoggerFactory");
            } catch (IncorrectOperationException e) {
                LOGGER.error("Could not insert field", e);
            }
        }
    }

    private static void addImport(PsiClass psiClass, PsiElementFactory elementFactory, PsiManager manager, String importClassName) {
        PsiFile file = psiClass.getContainingFile();
        if (file instanceof PsiJavaFile) {
            PsiJavaFile javaFile = (PsiJavaFile) file;
            PsiImportList importList = javaFile.getImportList();
            PsiImportStatementBase[] psiImportStatementBases = importList.getAllImportStatements();
            int length = psiImportStatementBases.length;

            for (int i = 0; i < length; i++) {
                PsiImportStatementBase is = psiImportStatementBases[i];
                String impQualifiedName = is.getImportReference().getQualifiedName();
                if (impQualifiedName.equals(importClassName)) {
                    return;
                }
            }

            PsiClass importClass = findPsiClass(manager, importClassName);
            PsiImportStatement importElement = elementFactory.createImportStatement(importClass);
            importList.add(importElement);
        }
    }
}
