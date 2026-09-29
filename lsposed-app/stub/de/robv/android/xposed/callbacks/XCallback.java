package de.robv.android.xposed.callbacks;
public abstract class XCallback implements Comparable<XCallback> {
  public static final int PRIORITY_HIGHEST = -10000;
  public static final int PRIORITY_DEFAULT = 50;
  public static final int PRIORITY_LOWEST = 10000;
  public final int priority;
  public XCallback() { this(PRIORITY_DEFAULT); }
  public XCallback(int priority) { this.priority = priority; }
  @Override public int compareTo(XCallback o) { return priority - o.priority; }
  protected void call(Param param) throws Throwable {}
  public static class Param {
    @SuppressWarnings("unchecked")
    public <T> T getObjectExtra(String key) { return null; }
    public void setObjectExtra(String key, Object o) {}
  }
}
