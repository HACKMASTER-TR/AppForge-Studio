import unittest,tempfile,pathlib,struct,json,subprocess,sys
ASSETS=pathlib.Path(__file__).resolve().parents[1]/'android-app/app/src/main/assets/device-build'
scope={'__name__':'rust_android_test','__file__':str(ASSETS/'rust-android.py')};exec(compile((ASSETS/'rust-android.py').read_bytes(),str(ASSETS/'rust-android.py'),'exec'),scope)
def elf(symbol=b'Java_com_example_Test_answer',alignment=16384,defined=True):
    b=bytearray(512);b[:6]=b'\x7fELF\x02\x01';struct.pack_into('<HH',b,16,3,183)
    struct.pack_into('<QQ',b,32,64,128);struct.pack_into('<HHHH',b,54,56,1,64,3)
    struct.pack_into('<IIQQQQQQ',b,64,1,5,0,0,0,len(b),len(b),alignment)
    struct.pack_into('<IIQQQQIIQQ',b,192,0,11,0,0,320,24,2,0,8,24)
    strings=b'\0'+symbol+b'\0';struct.pack_into('<IIQQQQIIQQ',b,256,0,3,0,0,352,len(strings),0,0,1,0)
    struct.pack_into('<IBBHQQ',b,320,1,0x12,0,1 if defined else 0,0,0);b[352:352+len(strings)]=strings
    return b
class AndroidRustTests(unittest.TestCase):
    def setUp(self):self.tmp=tempfile.TemporaryDirectory();self.root=pathlib.Path(self.tmp.name);self.lib=self.root/'libfixture.so'
    def tearDown(self):self.tmp.cleanup()
    def test_real_defined_jni_shape(self):self.lib.write_bytes(elf());self.assertEqual(scope['exports'](self.lib),['Java_com_example_Test_answer'])
    def test_c_abi_not_jni(self):self.lib.write_bytes(elf(b'add'));self.assertRaises(ValueError,scope['exports'],self.lib)
    def test_undefined_jni_rejected(self):self.lib.write_bytes(elf(defined=False));self.assertRaises(ValueError,scope['exports'],self.lib)
    def test_android16_alignment_rejected(self):self.lib.write_bytes(elf(alignment=4096));self.assertRaises(ValueError,scope['exports'],self.lib)
    def test_truncated_sections_rejected(self):self.lib.write_bytes(elf()[:100]);self.assertRaises(ValueError,scope['exports'],self.lib)
    def test_wrong_architecture_rejected(self):b=elf();struct.pack_into('<H',b,18,62);self.lib.write_bytes(b);self.assertRaises(ValueError,scope['exports'],self.lib)
    def test_symlink_rejected(self):real=self.root/'real';real.write_bytes(elf());self.lib.symlink_to(real);self.assertRaises(ValueError,scope['exports'],self.lib)
    def test_bad_section_link_rejected(self):b=elf();struct.pack_into('<I',b,192+40,4000);self.lib.write_bytes(b);self.assertRaises(ValueError,scope['exports'],self.lib)
    def test_explicit_binding_required(self):self.assertRaises(ValueError,scope['config'],self.root)
    def test_binding_cannot_escape_app_module(self):
        (self.root/'appforge-rust-android.json').write_text(json.dumps(dict(schema=1,module='../other',library='fixture',minApi=26)))
        self.assertRaises(ValueError,scope['config'],self.root)
    def test_corrupt_pinned_std_rejected_before_execution(self):
        name=json.loads((ASSETS/'rust-android-target-v1.json').read_text())['artifact']['file'];(self.root/name).write_bytes(b'wrong archive')
        self.assertRaisesRegex(ValueError,'SHA-256',scope['install_target'],self.root/'prefix',self.root)
    def test_no_implicit_source_execution(self):
        p=subprocess.run([sys.executable,'-I','-S',str(ASSETS/'rust-android.py'),str(self.root)],capture_output=True,text=True)
        self.assertNotEqual(p.returncode,0);self.assertIn('Explicit source execution consent',p.stderr)
    def test_missing_linker_fails_closed(self):self.assertRaisesRegex(ValueError,'linker missing',scope['discover_linker'],self.root)
if __name__=='__main__':unittest.main()
