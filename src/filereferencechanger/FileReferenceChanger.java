package filereferencechanger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.Set;
import java.util.Vector;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;
import org.mozilla.javascript.CompilerEnvirons;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.Token;
import org.mozilla.javascript.ast.AstNode;
import org.mozilla.javascript.ast.AstRoot;
import org.mozilla.javascript.ast.NodeVisitor;
import org.mozilla.javascript.ast.StringLiteral;

/**
 * @author wlemmon
 * This Ant task traverses a fileset and edits the files in the set according to a properties file.
 */
public class FileReferenceChanger extends Task {

	// STATIC VARIABLES 
	
	private static enum ParsibleExtension { css, js, html }

	private static Logger logger = Logger.getLogger(FileReferenceChanger.class.getName());;

	/*
	 * A Uniform Resource Locator (URL) is identified with a functional
	 * notation: BODY { background: url(http://www.bg.com/pinkish.gif) }
	 * 
	 * The format of a URL value is �url(� followed by optional white space
	 * followed by an optional single quote (�) or double quote (�) character
	 * followed by the URL itself followed by an optional single quote (�) or
	 * double quote (�) character followed by optional whitespace followed by
	 * �)�. Quote characters that are not part of the URL itself must be
	 * balanced.
	 * 
	 * Parentheses, commas, whitespace characters, single quotes (�) and double
	 * quotes (�) appearing in a URL must be escaped with a backslash: �\(�,
	 * �\)�, �\,�.
	 * 
	 * Partial URLs are interpreted relative to the source of the style sheet,
	 * not relative to the document.
	 */
	private static Pattern cssUrlPattern = Pattern.compile(
	      "(?:                     # noncapturing group\n"
		+ "url\\(                  # Match url(\n"
		+ "\\s*                    # followed by optional whitespace.\n"
		+ "(['\"]{0,1})            # CAPTURE_GROUP_1 the quote\n"
		+ "(                       # CAPTURE_GROUP_2 the url. Is the url unquoted?\n"
		+ "   (?<=[^'\"])          #    If the url is unquoted,\n"
		+ "   (                    #    end at un unescaped special character:\n"
		+ "      (?<=\\\\).        #       match any escaped character not a special character\n"
		+ "      |                 #       or\n"
		+ "      [^\\(\\),\\s'\"]  #       any non-escaped, non-special character\n"
		+ "   )                    #    \n"
		+ "   *                    #    any number of times\n"
		+ "   |                    #    or \n"
		+ "   (?<=['\"])           #    If the url IS quoted, \n"
		+ "   (?:                  #    don't match an unescaped quote terminator:\n"
		+ "      \\\\.             #       match an escaped character\n"
		+ "      |                 #       or\n"
		+ "      (?!\\1).          #       anything that is not the above quote.\n"
		+ "   )                    #    \n"
		+ "   *                    #    any number of times\n"
		+ ")                       # \n"
		+ "\\s*                    # optional whitespace \n"
		+ "\\1                     # Match the corresponding quote\n"
		+ "\\)                     # match the closing )\n"
		+ " |                      # or \n"
		+ "@import                 # import\n"
		+ "\\s*                    # followed by optional whitespace.\n"
        + "(['\"]{0,1})            # CAPTURE_GROUP_1 the quote\n"
        + "(                       # CAPTURE_GROUP_2 the url. Is the url unquoted?\n"
        + "   (?<=[^'\"])          #    If the url is unquoted,\n"
        + "   (                    #    end at un unescaped special character:\n"
        + "      (?<=\\\\).        #       match any escaped character not a special character\n"
        + "      |                 #       or\n"
        + "      [^\\(\\),\\s'\"]  #       any non-escaped, non-special character\n"
        + "   )                    #    \n"
        + "   *                    #    any number of times\n"
        + "   |                    #    or \n"
        + "   (?<=['\"])           #    If the url IS quoted, \n"
        + "   (?:                  #    don't match an unescaped quote terminator:\n"
        + "      \\\\.             #       match an escaped character\n"
        + "      |                 #       or\n"
        + "      (?!\\1).          #       anything that is not the above quote.\n"
        + "   )                    #    \n"
        + "   *                    #    any number of times\n"
        + ")                       # \n"
        + ")                       # \n",
		Pattern.COMMENTS	| Pattern.MULTILINE | Pattern.DOTALL);
	
