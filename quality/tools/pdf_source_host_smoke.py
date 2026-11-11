#!/usr/bin/env python3
"""Execute production PDF source validation using PDFBox at the Android parser boundary.
This is host evidence, not Android PdfRenderer or device acceptance. Supply verified PDFBox
3.0.6 pdfbox/pdfbox-io/fontbox and commons-logging 1.3.5 JARs in --parser-jars.
"""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile
import shutil

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--parser-jars', required=True)
parser.add_argument("--fixture-output", help="Optional test-only encrypted PDF fixture directory")
args = parser.parse_args()
root = Path(__file__).resolve().parents[2]
with tempfile.TemporaryDirectory(prefix='appforge-pdf-host-') as folder:
    work = Path(folder)
    sources = {
        'ParcelFileDescriptor.java': '''package android.os;
public final class ParcelFileDescriptor implements java.io.Closeable {
 public static final int MODE_READ_ONLY=1; public final java.io.File file;
 private ParcelFileDescriptor(java.io.File file){this.file=file;}
 public static ParcelFileDescriptor open(java.io.File file,int mode) throws java.io.IOException {
  if(mode!=MODE_READ_ONLY || !file.isFile())throw new java.io.IOException();return new ParcelFileDescriptor(file);
 }
 public void close(){}
}''',
        'PdfRenderer.java': '''package android.graphics.pdf;
public final class PdfRenderer implements java.io.Closeable {
 private final org.apache.pdfbox.pdmodel.PDDocument document;
 public PdfRenderer(android.os.ParcelFileDescriptor fd) throws java.io.IOException {
  try{document=org.apache.pdfbox.Loader.loadPDF(fd.file);}
  catch(org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException e){throw new SecurityException("Password required",e);}
 }
 public int getPageCount(){return document.getNumberOfPages();}
 public Page openPage(int index){return new Page(document.getPage(index));}
 public void close() throws java.io.IOException {document.close();}
 public static class Page implements java.io.Closeable {
  private final org.apache.pdfbox.pdmodel.PDPage page;Page(org.apache.pdfbox.pdmodel.PDPage page){this.page=page;}
  public int getWidth(){return (int)page.getMediaBox().getWidth();}
  public int getHeight(){return (int)page.getMediaBox().getHeight();}
  public void close(){}
 }
}''',
        'Fixtures.java': '''import java.io.File;import org.apache.pdfbox.pdmodel.*;import org.apache.pdfbox.pdmodel.encryption.*;
public class Fixtures {
 public static void main(String[] args)throws Exception {
  for(int count:new int[]{1,3})try(PDDocument document=new PDDocument()){
   for(int i=0;i<count;i++)document.addPage(new PDPage());document.save(new File(args[0],"valid-"+count+".pdf"));
  }
  try(PDDocument document=new PDDocument()){
   document.addPage(new PDPage());StandardProtectionPolicy policy=new StandardProtectionPolicy("fixture-owner","fixture-user",new AccessPermission());
   policy.setEncryptionKeyLength(128);document.protect(policy);document.save(new File(args[0],"encrypted.pdf"));
  }
 }
}''',
    }
    for name, value in sources.items():
        (work / name).write_text(value)
    subprocess.run(['javac', '-cp', args.parser_jars, '-d', str(work), *(str(work / name) for name in sources)], check=True)
    cp = str(work) + os.pathsep + args.parser_jars
    subprocess.run(['java', '-cp', cp, 'Fixtures', str(work)], check=True)
    if args.fixture_output:
        destination = Path(args.fixture_output)
        destination.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(work / 'encrypted.pdf', destination / 'encrypted.pdf')
    (work / 'Check.kt').write_text('''import com.appforge.studio.io.PdfProjectSource
import java.io.File
fun main(args:Array<String>) {
 val root=File(args[0])
 check(PdfProjectSource.validate(File(root,"valid-1.pdf"))==1)
 check(PdfProjectSource.validate(File(root,"valid-3.pdf"))==3)
 val encrypted=runCatching{PdfProjectSource.validate(File(root,"encrypted.pdf"))}.exceptionOrNull()
 check(encrypted is IllegalArgumentException && encrypted.message!!.contains("Parola"))
 for(text in listOf("not PDF", "%PDF-1.4\\ncorrupt\\n%%EOF")) {
  val bad=File(root,"corrupt.pdf").apply{writeText(text)}
  check(runCatching{PdfProjectSource.validate(bad)}.isFailure)
 }
 val project=File(root,"project").apply{mkdir()}
 File(root,"valid-3.pdf").copyTo(File(project,PdfProjectSource.DOCUMENT))
 PdfProjectSource.prepare(project);check(PdfProjectSource.isProject(project))
 File(project,PdfProjectSource.DOCUMENT).appendText("changed")
 check(!PdfProjectSource.isProject(project))
 val oversized=File(root,"oversized.pdf")
 java.io.RandomAccessFile(oversized,"rw").use{it.setLength(PdfProjectSource.MAX_BYTES+1)}
 check(runCatching{PdfProjectSource.validate(oversized)}.isFailure)
 val link=File(root,"linked.pdf");java.nio.file.Files.createSymbolicLink(link.toPath(),File(root,"valid-1.pdf").toPath())
 check(runCatching{PdfProjectSource.validate(link)}.isFailure)
 println("PASS production PDF source: valid, multipage, encrypted, corrupt, oversized, symlink and hash tamper (PDFBox host parser boundary)")
}''')
    subprocess.run(['kotlinc', str(root / 'android-app/app/src/main/java/com/appforge/studio/io/PdfProjectSource.kt'), str(work / 'Check.kt'), '-cp', cp, '-include-runtime', '-d', str(work / 'check.jar')], check=True)
    subprocess.run(['java', '-cp', str(work / 'check.jar') + os.pathsep + cp, 'CheckKt', str(work)], check=True)
