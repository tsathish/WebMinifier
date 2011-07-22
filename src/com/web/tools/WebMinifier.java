package com.web.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;

import com.yahoo.platform.yui.compressor.CssCompressor;
import com.yahoo.platform.yui.compressor.JavaScriptCompressor;

public class WebMinifier {

	static Reader in = null;
	static Writer out = null;
	static String charset = "UTF-8";
	
	// options
	static boolean munge = true;
	static boolean preserveAllSemiColons = false;
	static boolean disableOptimizations = false;
	static int linebreakpos = -1;
	static boolean verbose = false;
	static boolean backupSource = false;
	
	public static void main(String[] args) {
		if(args.length < 1) {
			System.out.println("Usage: java com.web.tools.WebMinifier <sourceDir> [<targetDir>]" +
					"sourceDir - Path of the source directory which has to be recursively minified." +
					"This entry can be a .htm / .css file." +
					"targetDir - Path of the target directory for copying the minified content." +
					"This entry can be a .htm / .css file." +
					"If not provided, the source directory is overwritten with the minified content." +
					"However, the source directory is copied as sourceDirectory_ori directory," +
					"before the minified content is written. \nEnjoy !!.");
			System.exit(1);
		}
		
		// set the source and destination directories
		String srcDirPath = args[0];
		String destDirPath = (args.length == 2) ? args[1] : args[0];

		// Parent directory for the files to compress or the file to compress
		File parentDir = new File(srcDirPath);
		if(! parentDir.exists()) {
			System.out.println("Path/File " + srcDirPath + " does not exist.");
			System.exit(2);
		}

		if(args.length == 1) {
			backupSource = true;
			try {
				if(parentDir.isFile()) {
					WebMinifier.copyFile(srcDirPath, srcDirPath + "_ori");
				} else {
					WebMinifier.copyDirectory(srcDirPath, srcDirPath + "_ori");
				}
			} catch (Exception e) {
				System.out.println("Exception when backing up the source.");
				e.printStackTrace();
			}
		}

		// call compress
		if(parentDir.isFile()) {
			String inputFilename = parentDir.getName();
			srcDirPath = parentDir.getParent();
			compressFile(inputFilename, srcDirPath, srcDirPath);
		} else {
			WebMinifier.compressDirectory(srcDirPath, destDirPath);
		}
	}
	
	public static void compressDirectory(String srcDirPath, String destDirPath) {
		File parentDir = new File(srcDirPath);
		List<String> files = Arrays.asList(parentDir.list());
		
		Iterator<String> fileNames = files.iterator();
		while(fileNames.hasNext()) {
			String inputFilename = (String) fileNames.next();
			compressFile(inputFilename, srcDirPath, destDirPath);
		}
	}
	
	private static void compressFile(
			String inputFilename, String srcDirPath, String destDirPath) {
		String type = null;
        int idx = inputFilename.lastIndexOf('.');
        if (idx >= 0 && idx < inputFilename.length() - 1) {
            type = inputFilename.substring(idx + 1);
        }
        if(type == null) {
        	// Could be a dir or a file
        	// If directory recurse;
        	File dir = new File(srcDirPath + "/" + inputFilename);
        	if(dir.isDirectory()) {
        		compressDirectory(srcDirPath + "/" + inputFilename, 
        				destDirPath + "/" + inputFilename);
        	} else {
        		// some file. just copy.
        		type = "";
        	}
        	return;
        }
        try {
        	boolean isJS = type.equalsIgnoreCase("js");
        	boolean isCSS = type.equalsIgnoreCase("css");

            File destDir = new File(destDirPath); 
            if(! destDir.exists()) {
            	destDir.mkdirs();
            }

            if (isJS || isCSS) {
            	in = new InputStreamReader(
            			new FileInputStream(srcDirPath + "/" + inputFilename), charset);
            	ErrorReporter er = new ErrorReporter() {
            		
                    public void warning(String message, String sourceName,
                            int line, String lineSource, int lineOffset) {
                        if (line < 0) {
                            System.err.println("\n[WARNING] " + message);
                        } else {
                            System.err.println(
                            		"\n[WARNING] " + line + ':' + lineOffset + ':' + message);
                        }
                    }

                    public void error(String message, String sourceName,
                            int line, String lineSource, int lineOffset) {
                        if (line < 0) {
                            System.err.println("\n[ERROR] " + message);
                        } else {
                            System.err.println(
                            		"\n[ERROR] " + line + ':' + lineOffset + ':' + message);
                        }
                    }

                    public EvaluatorException runtimeError(String message, String sourceName,
                            int line, String lineSource, int lineOffset) {
                        error(message, sourceName, line, lineSource, lineOffset);
                        return new EvaluatorException(message);
                    }
                };
            	
            	if(isJS) {
            		JavaScriptCompressor compressor = new JavaScriptCompressor(in, er);

            		// Close the input stream first, and then open the output stream,
                    // in case the output file should override the input file.
                    in.close(); in = null;

                    out = new OutputStreamWriter(
                    		new FileOutputStream(destDirPath + "/" + inputFilename), charset);
                    
                    compressor.compress(out, linebreakpos, munge, verbose,
                            preserveAllSemiColons, disableOptimizations);
	            	out.flush();
	            	out.close();
            	} else {
            		CssCompressor compressor = new CssCompressor(in);
            		
            		// Close the input stream first, and then open the output stream,
                    // in case the output file should override the input file.
                    in.close(); in = null;

                    out = new OutputStreamWriter(
                    		new FileOutputStream(destDirPath + "/" + inputFilename), charset);
                    
                    compressor.compress(out, linebreakpos);
	            	out.flush();
	            	out.close();
            	}
            } else {
            	if(! backupSource) {
            		copyFile( srcDirPath + "/" + inputFilename, destDirPath + "/" + inputFilename);
            	}
            }
        } catch (Exception e) {
        	e.printStackTrace();
		}
	}
	
	/**
	 * Creates a copy of the source directory as destination directory
	 * 
	 * @param sourceDir	- the name of the directory to copy
	 * @param destDir	- the name of the destination directory
	 * @throws Exception
	 */
	public static void copyDirectory(String sourceDir, String destDir) throws Exception {
		File[] files = new File(sourceDir).listFiles();
	    for(File file : files) {
	    	// create target dir, if not exists.
	    	File d = new File(destDir);
	    	if(! d.exists()) {
	    		d.mkdirs();
	    	}
	    	
		    if(file.isDirectory()) {		    	
		    	copyDirectory(file.getPath(), destDir + "/" + file.getName());
		      
		    } else {
		      copyFile(file.getPath(), destDir + "/" + file.getName());
		    }
	    }
	}
	  
	/**
	 * Creates a copy of the source file as destination file
	 * 
	 * @param sourceFile	- the name of the source file
	 * @param destFile		- the name of the destination file
	 * @throws Exception
	 */
	public static void copyFile(String sourceFile, String destFile) throws Exception {
	  FileChannel sourceChannel = new FileInputStream( sourceFile ).getChannel();
	  FileChannel targetChannel = new FileOutputStream( destFile ).getChannel();
	  sourceChannel.transferTo(0, sourceChannel.size(), targetChannel);
	  sourceChannel.close();
	  targetChannel.close();
	}
}