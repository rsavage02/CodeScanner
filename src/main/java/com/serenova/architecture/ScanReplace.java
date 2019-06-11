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

import java.io.*;
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
    private Integer                     totalReplaceCount = 0;
    private Integer                     lineMatchCount = 0;
    private Integer                     fileCount = 0;
    private Integer                     fileMatchCount = 0;
    private Integer                     fileLineMatchCount = 0;
    private static Boolean              doReplace = false;
    private Map<String, String>         lineFindReplace = new HashMap<String, String>();
    ConcurrentHashMap<String,String>    distinctURLs = new ConcurrentHashMap<String,String>();
    ConcurrentHashMap<String,String>    searchReplaoe = new ConcurrentHashMap<String,String>();

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

        System.out.println("Directory,Filename,Line Number, Sub Line Number, Matching Line");
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

                Map<String, Object> confMap = (Map<String, Object>) confEntry;
                //System.out.println("conf contents: " + confMap);


                for (String keyName : confMap.keySet())
                    {
                    //System.out.println(keyName + " = " + confMap.get(keyName).toString());

                    switch (keyName)
                        {
                        case "lineInclude":

                            for ( String key :  ((Map<String, String>) confMap.get(keyName)).keySet())
                                {
                                lineFindReplace.put(key, ((Map<String, String>) confMap.get(keyName)).get(key).toString());
                                }

                            lineIncludePattern = ConvertToPattern(lineFindReplace.keySet().toString());

                            break;
                        case "lineExclude":
                            lineExcludePattern = ConvertToPattern(confMap.get(keyName).toString());
                            break;
                        case "fileExclude":
                            fileExcludePattern = ConvertToPattern(confMap.get(keyName).toString());
                            break;
                        case "fileInclude":
                            fileIncludePattern = ConvertToPattern(confMap.get(keyName).toString());
                            break;
                        case "dirExclude":
                            dirExcludePattern = ConvertToPattern(confMap.get(keyName).toString());
                            break;
                        case "dirInclude":
                            dirIncludePattern = ConvertToPattern(confMap.get(keyName).toString());
                            break;
                        case "urlPattern":
                            urlPattern = ConvertToPattern(confMap.get(keyName).toString());
                            break;
                        }
                    }
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
        System.out.println("*   Line replace pattern: " + lineFindReplace.toString());
        System.out.println("*   Line include pattern: " + lineIncludePattern.toString());
        System.out.println("*   Line exclude pattern: " + lineExcludePattern.toString());
        System.out.println("*         Lines replaced: " + totalReplaceCount);
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
        int    regexFlags = 0;

        finalResult = finalResult.replace(", ","|");

        finalResult = finalResult.substring(1,finalResult.length()-1);

        regexFlags = ConvertRegExOptions(finalResult);

        return(Pattern.compile(finalResult, regexFlags));
        }

    /****************************************************************************
     * Method: ConvertRegExOptions()
     * Author: Ron Savage
     *   Date: 05/13/2019
     *
     * Description: This method the regex options inside the (?) to the equivalent
     * Java flag values.
     *
     * #  Constant                    Equivalent Embedded Flag Expression
     * #  Pattern.CASE_INSENSITIVE    (?i)
     * #  Pattern.COMMENTS            (?x)
     * #  Pattern.MULTILINE           (?m)
     * #  Pattern.DOTALL              (?s)
     * #  Pattern.UNICODE_CASE        (?u)
     * #  Pattern.UNIX_LINES          (?d)
     * # Can also do multiples like {?im)
     ****************************************************************************/
    private int ConvertRegExOptions(String configEntry)
        {
        int regExFlags = 0;
        String optionsRegex = "^\\(?(.+)\\)";
        String optionGroup = "";
        Pattern optionsPattern = Pattern.compile(optionsRegex);

        for (String regex : configEntry.split("[|]"))
            {
            Matcher optionsMatcher = optionsPattern.matcher(regex);

            if (optionsMatcher.find())
                {
                optionGroup = optionsMatcher.group(1);

                if (optionGroup.contains("i")) regExFlags |= Pattern.CASE_INSENSITIVE;
                if (optionGroup.contains("x")) regExFlags |= Pattern.COMMENTS;
                if (optionGroup.contains("m")) regExFlags |= Pattern.MULTILINE;
                if (optionGroup.contains("s")) regExFlags |= Pattern.DOTALL;
                if (optionGroup.contains("u")) regExFlags |= Pattern.UNICODE_CASE;
                if (optionGroup.contains("d")) regExFlags |= Pattern.UNIX_LINES;
                }
            }

        return(regExFlags);
        }

    /****************************************************************************
     * Method: DoFindReplace()
     * Author: Ron Savage
     *   Date: 05/31/2019
     *
     * Description: This method searches for and replaces matches with the replacement
     * text specified in the config.
     ****************************************************************************/
    private String DoFindReplace(String line)
        {
        String newLine = line;

        for (String findKey : lineFindReplace.keySet())
            {
            String repValue =  lineFindReplace.get(findKey).toString();

            Pattern findPattern = Pattern.compile(findKey);
            Matcher repMatcher = findPattern.matcher(newLine);

            newLine = repMatcher.replaceAll(repValue);
            }
        return(newLine);
        }

    /****************************************************************************
         * Method: Scan()
         * Author: Ron Savage
         *   Date: 05/13/2019
         *
         * Description: This method reads each line in the file and calls the pattern
         * match method to check for a match of the search parameters.
         ****************************************************************************/
    private void Scan(String fileToScan, String outputFile)
        {

        try (BufferedReader reader = new BufferedReader(new FileReader(fileToScan)))
            {
            String line;
            String csvLine = "";
            Integer lineCount = 0;
            Integer subLineCount = 0;
            File outFile;
            BufferedWriter bufferedOutfileWriter = null;

            try
                {
                if (doReplace)
                    {
                    bufferedOutfileWriter = new BufferedWriter(new FileWriter(new File(outputFile)));
                    }

                while ((line = reader.readLine()) != null)
                    {
                    totalLineCount++;
                    lineCount++;

                    subLineCount = 0;
                    String[] subLines = line.split("(?<=;)");
                    for (String subLine : subLines)
                        {
                        subLineCount++;

                        if (HasMatch(subLine, lineIncludePattern, lineExcludePattern))
                            {
                            lineMatchCount++;

                            ExtractURLs(subLine);

                            if (doReplace && bufferedOutfileWriter != null)
                                {
                                subLine = DoFindReplace(subLine);
                                bufferedOutfileWriter.write(subLine);
                                }

                            csvLine = (Paths.get(fileToScan).getParent().toString() + "," + Paths.get(fileToScan).getFileName() + "," + lineCount + "," + subLineCount + ",\"" + subLine.replace("\"", "\"\"") + "\"");
                            System.out.println(csvLine);
                            } else
                            {
                            if (doReplace && bufferedOutfileWriter != null)
                                {
                                bufferedOutfileWriter.write(subLine);
                                }
                            }
                        }

                    if (doReplace && bufferedOutfileWriter != null)
                        {
                        bufferedOutfileWriter.newLine();
                        }
                    }

                if (doReplace && bufferedOutfileWriter != null)
                    {
                    bufferedOutfileWriter.close();
                    }

                } catch (Exception o)
                {
                System.err.format("Exception occurred trying to write '%s'.", fileToScan);
                o.printStackTrace();
                }
            } catch (Exception e)
            {
            System.err.format("Exception occurred trying to read '%s'.", fileToScan);
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

                        File refFile;
                        if (doReplace)
                            {
                            refFile = new File(thisPath.toString() + "_bak");
                            file.renameTo(refFile);

                            //System.out.println(indentBuffer + thisPath.toString());
                            Scan(refFile.toPath().toAbsolutePath().toString(),thisPath.toAbsolutePath().toString());
                            }
                        else
                            {
                            //System.out.println(indentBuffer + thisPath.toString());
                            Scan(file.toPath().toAbsolutePath().toString(),thisPath.toAbsolutePath().toString());
                            }

                        }
                    }
                }
            }
        catch (Exception e)
            {
            System.err.format("Exception occurred trying to read '%s'.", findPath);
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
            if (includeMatcher.find() && !excludeMatcher.find())
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

            if (args.length > 2 && !args[2].contains(">"))
                {
                if (args[2].equalsIgnoreCase("replace"))
                    {
                    doReplace = true;
                    }
                }
            }
        else
            {
            System.out.println("Syntax: java -jar CodeScanner.jar <config file> <start dir> [replace] [> <outputfile.csv>]");
            }

        scanRep = new ScanReplace(configFile,startDir);
        }
    }
