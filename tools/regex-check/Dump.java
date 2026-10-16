import java.io.*; import java.lang.reflect.*; import java.nio.file.*; import java.util.*; import java.util.regex.Pattern;
/**
 * Writes every static Pattern/Regex field (Kotlin objects and companions, and lists or maps of them)
 * of the classes under the given directories as "flags<TAB>pattern" records separated by NUL bytes.
 * Usage: Dump <out file> <class dir>... (dependencies on -cp). Classes that fail to load are skipped.
 */
public class Dump {
  public static void main(String[] a) throws Exception {
    OutputStream out = new FileOutputStream(a[0]);
    List<Path> roots = new ArrayList<>(); List<java.net.URL> urls = new ArrayList<>();
    for (int i = 1; i < a.length; i++) { Path r = Paths.get(a[i]); if (Files.isDirectory(r)) { roots.add(r); urls.add(r.toUri().toURL()); } }
    java.net.URLClassLoader cl = new java.net.URLClassLoader(urls.toArray(new java.net.URL[0]), Dump.class.getClassLoader());
    List<String> names = new ArrayList<>();
    for (Path root : roots) Files.walk(root).filter(p -> p.toString().endsWith(".class"))
        .forEach(p -> names.add(root.relativize(p).toString().replace('/', '.').replaceAll("\\.class$", "")));
    int n = 0;
    for (String name : names) {
      Class<?> c; try { c = Class.forName(name, true, cl); } catch (Throwable t) { continue; }
      Field[] fields; try { fields = c.getDeclaredFields(); } catch (Throwable t) { continue; }
      for (Field fd : fields) {
        if (!Modifier.isStatic(fd.getModifiers())) continue;
        Object v; try { fd.setAccessible(true); v = fd.get(null); } catch (Throwable t) { continue; }
        List<Object> vals = new ArrayList<>();
        if (v instanceof Collection) vals.addAll((Collection<?>) v);
        else if (v instanceof Map) vals.addAll(((Map<?, ?>) v).values());
        else if (v instanceof Object[]) vals.addAll(Arrays.asList((Object[]) v));
        else vals.add(v);
        for (Object x : vals) {
          Pattern p = null;
          if (x instanceof Pattern) p = (Pattern) x;
          else if (x instanceof kotlin.text.Regex) p = ((kotlin.text.Regex) x).toPattern();
          if (p == null) continue;
          out.write((p.flags() + "\t" + p.pattern()).getBytes("UTF-8")); out.write(0); n++;
        }
      }
    }
    out.close(); System.out.println(n);
  }
}
