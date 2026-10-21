import unittest,tempfile,pathlib,struct,json,subprocess,sys
ASSETS=pathlib.Path(__file__).resolve().parents[1]/'android-app/app/src/main/assets/device-build'
scope={'__name__':'rust_windows_test','__file__':str(ASSETS/'rust-windows.py')};exec(compile((ASSETS/'rust-windows.py').read_bytes(),str(ASSETS/'rust-windows.py'),'exec'),scope)
def pe(dll=False):
 b=bytearray(512);b[:2]=b'MZ';struct.pack_into('<I',b,60,64);b[64:68]=b'PE\0\0';struct.pack_into('<HH',b,68,0x8664,1);struct.pack_into('<HH',b,84,112,2|(0x2000 if dll else 0));struct.pack_into('<H',b,88,0x20b);struct.pack_into('<II',b,216,32,300);return b
class WindowsRustTests(unittest.TestCase):
 def setUp(self):self.tmp=tempfile.TemporaryDirectory();self.root=pathlib.Path(self.tmp.name);self.file=self.root/'demo.exe';self.file.write_bytes(pe())
 def tearDown(self):self.tmp.cleanup()
 def test_executable_shape(self):scope['check_pe'](self.file)
 def test_dll_not_mislabelled_exe(self):self.file.write_bytes(pe(True));self.assertRaises(ValueError,scope['check_pe'],self.file)
 def test_other_machine(self):b=pe();struct.pack_into('<H',b,68,0xaa64);self.file.write_bytes(b);self.assertRaises(ValueError,scope['check_pe'],self.file)
 def test_bad_section(self):b=pe();struct.pack_into('<II',b,216,1000,300);self.file.write_bytes(b);self.assertRaises(ValueError,scope['check_pe'],self.file)
 def test_truncated_header(self):self.file.write_bytes(pe()[:65]);self.assertRaises(ValueError,scope['check_pe'],self.file)
 def test_header_escape(self):b=pe();struct.pack_into('<I',b,60,0xffffffff);self.file.write_bytes(b);self.assertRaises(ValueError,scope['check_pe'],self.file)
 def test_symlink(self):link=self.root/'link';link.symlink_to(self.file);self.assertRaises(ValueError,scope['check_pe'],link)
 def test_missing_binding(self):self.assertRaises(ValueError,scope['config'],self.root)
 def test_traversal_binding(self):
  (self.root/'appforge-rust-windows.json').write_text(json.dumps(dict(schema=1,target='x86_64-pc-windows-gnu',executable='../outside.exe')));self.assertRaises(ValueError,scope['config'],self.root)
 def test_wrong_target_binding(self):
  (self.root/'appforge-rust-windows.json').write_text(json.dumps(dict(schema=1,target='aarch64-pc-windows-msvc',executable='demo.exe')));self.assertRaises(ValueError,scope['config'],self.root)
 def test_dll_requires_explicit_library_schema(self):
  (self.root/'appforge-rust-windows.json').write_text(json.dumps(dict(schema=2,target='x86_64-pc-windows-gnu',library='fixture.dll',export='appforge_answer')))
  self.assertEqual(scope['binding'](self.root),('fixture.dll','dll','appforge_answer'))
 def test_executable_cannot_use_dll_kind(self):self.assertRaises(ValueError,scope['check_pe'],self.file,'dll')
 def test_forward_library_name_escape_rejected(self):
  (self.root/'appforge-rust-windows.json').write_text(json.dumps(dict(schema=2,target='x86_64-pc-windows-gnu',library='../fixture.dll',export='appforge_answer')));self.assertRaises(ValueError,scope['binding'],self.root)
 def test_no_implicit_execution(self):
  p=subprocess.run([sys.executable,'-I','-S',str(ASSETS/'rust-windows.py'),str(self.root)],capture_output=True,text=True);self.assertNotEqual(p.returncode,0);self.assertIn('Explicit source execution consent',p.stderr)
if __name__=='__main__':unittest.main()