	/*
	 * Used to find strings in html
	 */
	private static Pattern htmlUrlPattern = Pattern.compile(
			  "<                    # opening tag\n"
			+ "[^>]*                # any text \n"
			+ "(?:src|href)         # src or href attribute \n"
			+ "\\s*=\\s*            # space and equals \n"
			+ "(['\"])                # single or double quote \n"
			+ "(                    # CAPTURE_GROUP_1 \n"
			+ "   (?:               #    don't match an unescaped quote terminator:\n"
			+ "      \\\\.          #       match an escaped character\n"
			+ "      |              #       or\n"
			+ "      (?!\\1).       #       anything that is not the above quote.\n"
			+ "   )                 #    \n"
			+ "   *                 #    any number of times\n"
			+ ")                    # \n"
			+ "\\1                  # Match the corresponding quote\n"
			+ "[^>]*>               # text and tag closing \n", Pattern.COMMENTS
				| Pattern.MULTILINE | Pattern.DOTALL);
	
	/*
	 * used to match the name portion of a text reference found in a css file, including any 
	 * escaped characters: this is important when handling both quoted and unquoted strings.
	 */
	private static Pattern escapedFilenamePattern = Pattern.compile(
			"(?:            # match  \n" +
			"   [^\\/]*     # anything not a directory separator  \n" +
			"   [\\/]       # followed by a directory separator  \n" +
			")*             # a number of times  \n" +
			"(              # CAPTURE_GROUP_1  \n" +
			"   (?:         # match an escaped character or a regular character  \n" +
			"      \\\\.    #  \n" +
			"      |        #  \n" +
			"      .        #  \n" +
			"   )*          # many times \n" +
			")", 
			Pattern.COMMENTS | Pattern.MULTILINE | Pattern.DOTALL);
	
	//ANT INPUTS
	
	// a list of list of files to edit
	private Vector<FileSet> filesets = new Vector<FileSet>();

	// the directory for which relative references in your javascript will be rooted to
	private String webAppRoot;

	// a properties file mapping relative or absolute files to their hashed counterparts
	private String replacementMapFile;

	// DERIVED FROM ANT INPUTS
	
	// the same thing as replacementMapFile, in file form
	private Map<File, File> filenameReferencesToChange = new HashMap<File, File>();
	
	// a list of extensions that appear in replacementMapFile. These are used to js strings which 
	// are file references.
	private Set<String> validExtensions = new HashSet<String>();

	// the same thing as webAppRoot, in file form
	private File webAppRootFile;

	private StringBuilder sb;
	
	private boolean changed = false;
	
	private int shift = 0;
	
	// GETTERS and SETTERS
	
	public static Pattern getCssUrlPattern() {
		return cssUrlPattern;
	}
	
	public static Pattern getHtmlUrlPattern() {
		return htmlUrlPattern;
	}
	
	public static Pattern getFilenamePattern() {
		return escapedFilenamePattern;
	}
	
	public void setReplacefilterfile(String r) {
		replacementMapFile = r;
	}

	public void setWebAppRoot(String w) {
		webAppRoot = w;
	}

	/**
	 * @author wlemmon
	 * A Mozilla Rhino NodeVisitor (see https://developer.mozilla.org/en-US/docs/Rhino). Given 
	 * an Abstract Syntax Tree (AST) representation of a javascript file, this visitor will replace
	 * string references appropriately.
	 */
	protected static class JsStringVisitor implements NodeVisitor {
		//a flag to mark whether the tree has been changed.
		private FileReferenceChanger frc;

		public JsStringVisitor(FileReferenceChanger frc, File file, StringBuilder sb) {
			this.frc = frc;
		}

		public boolean visit(AstNode node) {
			int tt = node.getType();
			if (tt == Token.STRING) {
				StringLiteral stringLiteral = ((StringLiteral) node);
				String textReference = stringLiteral.getValue();
				int begin = node.getAbsolutePosition() + 1;
				int end = node.getAbsolutePosition() + node.getLength() - 1;
				findAndProcessMatch(frc, begin, end, frc.webAppRootFile, textReference);
			}
			return true; // process children
		}
	}
	
