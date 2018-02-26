import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesSelection;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import util.FileUtil;
import util.ZipUtil;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

/**
 * editor and change  by liwj on 2018/2/1.
 */
public class ExportPatchAction extends AnAction {
    public static final String PATCH_ROOT_FOLDER = "patch";
    private String projectPath;
    private String java_source_path = "\\src\\main\\java";
    private String conf_path = "\\src\\main\\resources";
    private String class_path = "\\WEB-INF\\classes";
    private String page_patch = "\\WebRoot";
    private String web_path = "";
    private String out_patch = "\\target\\classes";
    private String wfTemplete = "";
    private String logFile;
    private String pathFolder;
    private StringBuffer copyFile = new StringBuffer("已导出文件：\n");
    private StringBuffer noExistFile = new StringBuffer("\n\n不存在的文件[导出前需要先编译]：\n");
    private StringBuffer noCopyFile = new StringBuffer("\n\n不需要导出的文件：");
    private int copyFileCount = 0;
    private int noExistFileCount = 0;
    private int noCopyFileCount = 0;
    Application application = ApplicationManager.getApplication();
    ExportPatchComponent exportJARComponent = application.getComponent(ExportPatchComponent.class);

    public void actionPerformed(AnActionEvent event) {
        copyFileCount = 0;
        noExistFileCount = 0;
        noCopyFileCount = 0;
        Project project = event.getData(CommonDataKeys.PROJECT);
        this.projectPath = processPath(project.getBasePath());
        this.wfTemplete = (this.projectPath + "\\templates\\workflowTemplate");
        this.logFile = (this.projectPath + File.separator + getTodayPatchFolder() + File.separator + getYYYYMMDDHHMMSS() + File.separator + "log.txt");
        this.pathFolder = (this.projectPath + File.separator + getTodayPatchFolder() + File.separator + getYYYYMMDDHHMMSS());

        ChangesSelection changesSelection = event.getData(VcsDataKeys.CHANGES_SELECTION);

        if (changesSelection != null) {
            List<Change> changes = changesSelection.getChanges();
            for (Change change : changes) {
                FilePath filePath = ChangesUtil.getFilePath(change);
                copyFile(processPath(filePath.getPath()));
            }
            //压缩文件夹
            genZip2(this.projectPath,false);

        }
        FileUtil.writeExportLog(this.logFile, this.copyFile);
        FileUtil.writeExportLog(this.logFile, this.noExistFile);
        FileUtil.writeExportLog(this.logFile, this.noCopyFile);
        String msg = "已导出文件:" + this.copyFileCount + "个\n";
        msg = msg + "不存在文件:" + this.noExistFileCount + "个\n";
        msg = msg + "不需要导出文件:" + this.noCopyFileCount + "个\n";
        msg = msg + "详细内容请看日志文件：\n" + this.logFile;
        exportJARComponent.setMsg(msg);
        exportJARComponent.showResult();
    }


    private boolean copyFile(String src) {
        String desc = "";
        String outFile = "";
        boolean needCopy = false;
        boolean copyInnerClass = false;
        File[] targetClassFile = new File[]{};
        if (src.contains(getProjItemPath(this.java_source_path))) {
            desc = changeJavaToClass(src.replace(getProjItemPath(this.java_source_path), getTargetPath(this.class_path)));
            src = changeJavaToClass(src.replace(this.java_source_path, this.out_patch));
            outFile = desc.replace(getTargetPath(""), "");
            needCopy = true;


            //TODO 增加导出java内部类的功能
            String srcClassFilePath = src.substring(0, src.lastIndexOf("\\"));
            String srcClassName = src.replace(srcClassFilePath + "\\", "").replace(".class", "");
            File srcClassDir = new File(srcClassFilePath);
            if (srcClassDir.isDirectory()) {
                Pattern pattern = Pattern.compile(new StringBuilder(srcClassName).append("\\$(\\w|\\W)*.class$").toString());
                targetClassFile = srcClassDir.listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File pathname) {
                        return pattern.matcher(pathname.getName()).find();//匹配所有的内部类
                    }
                });
                copyInnerClass = targetClassFile.length > 0;
            }
            if (copyInnerClass) {
                //先复制外部class文件
                try {
                    FileUtil.copyFile(src, desc);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                //再复制内部类
                for (File file : targetClassFile) {
                    String src_ = file.getAbsolutePath();
                    String desc_ = src_.replace(this.projectPath + this.out_patch, getTargetPath(this.class_path));
                    try {
                        FileUtil.copyFile(src_, desc_);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        if (src.contains(getProjItemPath(this.conf_path))) {
            desc = src.replace(getProjItemPath(this.conf_path), getTargetPath(this.class_path));
            src = src.replace(this.java_source_path, this.out_patch);
            outFile = desc.replace(getTargetPath(""), "");
            needCopy = true;
        }
        if (src.contains(this.page_patch)) {
            desc = src.replace(getProjItemPath(this.page_patch), getTargetPath(this.web_path));
            outFile = desc.replace(getTargetPath(""), "");
            needCopy = true;
        }
        if (src.contains(this.wfTemplete)) {
            desc = src.replace(this.wfTemplete, getTargetPath("\\workflowTemplate"));
            outFile = desc.replace(getTargetPath(""), "");
            needCopy = true;
        }
        try {
            if (!needCopy) {
                this.noCopyFile.append(src.replace(this.projectPath, "")).append("\n");
                this.noCopyFileCount += 1;
                return false;
            }
            File file = new File(src);
            if (!file.exists()) {
                this.noExistFile.append(src.replace(this.projectPath, "")).append("\n");
                this.noExistFileCount += 1;
                return false;
            }
            if (!copyInnerClass) FileUtil.copyFile(src, desc);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        this.copyFile.append(outFile).append("\n");
        this.copyFileCount += 1;
        return true;
    }

    private String getTargetPath(String path) {
        return this.pathFolder + path;
    }

    private String changeJavaToClass(String filePath) {
        return filePath.replace(".java", ".class");
    }

    private String processPath(String filePath) {
        if ("\\".equals(File.separator)) {
            filePath = filePath.replaceAll("/", "\\\\");
        }
        return filePath;
    }

    private String getProjItemPath(String itemPath) {
        return this.projectPath + itemPath;
    }

    private static void genZip2(String projectBasePatch,boolean delSrc) {
        String patch_date_folder = getTodayPatchFolder();
        File dir = new File(projectBasePatch + File.separator + patch_date_folder);

        File zipFile = new File(dir.getPath() + File.separator + getYYYYMMDDHHMMSS() + ".zip");

        ZipUtil appZip = new ZipUtil(dir, zipFile.getAbsolutePath());
        appZip.zipIt(delSrc);
    }

    private static String getTodayPatchFolder() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("YYYYMMdd");
        String dateFolder = dateFormat.format(new Date());
        return PATCH_ROOT_FOLDER + File.separator + dateFolder;
    }

    private static String getYYYYMMDDHHMMSS() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("YYYYMMddHHmmss");
        return dateFormat.format(new Date());
    }

    public static void main(String[] args) {
        String srcClassName = "XjStatisticsTreeImpl";
        String descClassName = "XjStatisticsTreeImpl$CatalogInfo.class";
        Pattern pattern = Pattern.compile(new StringBuilder(srcClassName).append("\\$(\\w|\\W)*.class$").toString());
        System.out.printf("匹配结果:" + pattern.matcher(descClassName).find());
    }
}
