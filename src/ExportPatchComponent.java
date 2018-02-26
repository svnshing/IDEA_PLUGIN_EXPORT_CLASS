import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.ui.ex.MessagesEx;
import org.jetbrains.annotations.NotNull;

public class ExportPatchComponent
        implements ApplicationComponent
{
    String msg;

    public void setMsg(String msg)
    {
        this.msg = msg;
    }

    public void initComponent()
    {
    }

    public void disposeComponent()
    {
    }

    @NotNull
    public String getComponentName()
    {
        return "ExportJARComponent";
    }

    public void showResult() {
        MessagesEx.showInfoMessage(this.msg, "导出补丁结果:");
    }
}