	// MAIN METHOD EXECUTED FROM ANT
	public void execute() {
		
		logger.setLevel(Level.INFO);
		Handler consoleHandler = new ConsoleHandler();
		consoleHandler.setLevel(Level.INFO);
		logger.addHandler(consoleHandler);
		consoleHandler.setFormatter(new Formatter() {
			@Override
			public String format(LogRecord record) {
				return record.getMessage() + "\n";
			}
		});

		try {
			setupFilesToRename();
			iterateFiles();
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println(e.getStackTrace());
		}
	}
	
	
	/**
	 * Creates time-saving variables for later use.
	 * @throws Exception
	 */
	private void setupFilesToRename() throws Exception {
		Properties p = new Properties();
		p.load(new FileInputStream(new File(replacementMapFile)));
		
		webAppRootFile = new File(webAppRoot);

		if (!webAppRootFile.isAbsolute()) {
			webAppRootFile = new File(System.getProperty("user.dir"), webAppRoot);
		}
		for (java.util.Map.Entry<Object, Object> entry : p.entrySet()) {
			String key = (String) entry.getKey();
			String value = (String) entry.getValue();

			validExtensions.add(getExtensionString(key));

			File file = new File(key);
			// if the file is not an absolute file, it is a test file: use the current directory
			if (!file.isAbsolute()) {
				file = new File(System.getProperty("user.dir"), key);
			}
			
			filenameReferencesToChange.put(file, new File(file.getParentFile(), value));
		}
	}
	
	/**
	 * Steps through all files in all filesets and edits the files.
	 * @throws Exception
	 */
	private void iterateFiles() throws Exception {
		for (FileSet fileset : filesets) {
			DirectoryScanner ds = fileset.getDirectoryScanner();
			File dir = ds.getBasedir();
			String[] filesInSet = ds.getIncludedFiles();
			for (String filename : filesInSet) {
				ParsibleExtension extension = getExtension(filename);
				if(extension == null){
					logger.warning("file type of "+filename+" is not supported. skipping...");
					continue;
				}
				logger.fine(filename);
				File file = new File(dir, filename);
				iterateLines(file);
			}
		}
	}

	/**
	 * Reads a whole file into a StringBuilder, edits it, then writes it back out. 
	 * @param fileInRead
	 * @throws Exception
	 */
	private void iterateLines(File fileInRead) throws Exception {

		// read in the whole file
		Scanner scan = new Scanner(fileInRead);
		scan.useDelimiter("\\Z");
		if (scan.hasNext()) {
			sb = new StringBuilder(scan.next());
			findReferenceInLine(fileInRead, sb);
			if (changed) {
				FileWriter f = new FileWriter(fileInRead);
				f.write(sb.toString());
				f.close();
			}
		}
		scan.close();
	}
	
	/**
	 * @param file - The current file being edited
	 * @param sb - a StringBuilder containing the fill file contents.
	 * @throws Exception
	 */
	private void findReferenceInLine(File file, StringBuilder sb) throws Exception {
		ParsibleExtension extension = getExtension(file.getName());
		shift = 0;
		switch (extension) {
		case css:
			handleRegexReplacement(cssUrlPattern, sb, file);
			break;
		case js:
			CompilerEnvirons ce = new CompilerEnvirons();
			ce.setRecordingComments(true);
			Parser p = new org.mozilla.javascript.Parser(ce);
			AstRoot ast = p.parse(sb.toString(), null, 1);
			JsStringVisitor jsv = new JsStringVisitor(this, file, sb);
			ast.visit(jsv);
			break;
		case html:
			handleRegexReplacement(htmlUrlPattern, sb, file);
			break;
		default:
			throw new Exception("The file type " + extension + "is not supported for searching.");
		}
	}
	
	/**
	 * This does the work for CSS and HTML files.
	 * @param pattern
	 * @param sb
	 * @param file
	 * @return
	 */
	private void handleRegexReplacement(Pattern pattern, StringBuilder sb, File file){
		// an adjustment to make every time we alter the base string and do not
		// recreate the matcher
		Matcher matcher = pattern.matcher(sb.toString());
		while (matcher.find()) {
			// the string found in the text
			String textReference = matcher.group(2);
			logger.finer("\t textReference:" + textReference);
			int begin = matcher.start(2);
			int end = matcher.end(2);
			findAndProcessMatch(this, begin, end, file.getParentFile(), textReference);
		}
	}

