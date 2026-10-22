"""The old profile header moved into the new shape, and nothing else touched."""
import os
import pathlib
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from margyt import dexpatch  # noqa: E402

STUBS = {
    'com/ss/android/ugc/aweme/profile/model/User.java': 'package com.ss.android.ugc.aweme.profile.model;public class User{}',
    'com/ss/android/ugc/profile/platform/base/data/ProfileComponents.java':
        'package com.ss.android.ugc.profile.platform.base.data;public class ProfileComponents{public String componentId,componentName;'
        'public java.util.List<? extends ProfileComponents> components;}',
    'com/ss/android/ugc/profile/platform/base/data/ProfileUser.java':
        'package com.ss.android.ugc.profile.platform.base.data;public class ProfileUser{public ProfileComponents headerComponents;}',
    'kotlin/jvm/functions/Function1.java': 'package kotlin.jvm.functions;public interface Function1<P,R>{R invoke(P p);}',
    'cat/narezany/margyt/Flags.java': 'package cat.narezany.margyt;class Flags{static boolean on=true;static boolean newProfile(){return on;}}',
    'cat/narezany/margyt/Diary.java': 'package cat.narezany.margyt;class Diary{static void note(String s){}}',
    # TikTok's own method, which the mod calls after moving the parts
    'X/0sm9.java': None,
}

HARNESS = r'''package cat.narezany.margyt;
import com.ss.android.ugc.profile.platform.base.data.*;import java.util.*;
public class HeaderHarness{
 static void check(boolean ok,String what){if(!ok)throw new AssertionError(what);}
 static ProfileComponents c(String name,ProfileComponents... kids){ProfileComponents p=new ProfileComponents();p.componentId=name;p.componentName=name;p.components=kids.length==0?null:new ArrayList<ProfileComponents>(Arrays.asList(kids));return p;}
 static String shape(ProfileComponents p){StringBuilder s=new StringBuilder(p.componentName);if(p.components!=null){s.append('[');for(int i=0;i<p.components.size();i++){if(i>0)s.append(',');s.append(shape(p.components.get(i)));}s.append(']');}return s.toString();}
 public static void main(String[] a){
  ProfileUser old=new ProfileUser();
  old.headerComponents=c("header",c("header_avatar",c("avatar_normal")),c("info",c("user_info",c("user_account_name_info"),c("user_account_base_info")),c("user_relation_info",c("relation_info_following"))),c("cta",c("cta_social_follow")),c("bio",c("bio_signature")),c("profile_banner"));
  check(ProfileHeader.rearrange(old),"the old header is moved");
  check(shape(old.headerComponents).equals("header[info[user_basic_info[header_avatar[avatar_normal],user_info[user_account_name_info,user_account_base_info]],user_relation_info[relation_info_following]],bio[bio_signature],cta[cta_social_follow],profile_banner]"),"into the new shape, the server's extras kept: "+shape(old.headerComponents));
  check(!ProfileHeader.rearrange(old),"and a second time does nothing");
  ProfileUser fresh=new ProfileUser();
  fresh.headerComponents=c("header",c("info",c("user_basic_info",c("header_avatar"),c("user_info")),c("user_relation_info")),c("bio"),c("cta"));
  String before=shape(fresh.headerComponents);
  check(!ProfileHeader.rearrange(fresh)&&shape(fresh.headerComponents).equals(before),"a new header is left alone");
  ProfileUser odd=new ProfileUser();odd.headerComponents=c("header",c("something_else"));
  check(!ProfileHeader.rearrange(odd),"and one it does not know");
  ProfileUser empty=new ProfileUser();check(!ProfileHeader.rearrange(empty),"and none at all");
  // through TikTok's method: moved first, then handed on
  ProfileUser page=new ProfileUser();page.headerComponents=c("header",c("header_avatar"),c("info",c("user_info")),c("cta"),c("bio"));
  check(ProfileHeader.LJIILIIL(null,page,true,null)==page&&shape(page.headerComponents).startsWith("header[info[user_basic_info"),"the call moves then hands on");
  check(X.Calls.count==1,"TikTok's own method ran once");
  Flags.on=false;ProfileUser kept=new ProfileUser();kept.headerComponents=c("header",c("header_avatar"),c("info",c("user_info")));
  ProfileHeader.LJIILIIL(null,kept,false,null);check(shape(kept.headerComponents).startsWith("header[header_avatar"),"with the switch off the page is TikTok's");
  System.out.println("header passed");
 }
}'''

TIKTOK = '''package X;
public class Calls{public static int count;}
'''


class ProfileHeaderTest(unittest.TestCase):
    def test_the_build_sends_every_page_through_the_mod(self):
        rules = [rule for rule in dexpatch.model_rules() if rule[0] == "0sm9->LJIILIIL"]
        self.assertEqual(len(rules), 1)
        _label, pattern, target = rules[0]
        call = ("    invoke-static {v0, v1, v2, v3}, LX/0sm9;->LJIILIIL(Lcom/ss/android/ugc/aweme/profile/model/User;"
                "Lcom/ss/android/ugc/profile/platform/base/data/ProfileUser;ZLkotlin/jvm/functions/Function1;)"
                "Lcom/ss/android/ugc/profile/platform/base/data/ProfileUser;\n")
        out, hits = pattern.subn(target, call)
        self.assertEqual(hits, 1)
        self.assertIn("Lcat/narezany/margyt/ProfileHeader;->LJIILIIL(", out)
        with open(ROOT / "margyt/build.py", encoding="utf-8") as handle:
            self.assertIn('feature_hits["native profile header"] += counts.get("0sm9->LJIILIIL",0)', handle.read())

    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required')
    def test_the_old_header_is_moved_into_the_new_shape(self):
        with tempfile.TemporaryDirectory() as directory:
            work = pathlib.Path(directory)
            sources = {k: v for k, v in STUBS.items() if v is not None}
            # "0sm9" is not a name javac allows, so TikTok's class is compiled
            # under another and renamed in the class file's own bytes
            sources['X/Zsm9.java'] = ('package X;public class Zsm9{public static com.ss.android.ugc.profile.platform.base.data.ProfileUser '
                                      'LJIILIIL(com.ss.android.ugc.aweme.profile.model.User u,com.ss.android.ugc.profile.platform.base.data.ProfileUser p,'
                                      'boolean m,kotlin.jvm.functions.Function1 f){Calls.count++;return p;}}')
            sources['X/Calls.java'] = TIKTOK
            sources['cat/narezany/margyt/ProfileHeader.java'] = (ROOT / 'inject/java/cat/narezany/margyt/ProfileHeader.java').read_text(encoding='utf-8')
            sources['cat/narezany/margyt/HeaderHarness.java'] = HARNESS
            for name, content in sources.items():
                path = work / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content, encoding='utf-8')
            built = subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(work)] + [str(work / n) for n in sources],
                                   capture_output=True, text=True)
            self.assertEqual(0, built.returncode, built.stderr)
            renamed = (work / 'X/Zsm9.class').read_bytes().replace(b'X/Zsm9', b'X/0sm9')
            (work / 'X/0sm9.class').write_bytes(renamed)
            run = subprocess.run(['java', '-cp', str(work), 'cat.narezany.margyt.HeaderHarness'], capture_output=True, text=True)
            self.assertEqual(0, run.returncode, run.stdout + run.stderr)


if __name__ == '__main__':
    unittest.main()
