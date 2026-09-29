package de.robv.android.xposed;
import de.robv.android.xposed.callbacks.XCallback;
import java.lang.reflect.Member;
public abstract class XC_MethodHook extends XCallback {
  public XC_MethodHook() { super(); }
  public XC_MethodHook(int priority) { super(priority); }
  protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}
  protected void afterHookedMethod(MethodHookParam param) throws Throwable {}
  public static class MethodHookParam extends XCallback.Param {
    public Member method;
    public Object thisObject;
    public Object[] args;
    private Object result;
    private Throwable throwable;
    boolean returnEarly = false;
    public Object getResult() { return result; }
    public void setResult(Object result) { this.result = result; this.throwable = null; this.returnEarly = true; }
    public Throwable getThrowable() { return throwable; }
    public void setThrowable(Throwable t) { this.throwable = t; this.result = null; this.returnEarly = true; }
    public boolean hasThrowable() { return throwable != null; }
  }
  public interface Unhook {
    void unhook();
  }
}
