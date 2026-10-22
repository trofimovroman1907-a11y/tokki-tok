"""A .mttheme is taken whole or not at all, and the example in the repository is one."""
import glob
import os
import pathlib
import re
import shutil
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
# android.jar's json, stood in for by the same library on the computer
JSON_JAR = next(iter(sorted(glob.glob(os.path.expanduser(
    "~/.gradle/caches/modules-2/files-2.1/org.json/json/*/*/json-*.jar")))), None)
JSON_JAR = JSON_JAR if JSON_JAR and "sources" not in JSON_JAR else None


def text_stub():
    """Every THEME_FILE_ string of the real Text, as its Russian."""
    source = (ROOT / "inject/java/cat/narezany/margyt/Text.java").read_text(encoding="utf-8")
    names = sorted(set(re.findall(r"static final String (THEME_FILE_\w+)", source)))
    return "package cat.narezany.margyt;class Text{" + "".join(
        'static final String %s="%s";' % (name, name) for name in names) + "}"


STUBS = {
    "android/content/Context.java": "package android.content;public class Context{}",
    "android/content/SharedPreferences.java": "package android.content;public interface SharedPreferences{}",
    "android/graphics/Color.java": "package android.graphics;public class Color{public static int parseColor(String s){"
        "long v=Long.parseLong(s.substring(1),16);if(s.length()==7)v|=0xFF000000L;return (int)v;}}",
    "cat/narezany/margyt/Design.java": 'package cat.narezany.margyt;class Design{static boolean valid(String m){'
        'return "md3".equals(m)||"glass".equals(m)||"fluent".equals(m);}}',
    "cat/narezany/margyt/AppearanceColors.java": 'package cat.narezany.margyt;class AppearanceColors{static final String ACCENT="accent";'
        'static final String[] ROLES={"accent","background","secondary_background","surface","text","secondary_text","divider",'
        '"selected_tab","nav_icon","control","switch","progress","profile","nickname"};}',
}

HARNESS = r'''package cat.narezany.margyt;
public class Test{
 static void check(boolean ok,String what){if(!ok)throw new AssertionError(what);}
 static String refused(String json){try{ThemeFile.read(json.getBytes("UTF-8"));return null;}catch(ThemeFile.Refused r){return r.getMessage();}catch(Exception e){throw new RuntimeException(e);}}
 public static void main(String[] a)throws Exception{
  ThemeFile.Theme example=ThemeFile.read(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(a[0])));
  check(example.name.equals("Мятная ночь")&&example.author.equals("narezany"),"the example is read: "+example.name);
  check(example.colours==33,"every colour of the example counted: "+example.colours);
  check(refused("{\"mttheme\":1,\"name\":\"x\"}")==null,"only the format and a name are needed");
  check(refused("[]").equals(Text.THEME_FILE_NOT_JSON),"not json");
  check(refused("{\"name\":\"x\"}").equals(Text.THEME_FILE_NOT_THEME),"no format number");
  check(refused("{\"mttheme\":2,\"name\":\"x\"}").equals(Text.THEME_FILE_NEWER),"a newer format");
  check(refused("{\"mttheme\":1}").equals(Text.THEME_FILE_NO_NAME),"no name");
  check(refused("{\"mttheme\":1,\"name\":\"x\",\"dark\":{\"text\":\"white\"}}").startsWith(Text.THEME_FILE_BAD_COLOUR),"a colour by name is refused");
  check(refused("{\"mttheme\":1,\"name\":\"x\",\"accent\":\"#12345\"}").startsWith(Text.THEME_FILE_BAD_COLOUR),"a short colour is refused");
  check(refused("{\"mttheme\":1,\"name\":\"x\",\"design\":\"aero\"}").startsWith(Text.THEME_FILE_BAD_DESIGN),"an unknown style");
  check(refused("{\"mttheme\":1,\"name\":\"x\",\"dark\":{\"sparkles\":\"nope\"},\"future\":true}")==null,"unknown roles and fields are passed over");
  check(ThemeFile.colour("#805BE7B9")==0xFF5BE7B9,"a theme colour is always solid");
  check(ThemeFile.fileName("a/b: c?").equals("a b  c.mttheme"),"a name a file system takes: "+ThemeFile.fileName("a/b: c?"));
  StringBuilder big=new StringBuilder("{\"mttheme\":1,\"name\":\"x\",\"description\":\"");for(int i=0;i<70000;i++)big.append('a');big.append("\"}");
  check(refused(big.toString()).equals(Text.THEME_FILE_BIG),"over 64 KB");
  System.out.println("themes passed");
 }
}'''


class ThemeFileTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which("javac") and shutil.which("java") and JSON_JAR, "JDK and org.json required")
    def test_a_theme_is_taken_whole_or_not_at_all(self):
        with tempfile.TemporaryDirectory() as directory:
            work = pathlib.Path(directory)
            sources = dict(STUBS)
            sources["cat/narezany/margyt/Text.java"] = text_stub()
            sources["cat/narezany/margyt/ThemeFile.java"] = (ROOT / "inject/java/cat/narezany/margyt/ThemeFile.java").read_text(encoding="utf-8")
            sources["cat/narezany/margyt/Test.java"] = HARNESS
            # only reading is exercised: what puts a theme on is cut out with its Android calls
            body = sources["cat/narezany/margyt/ThemeFile.java"]
            start = body.index("    // ------------------------------------------------------------ putting on")
            end = body.index("    /** A name a file system takes. */")
            sources["cat/narezany/margyt/ThemeFile.java"] = body[:start] + body[end:]
            for name, content in sources.items():
                path = work / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content, encoding="utf-8")
            built = subprocess.run(["javac", "-encoding", "UTF-8", "-cp", JSON_JAR, "-d", str(work)]
                                   + [str(work / n) for n in sources], capture_output=True, text=True)
            self.assertEqual(0, built.returncode, built.stderr)
            run = subprocess.run(["java", "-cp", str(work) + os.pathsep + JSON_JAR, "cat.narezany.margyt.Test",
                                  str(ROOT / "examples/mint-night.mttheme")], capture_output=True, text=True)
            self.assertEqual(0, run.returncode, run.stdout + run.stderr)


if __name__ == "__main__":
    unittest.main()