	/**
	 * @param frc - state
	 * @param parent - the directory we are currently in
	 * @param textReference - text from a file which might represent a file reference.
	 * @return
	 */
	public static void findAndProcessMatch(FileReferenceChanger frc, int begin, int end, File parent, String textReference) {
		
		String fileReadyReference = removeEscapeCharacters(textReference);
		String referenceExtension = getExtensionString(fileReadyReference);
		if (!frc.validExtensions.contains(referenceExtension)) {
			return;
		}
		// a file created based on the text reference
		File possibleReference = new File(parent, fileReadyReference);

		logger.fine("\t" + possibleReference.toString());
		File match = frc.findMatchingCanonicalPath(possibleReference);
		if (match != null) {
			String oldValue = frc.sb.substring(begin + frc.shift, end + frc.shift);
			String newValue = getNewFilename(parent, textReference, match.getName());

			// make change
			replaceReference(frc.sb, begin, end, newValue, frc.shift);
			// update flag
			frc.changed |= (!oldValue.equals(newValue));
			frc.shift += (newValue.length() - oldValue.length());
		}
		return;
	}

	/**
	 * Retrieves a files extension from a string
	 * @param file - the name of a file
	 * @return The text after the last occurrence of '.' or null if '.' does not occur.
	 */
	private static String getExtensionString(String file) {
		int i = file.lastIndexOf('.');
		if (i > 0) {
			String extension = file.substring(i + 1);
			extension = extension.toLowerCase();
			return extension;
		}
		return null;
	}
	
	/**
	 * Retrieves a files extension from a file
	 * @param file - the file to access
	 * @return - the extension
	 */
	private ParsibleExtension getExtension(String fileName) {
		String extension = getExtensionString(fileName);
		if(extension == null){
			return null;
		}
		try {
			return ParsibleExtension.valueOf(extension);
		} catch (Exception e) {
			return null;
		}
	}
	
	

	/**
	 * @param parent - a directory to put the new file in
	 * @param textReference - the string which represents a file
	 * @param newName - new name for the file
	 * @return - the original path as designated in the text reference appended with the new name.
	 */
	public static String getNewFilename(File parent, String textReference, String newName) {
		// the name at the end of the text url
		Matcher matcher = escapedFilenamePattern.matcher(textReference);
		matcher.find();
		String textNameOfFile = matcher.group(1); 

		// the relative path in the text url
		String relativePath = getPathOfTextReference(textReference, textNameOfFile);

		// the new url
		return relativePath + newName;
	}

	/**
	 * Given a full path string of a file, returns the path portion only.
	 * @param path - a full file reference, relative or absolute
	 * @param name - just the name of the file
	 * @return just the path of the file
	 */
	private static String getPathOfTextReference(String path, String name) {
		int indexOf = path.indexOf(name);
		return path.substring(0, indexOf);
	}

	/**
	 * This method is supposed to be used for parsing CSS-unquoted strings.
	 * @param input - a string with escaped characters in it
	 * @return The string with escape characters removed
	 */
	private static String removeEscapeCharacters(String input) {
		return input.replaceAll("\\\\(?=.)", "");
	}

	/**
	 * @param sb - a Representation of an entire file
	 * @param begin - the first character to remove
	 * @param end - the last character to remove
	 * @param newValue - the new string to insert
	 * @param shift - an optional adjustment to the start and end points.
	 */
	public static void replaceReference(StringBuilder sb, int begin, int end, String newValue,
			int shift) {
		sb.replace(begin + shift, end + shift, newValue);
	}

	public void addFileset(FileSet fileset) {
		filesets.add(fileset);
	}

	/**
	 * Finds a matching file from the properties file. Matches on Canonical Path.
	 * @param fileToMatch - a file to match
	 * @return The file found, or null if nothing matches.
	 */
	public File findMatchingCanonicalPath(File fileToMatch) {
		try {
			for (File key : filenameReferencesToChange.keySet()) {
				if (key.getCanonicalPath().equals(fileToMatch.getCanonicalPath())) {
					return filenameReferencesToChange.get(key);
				}
			}
		} catch (IOException e) {
			return null;
		}
		return null;
	}

}
