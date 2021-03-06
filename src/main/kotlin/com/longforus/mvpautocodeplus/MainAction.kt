package com.longforus.mvpautocodeplus

import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.featureStatistics.ProductivityFeatureNames
import com.intellij.ide.util.PackageUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.WriteActionAware
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.util.PlatformIcons
import com.longforus.mvpautocodeplus.maker.TemplateMaker
import com.longforus.mvpautocodeplus.maker.TemplateParamFactory
import com.longforus.mvpautocodeplus.maker.createFileFromTemplate
import com.longforus.mvpautocodeplus.maker.overrideOrImplementMethods
import com.longforus.mvpautocodeplus.ui.EnterKeywordDialog
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.jetbrains.kotlin.psi.KtFile


/**
 * Created by XQ Yang on 2018/6/25  13:43.
 * Description :
 */

class MainAction : AnAction("main", "auto make mvp code", PlatformIcons.CLASS_ICON), WriteActionAware {
    var project: Project? = null
    lateinit var mSelectedState: PropertiesComponent
    fun createFile(enterName: String, templateName: String, dir: PsiDirectory, superImplName: String, contract: PsiFile? = null, fileName: String = enterName):
        PsiFile? {
        val template = TemplateMaker.getTemplate(templateName, project!!) ?: return null
        val liveTemplateDefaultValues = TemplateParamFactory.getParam4TemplateName(templateName, enterName, superImplName, contract, mSelectedState)
        val psiFile = createFileFromTemplate(fileName, template, dir, null, false, liveTemplateDefaultValues, mSelectedState.getValue(COMMENT_AUTHOR))
        if (!templateName.contains("Contract")) {
            val openFile = FileEditorManager.getInstance(project!!).openFile(psiFile!!.virtualFile, false)
            val textEditor = openFile[0] as TextEditor
            var clazz: PsiClass? = null
            if (psiFile is PsiJavaFile) {
                if (psiFile.classes.isEmpty()) {
                    return psiFile
                }
                clazz = psiFile.classes[0]
            } else if (psiFile is KtFile) {
                if (psiFile.classes.isEmpty()) {
                    return psiFile
                }
                clazz = psiFile.classes[0]
            }
            FeatureUsageTracker.getInstance().triggerFeatureUsed(ProductivityFeatureNames.CODEASSISTS_OVERRIDE_IMPLEMENT)
            overrideOrImplementMethods(project!!, textEditor.editor, clazz!!, true)
        }
        return psiFile
    }

    override fun actionPerformed(e: AnActionEvent) {
        val dataContext = e.dataContext
        val view = LangDataKeys.IDE_VIEW.getData(dataContext) ?: return
        project = CommonDataKeys.PROJECT.getData(dataContext)
        val dir = view.orChooseDirectory

        if (dir == null || project == null) return
//        var state: PropertiesComponent = PropertiesComponent.getInstance(project)
//        if (!state.getBoolean(USE_PROJECT_CONFIG,false)) {
//            state  = PropertiesComponent.getInstance()
//        }
//        if (state.getValue(SUPER_VIEW).isNullOrEmpty()) {
//            Messages.showErrorDialog("Super IView Interface name is null ! $GOTO_SETTING", "Error")
//            return
//        }
        EnterKeywordDialog.getDialog(project) {
            mSelectedState = it.state
            runWriteAction {
                if (it.isJava) {
                    val contractJ = createFile(it.name, if (it.generateModel) CONTRACT_TP_NAME_JAVA else CONTRACT_TP_NO_MODEL_NAME_JAVA, getSubDir(dir, CONTRACT),
                        "") as PsiJavaFile
                    if (!it.vImpl.isEmpty() && !it.vImpl.startsWith(IS_NOT_SET)) {
                        val sdV = getSubDir(dir, VIEW)
                        if (it.isActivity) {
                            createFile(it.name, VIEW_IMPL_TP_ACTIVITY_JAVA, sdV, it.vImpl, contractJ)
                        } else {
                            createFile(it.name, VIEW_IMPL_TP_FRAGMENT_JAVA, sdV, it.vImpl, contractJ)
                        }
                    }
                    if (!it.pImpl.isEmpty()) {
                        val sdP = getSubDir(dir, PRESENTER)
                        createFile(it.name, PRESENTER_IMPL_TP_JAVA, sdP, it.pImpl, contractJ)
                    }
                    if (!it.mImpl.isEmpty() && it.generateModel) {
                        val sdM = getSubDir(dir, MODEL)
                        createFile(it.name, MODEL_IMPL_TP_JAVA, sdM, it.mImpl, contractJ)
                    }

                } else {
                    val contractK = createFile(it.name, if (it.generateModel) CONTRACT_TP_NAME_KOTLIN else CONTRACT_TP_NO_MODEL_NAME_KOTLIN, getSubDir(dir, CONTRACT), "",
                        fileName = getContractName(it
                            .name))

                    if (!it.vImpl.isEmpty() && !it.vImpl.startsWith(IS_NOT_SET)) {
                        val sdV = getSubDir(dir, VIEW)
                        if (it.isActivity) {
                            createFile(it.name, VIEW_IMPL_TP_ACTIVITY_KOTLIN, sdV, it.vImpl, contractK, "${it.name}Activity")
                        } else {
                            createFile(it.name, VIEW_IMPL_TP_FRAGMENT_KOTLIN, sdV, it.vImpl, contractK, "${it.name}Fragment")
                        }
                    }
                    if (!it.pImpl.isEmpty()) {
                        val sdP = getSubDir(dir, PRESENTER)
                        createFile(it.name, PRESENTER_IMPL_TP_KOTLIN, sdP, it.pImpl, contractK, "${it.name}Presenter")
                    }
                    if (!it.mImpl.isEmpty() && it.generateModel) {
                        val sdM = getSubDir(dir, MODEL)
                        createFile(it.name, MODEL_IMPL_TP_KOTLIN, sdM, it.mImpl, contractK, "${it.name}Model")
                    }
                }
            }

        }

    }

    fun getSubDir(dir: PsiDirectory, dirName: String): PsiDirectory {
        return if (dir.name == CONTRACT) {
            if (dirName == CONTRACT) {
                dir
            } else {
                PackageUtil.findOrCreateSubdirectory(dir.parentDirectory!!, dirName)
            }
        } else {
            PackageUtil.findOrCreateSubdirectory(dir, dirName)
        }
    }

    override fun update(e: AnActionEvent?) {
        val dataContext = e!!.dataContext
        val presentation = e.presentation

        val enabled = isAvailable(dataContext)

        presentation.isVisible = enabled
        presentation.isEnabled = enabled
    }

    protected fun isAvailable(dataContext: DataContext): Boolean {
        val project = CommonDataKeys.PROJECT.getData(dataContext)
        val view = LangDataKeys.IDE_VIEW.getData(dataContext)
        return project != null && view != null && view.directories.isNotEmpty()
    }

}