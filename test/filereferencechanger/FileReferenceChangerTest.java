package filereferencechanger;
import java.io.File;
import java.io.IOException;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Matcher;

import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.BuildFileTest;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FileSet;

import filereferencechanger.FileReferenceChanger;

public class FileReferenceChangerTest extends BuildFileTest {

	public static Logger logger = Logger.getLogger(FileReferenceChangerTest.class.getName());
	{
		logger.setLevel(Level.FINE);
		Handler consoleHandler = new ConsoleHandler();
		consoleHandler.setLevel(Level.INFO);
		logger.addHandler(consoleHandler);
		consoleHandler.setFormatter(new Formatter() {
			@Override
			public String format(LogRecord record) {
				return record.getMessage()+"\n";
			}
		});
	}
	
	public void testUrlPattern(){
		
		
		//test no quotes
		Matcher m = FileReferenceChanger.getCssUrlPattern().matcher("url(../../images/test.jpg)");
		m.find();
		logger.fine(m.group(1));
		logger.fine(m.group(2));
		assertEquals("", m.group(1));
		assertEquals("../../images/test.jpg", m.group(2));
		
		//test no quotes - special characters MUST be escaped
		m = FileReferenceChanger.getCssUrlPattern().matcher("url(../../images/\\(m\\ y\\ test.jpg)");
		m.find();
		logger.fine(m.group(2));
		assertEquals("", m.group(1));
		assertEquals("../../images/\\(m\\ y\\ test.jpg", m.group(2));
		
		
		//test single quotes
		m = FileReferenceChanger.getCssUrlPattern().matcher("url('../../images/test.jpg')");
		m.find();
		logger.fine(m.group(2));
		assertEquals("'", m.group(1));
		assertEquals("../../images/test.jpg", m.group(2));
		
		//test single quotes - special characters MAY be escaped
		m = FileReferenceChanger.getCssUrlPattern().matcher("url('../../images/\\((m\\ y test.jpg')");
		m.find();
		logger.fine(m.group(2));
		assertEquals("'", m.group(1));
		assertEquals("../../images/\\((m\\ y test.jpg", m.group(2));
		
		//test double quotes
		m = FileReferenceChanger.getCssUrlPattern().matcher("url(\"../../images/test.jpg\")");
		m.find();
		logger.fine(m.group(2));
		assertEquals("\"", m.group(1));
		assertEquals("../../images/test.jpg", m.group(2));
		
		//test double quotes - special characters MAY be escaped
		m = FileReferenceChanger.getCssUrlPattern().matcher("url(\"../../images/\\((m\\ y test.jpg\")");
		m.find();
		logger.fine(m.group(2));
		assertEquals("\"", m.group(1));
		assertEquals("../../images/\\((m\\ y test.jpg", m.group(2));
		
		//test slashes of either direction
		m = FileReferenceChanger.getCssUrlPattern().matcher("url(\"..\\../images\\test.jpg\")");
		m.find();
		logger.fine(m.group(2));
		assertEquals("\"", m.group(1));
		assertEquals("..\\../images\\test.jpg", m.group(2));
		
		//test escaped parentheses, commas, whitespace characters, single quotes and double quotes
		m = FileReferenceChanger.getCssUrlPattern().matcher("url(\"../../s\\ p\\ a\\ c\\ e\\ s/\\(test\\)\\,\\'\\\".jpg\")");
		m.find();
		logger.fine(m.group(2));
		assertEquals("\"", m.group(1));
		assertEquals("../../s\\ p\\ a\\ c\\ e\\ s/\\(test\\)\\,\\'\\\".jpg", m.group(2));
		
		//test additional text after closing parenthesis
		m = FileReferenceChanger.getCssUrlPattern().matcher("url(../../images/test.jpg) repeat none;");
		m.find();
		logger.fine(m.group(2));
		assertEquals("", m.group(1));
		assertEquals("../../images/test.jpg", m.group(2));
		
		//test no space after closing parenthesis
		m = FileReferenceChanger.getCssUrlPattern().matcher("url(../../images/test.jpg);body{}");
		m.find();
		logger.fine(m.group(2));
		assertEquals("", m.group(1));
		assertEquals("../../images/test.jpg", m.group(2));
		
		// test two matches in the same string
		m = FileReferenceChanger.getCssUrlPattern().matcher(".sessionBox{background:black !important;box-shadow:0px 0px 20px 0px rgba(0,0,0,0.5) !important}.institutioncontext{border-radius:0px !important;background:#1a76a9 !important;border:0px !important}.institutioncontext .x-btn-inner{display:block;padding:0px 35px 0px 14px !important;color:white !important;font-family:\"Helvetica Neue\", Helvetica, Arial, sans-serif !important;font-size:16px !important}.institutioncontext .x-btn-icon{background:url(../images/InstitutionIcon.png) center no-repeat !important}.institutioncontext .x-btn-arrow-right{background:url(../images/institutioncontexttrigger.gif) right no-repeat !important;margin-right:5px !important}.sessionboxtext,.sessionboxtext a{color:white !important;font-family:\"Helvetica Neue\", Helvetica, Arial, sans-serif !important;font-size:16px !important;text-decoration:none !important}.institutioncontextmenu>.x-panel-body{border:0px !important;padding:0px !important;background:#1a76a9 !important}");
		m.find();
		logger.fine(m.group(2));
		assertEquals("", m.group(1));
		assertEquals("../images/InstitutionIcon.png", m.group(2));
		
		
		// test @import
		m = FileReferenceChanger.getCssUrlPattern().matcher("@import \"test2.css\"");
        m.find();
        logger.fine(m.group(2));
        assertEquals("\"", m.group(1));
        assertEquals("test2.css", m.group(2));
        
	}
	
