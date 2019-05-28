/****************************************************************************
 *    Class: ScanReplace.java
 *   Author: Ron Savage
 *     Date: 05/13/2019
 *
 * Description: This class loops through all the lines in the file specified
 * to identify any line that meets one of the search criteria specified in
 * the yamlConfig file.
 *
 * Date       Init Note
 * 05/13/2019 RS   Created.
 ****************************************************************************/
package com.serenova.architecture;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yaml.snakeyaml.Yaml;

public class ScanReplace
    {
    private String                      confFileName;
    Path                                startPath;
    private String                      fileSpec;
    private String                      indentBuffer = "";
    private Iterable<Object>            yamlConfig;
    private Pattern                     lineExcludePattern;
    private Pattern                     lineIncludePattern;
    private Pattern                     fileExcludePattern;
    private Pattern                     fileIncludePattern;
    private Pattern                     dirExcludePattern;
    private Pattern                     dirIncludePattern;
    private Pattern                     urlPattern;
    private Integer                     folderLevel = 0;
    private Integer                     dirCount = 0;
    private Integer                     dirMatchCount = 0;
    private Integer                     dirLineMatchCount = 0;
    private Integer                     totalLineCount = 0;
    private Integer                     lineMatchCount = 0;
    private Integer                     fileCount = 0;
    private Integer                     fileMatchCount = 0;
    private Integer                     fileLineMatchCount = 0;
    private static Boolean              doReplace = false;

    ConcurrentHashMap<String,String>    distinctURLs = new ConcurrentHashMap<String,String>();

    /****************************************************************************
     * Method: ScanReplace()
     * Author: Ron Savage
     *   Date: 05/13/2019
     *
     * Description: This method is the constructor.
     ****************************************************************************/
    public ScanReplace(String file, String startDir)
        {
        confFileName = file;
        startPath    = Paths.get(startDir);
        fileSpec     = "*.*";

        ReadConfig();

        System.out.println("Directory,Filename,Matching Line");
        FindFiles(startPath);

        SummaryReport();
        }

    /****************************************************************************
     * Method: ReadConfig()
     * Author: Ron Savage
     *   Date: 05/14/2019
     *
     * Description: This method reads the yaml configuration file.
     ****************************************************************************/
    private void ReadConfig()
        {
            try
            {
            Yaml yaml = new Yaml();

            BufferedReader reader = new BufferedReader(new FileReader(confFileName));

            yamlConfig = yaml.loadAll(reader);

            for (Object confEntry : yamlConfig)
                {
                //System.out.println("Configuration object type: " + confEntry.getClass());

                Map<String, List<String>> confMap = (Map<String, List<String>>) confEntry;
                //System.out.println("conf contents: " + confMap);

/*
                for (String keyName : confMap.keySet())
                    {
                    System.out.println(keyName + " = " + confMap.get(keyName).toString());
                    }
*/

                lineExcludePattern  = ConvertToPattern(confMap.get("lineExclude").toString());
                lineIncludePattern  = ConvertToPattern(confMap.get("lineInclude").toString());
                fileExcludePattern  = ConvertToPattern(confMap.get("fileExclude").toString());
                fileIncludePattern  = ConvertToPattern(confMap.get("fileInclude").toString());
                dirExcludePattern   = ConvertToPattern(confMap.get("dirExclude").toString());
                dirIncludePattern   = ConvertToPattern(confMap.get("dirInclude").toString());
                urlPattern          = ConvertToPattern(confMap.get("urlPattern").toString());
                }

            } catch (Exception e)
            {
                System.err.format("Exception occurred trying to read '%s'.", confFileName);
                e.printStackTrace();
            }
        }

    /****************************************************************************
     * Method: SummaryReport()
     * Author: Ron Savage
     *   Date: 05/13/2019
     *
     * Description: This method prints out a summary of file/line counts processed.
     ****************************************************************************/
    private void SummaryReport()
        {
        System.out.println("\n\n***********************************************************************");
        System.out.println("*                    CodeScanner Summary Report");
        System.out.println("*");
        System.out.println("*  Run Date: " + new SimpleDateFormat("yyyy.MM.dd  HH:mm:ss").format(new Date()));
        System.out.println("* Yaml File: " + confFileName);
        System.out.println("* Code Path: " + startPath);
        System.out.println("*");
        System.out.println("*    Dir include pattern: " + dirIncludePattern.toString());
        System.out.println("*    Dir exclude pattern: " + dirExcludePattern.toString());
        System.out.println("*    Directories scanned: " + dirCount);
        System.out.println("*    Directories matched: " + dirMatchCount);
        System.out.println("*       Dir Line matched: " + dirLineMatchCount);
        System.out.println("*");
        System.out.println("*   File include pattern: " + fileIncludePattern.toString());
        System.out.println("*   File exclude pattern: " + fileExcludePattern.toString());
        System.out.println("*          Files scanned: " + fileCount);
        System.out.println("*          Files matched: " + fileMatchCount);
        System.out.println("*     Files Line matched: " + fileLineMatchCount);
        System.out.println("*");
        System.out.println("*   Line include pattern: " + lineIncludePattern.toString());
        System.out.println("*   Line exclude pattern: " + lineExcludePattern.toString());
        System.out.println("*          Lines scanned: " + totalLineCount);
        System.out.println("*          Lines matched: " + lineMatchCount);
        System.out.println("*");
        System.out.println("* Distinct Matching URLs Found");

        for (String url : distinctURLs.values())
            {
            System.out.println("*   " + url);
            }

        System.out.println("*");
        System.out.println("***********************************************************************");
        }

    /****************************************************************************
     * Method: ConvertToPattern()
     * Author: Ron Savage
     *   Date: 05/13/2019
     *
     * Description: This method the configuration strings into patterns.
     ****************************************************************************/
    private Pattern ConvertToPattern(String configEntry)
        {
        String finalResult = configEntry;

        finalResult = finalResult.replace(", ","|");

        finalResult = finalResult.substring(1,finalResult.length()-1);

        return(Pattern.compile(finalResult,Pattern.CASE_INSENSITIVE));
        }

    /****************************************************************************
         * Method: Scan()
         * Author: Ron Savage
         *   Date: 05/13/2019
         *
         * Description: This method reads each line in the file and calls the pattern
         * match method to check for a match of the search parameters.
         ****************************************************************************/
    private void Scan(String fileToScan)
        {

        try (BufferedReader reader = new BufferedReader(new FileReader(fileToScan)))
            {
            String line;
            Integer lineCount = 0;

            while ((line = reader.readLine()) != null)
                {
                totalLineCount++;
                lineCount++;

                if (HasMatch(line, lineIncludePattern, lineExcludePattern))
                    {
                    lineMatchCount++;

                    ExtractURLs(line);

                    while (line.length() > 32000)
                        {
                        System.out.println(Paths.get(fileToScan).getParent().toString() + "," + Paths.get(fileToScan).getFileName() + ",\"" + lineCount + ": " + line.substring(0,32000).replace("\"", "\"\"") + "\"");

                        line = line.substring(32001,line.length());
                        }

                    System.out.println(Paths.get(fileToScan).getParent().toString() + "," + Paths.get(fileToScan).getFileName() + ",\"" + lineCount + ": " + line.replace("\"", "\"\"") + "\"");
                    }
                }

            //System.out.println("");
            } catch (Exception e)
            {
            System.err.format("Exception occurred trying to read '%s'.", confFileName);
            e.printStackTrace();
            }

        }

    /****************************************************************************
     * Method: ExtractURLs()
     * Author: Ron Savage
     *   Date: 05/24/2019
     *
     * Description: This method extracts any matching URLs and stores them in a
     * distinct hash map.
     ****************************************************************************/
    private void ExtractURLs(String line)
        {
        String matchedURL = "";
        Matcher urlMatcher = urlPattern.matcher(line);

        while (urlMatcher.find())
            {
            matchedURL = urlMatcher.group();

            if (!distinctURLs.containsKey(matchedURL))
                {
                distinctURLs.put(matchedURL,matchedURL);
                }
            }
        }

    /****************************************************************************
     * Method: FindFiles.java
     * Author: Ron Savage
     *   Date: 05/13/2019
     *
     * Description: This method reads each line in the file and calls the pattern
     * match method to check for a match of the search parameters.
     ****************************************************************************/
    private void FindFiles(Path findPath)
        {

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(findPath))
            {

            for (Path thisPath : stream)
                {
                File file = new File(thisPath.toString());

                if (file.isDirectory())
                    {
                    dirCount++;

                    if (HasMatch(thisPath.toString(), lineIncludePattern, lineExcludePattern))
                        {
                        dirLineMatchCount++;

                        System.out.println(thisPath.toString() + ",directory,none");
                        }

                    if (HasMatch(thisPath.toString(), dirIncludePattern, dirExcludePattern))
                        {
                        dirMatchCount++;
                        folderLevel++;
                        //indentBuffer = String.join("", Collections.nCopies(folderLevel * 3, " "));
                        indentBuffer = "";

                        //System.out.println(indentBuffer + thisPath.toString() + " ...");
                        FindFiles(thisPath);

                        folderLevel--;
                        //indentBuffer = String.join("", Collections.nCopies(folderLevel * 3, " "));
                        }
                    }
                else
                    {
                    fileCount++;

                    if (HasMatch(thisPath.getParent().toString(), lineIncludePattern, lineExcludePattern))
                        {
                        fileLineMatchCount++;

                        System.out.println(thisPath.getParent().toString() + "," + thisPath.getFileName() + ",none");
                        }

                    if (HasMatch(thisPath.toString(), fileIncludePattern, fileExcludePattern))
                        {
                        fileMatchCount++;

                        //System.out.println(indentBuffer + thisPath.toString());
                        Scan(thisPath.toAbsolutePath().toString());
                        }
                    }
                }
            }
        catch (Exception e)
            {
            System.err.format("Exception occurred trying to read '%s'.", confFileName);
            e.printStackTrace();
            }

        }

    /****************************************************************************
     * Method: HasMatch.java
     * Author: Ron Savage
     *   Date: 05/14/2019
     *
     * Description: This method checks the text for any matches from
     * the includePattern with no matches in the excludePattern patterns sent.
     ****************************************************************************/
    private Boolean HasMatch(String line, Pattern includePattern, Pattern excludePattern)
        {
        Boolean foundMatch = false;

        Matcher includeMatcher = includePattern.matcher(line);
        Matcher excludeMatcher = excludePattern.matcher(line);

        try
            {
            if (includeMatcher.matches() && !excludeMatcher.matches())
                {
                foundMatch = true;
                }


            } catch (Exception e)
            {
            System.err.format("Exception occurred trying to find matches in: '%s'.", line);
            e.printStackTrace();
            }

        return (foundMatch);
        }

    /****************************************************************************
     *   Method: Main()
     *   Author: Ron Savage
     *     Date: 05/13/2019
     *
     * Description: This is the main entry point for the application.
     ****************************************************************************/
    public static void main(String[] args)
        {
        ScanReplace scanRep;
        String configFile   = "";
        String startDir     = "";

        if (args.length > 1)
            {
            configFile   = args[0];
            startDir     = args[1];

            if (args.length > 2)
                {
                if (args[2].equalsIgnoreCase("replace"))
                    {
                    doReplace = true;
                    }
                }
            }
        else
            {
            System.out.println("Syntax: java -jar CodeScanner.jar <config file> <start dir> [replace]");
            }

        scanRep = new ScanReplace(configFile,startDir);
        }
    }