	public void testEscapedFilenamePattern(){
		//test no quotes
		Matcher m = FileReferenceChanger.getFilenamePattern().matcher("..\\../images/../\\f\\ i\\ l\\ e\\ name.jpg");
		m.find();
		logger.fine(m.group(0));
		assertEquals("\\f\\ i\\ l\\ e\\ name.jpg", m.group(1));

	}
	
	
	public void testReplaceReference(){
		StringBuilder sb = new StringBuilder("abcdefghijklmnopqrstuvwxyz");
		FileReferenceChanger.replaceReference(sb, 4, 7, "xxxxx", 0);
		assertEquals("abcdxxxxxhijklmnopqrstuvwxyz", sb.toString());
	}
		
	public void testFileReferenceChanger() throws IOException {
		Project project = new Project();
		//PROBABLY FAILING BECAUSE YOU ARE ON A MAC AND JAVA THINKS I WAS CREATING AN ABSOLUTE REFERENCE. THIS SHOULD BE RELATIVE TO THE CURRENT DIRECTORY.
		File rootTestDir = new File("data\\test\\filereferencechanger");
		File oldWebappDir = new File(rootTestDir+"\\webapp");
		File copyWebappDir = new File(oldWebappDir.getName()+" - Copy");
		
		FileReferenceChanger rename = new FileReferenceChanger();
		rename.setProject(project);
		FileSet f = new FileSet();
		FileUtils.deleteDirectory(copyWebappDir);
		FileUtils.copyDirectory(oldWebappDir, copyWebappDir);
		f.setDir(copyWebappDir);
		
		f.setProject(project);
		f.createInclude().setName("**/*.js");
		f.createInclude().setName("**/*.css");
		f.createInclude().setName("**/*.html");
		f.createExclude().setName("extjs/**/*");
		rename.addFileset(f);
		
		rename.setReplacefilterfile(rootTestDir+"\\md5references.properties");
		rename.setWebAppRoot(copyWebappDir.getName());
		rename.execute();
		
		FileUtils.deleteDirectory(copyWebappDir);
	}


}
