import javax.xml.parsers.*;
import org.w3c.dom.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;

public final class ToxicityTable {
    private static final NumberFormat NF = NumberFormat.getInstance();
    static {
        NF.setMaximumFractionDigits(0);
    }

    private static final List<Rule> RULE_PARSERS =        
        Arrays.asList
        (new Rule("File length",
                  "FileLengthCheck", 
                  "File length is ([^ ]+) lines"),
         new Rule("Method length",
                  "MethodLengthCheck", 
                  "Method length is ([^ ]+) lines"),
         new Rule("Argument amount",
                  "ParameterNumberCheck", 
                  "More than [^ ]+ parameters \\(found ([^\\)]+)\\)"),
         new Rule("Cyclomatic complexity",
                  "CyclomaticComplexityCheck", 
                  "Cyclomatic Complexity is ([^ ]+) \\("),
         // new Rule("Missing switch default",
         //          "MissingSwitchDefaultCheck", 
         //          null),
         new Rule("Anonymous method length",
                  "AnonInnerLengthCheck", 
                  "Anonymous inner class length is ([^ ]+) lines"),
         new Rule("Boolean complexity",
                  "BooleanExpressionComplexityCheck",
                  "Boolean expression complexity is ([^ ]+) "),
         new Rule("Class abstraction coupling",
                  "ClassDataAbstractionCouplingCheck",
                  "Class Data Abstraction Coupling is ([^ ]+) "),
         new Rule("Class fan-out complexity",
                  "ClassFanOutComplexityCheck",
                  "Class Fan-Out Complexity is ([^ ]+) "),
         new Rule("Nested if depth",
                  "NestedIfDepthCheck",
                  "Nested if-else depth is ([^ ]+) "),
         new Rule("Nested try depth",
                  "NestedTryDepthCheck",
                  "Nested try depth is ([^ ]+) ")
         );

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage: ToxicityTable "
                               + "[-f csv] [-p START-FILENAME-FROM] "
                               + "[-o OUT] FILE");
            return;
        }
        String outfile = null;
        String format = "html";
        String file = null;
        String startFilenameFrom = null;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("-p")) {
                i++;
                startFilenameFrom = args[i];
            } 
            else if (arg.equals("-f")) {
                i++;
                format = args[i];
            }
            else if (arg.equals("-o")) {
                i++;
                outfile = args[i];
            }
            else {
                file = arg;
            }
        }

        List<FileStats> filesStats = parse(new File(file), startFilenameFrom);

        PrintStream out = System.out;

        if (outfile != null) {
            out = new PrintStream(outfile);
        }

        if (format.equals("html")) {
            makeHtml(filesStats, out);
        } else if (format.equals("csv")) {
            makeCsv(filesStats, out);
        } else {
            System.err.println("Invalid -f: " + format);
            System.exit(1);
        }
    }

    public static List<FileStats> parse(File f, String startFilenameFrom) 
        throws Exception {
        Element xml = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder().parse(f).getDocumentElement();

        List<FileStats> filesStats = new ArrayList<>();

        for (Element file : getElements(xml, "file")) {
            String filename = fixFilename(file.getAttribute("name"),
                                          startFilenameFrom);
            if (filename == null) {
                continue;
            }
            FileStats filestats = new FileStats(filename);
            for (Element error : getElements(file, "error")) {
                String source = error.getAttribute("source");
                String message = error.getAttribute("message");

                for (Rule rule : RULE_PARSERS) {
                    if (rule.matchesSource(source)) {
                        int number = rule.getNumber(message);
                        filestats.addRuleNumber(rule, number);
                        break;
                    }
                }
            }
            filesStats.add(filestats);
        }
        return filesStats;
    }

    private static String fixFilename(String filename, String startFilenameFrom) {
        if (startFilenameFrom == null) {
            return filename;
        } else {
            if (!filename.contains(startFilenameFrom)) {
                return null;
            }
            return filename.substring(filename.indexOf(startFilenameFrom));
        }
    }

    private static final void makeCsv(List<FileStats> filesStats, 
                                       PrintStream out) {
        Collections.sort(filesStats);

        out.print("\"File\",\"Toxicity\"");
        for (Rule rule : RULE_PARSERS) {
            out.print(",\"" + rule.getName() + "\"");
        }
        out.println();
        for (FileStats fileStats : filesStats) {
            if (fileStats.getToxicity() <= 0.0) {
                break;
            }
            out.print("\"" + fileStats.getFilename() + "\"");
            out.print(",\"" + fileStats.getToxicity() + "\"");
            for (Rule rule : RULE_PARSERS) {
                if (fileStats.getToxicity(rule) == 0) {
                    out.print(",\"\"");
                } else {
                    out.print(",\""+fileStats.getToxicity(rule) + "\"");
                }
            }
            out.println();
        }
    }

    private static final void makeHtml(List<FileStats> filesStats, 
                                       PrintStream out) {
        out.println("<html>");
        out.println("<head><script src=sorttable.js></script></head>");
        out.println("<table class=sortable>");
        out.println("<thead>");
        out.println("<tr bgcolor=lightblue>");
        out.println("  <th nowrap align=right>File</th>");
        out.println("  <th nowrap>Toxicity</th>");

        for (Rule rule : RULE_PARSERS) {
            out.println("  <th nowrap>" + rule.getName() + "</th>");
        }
        out.println("</tr>");
        out.println("</thead>");
        out.println("<tbody>");

        Collections.sort(filesStats);

        for (FileStats fileStats : filesStats) {
            if (fileStats.getToxicity() <= 0.0) {
                break;
            }
            makeHtmlFileStats(fileStats, out);
        }

        out.println("</tbody></table></html>");
    }

    private static final void makeHtmlFileStats(FileStats fileStats,
                                                PrintStream out) {
        out.println("<tr>");
        out.println("  <td nowrap align=right>" 
                    + fileStats.getFilename() + "</td>");
        out.println("  <td nowrap>" + nf(fileStats.getToxicity()) + "</td>");
        for (Rule rule : RULE_PARSERS) {
            List<Integer> toxicities = fileStats.getToxicities(rule);
            Collections.sort(toxicities, Collections.reverseOrder());
            out.print("  <td nowrap>");
            boolean first = true;
            for (Integer tox : toxicities) {
                if (!first) {
                    out.print("; ");
                }
                out.print(nf(tox));
                first = false;
            }
            out.println("</td>");
        }
        out.println("</tr>");
    }

    private static String nf(int number) {
        return NF.format(number);
    }
    private static String nf(double number) {
        return NF.format(number);
    }

    private static List<Element> getElements(Element elm, String tag) 
        throws Exception {
        List<Element> elements = new ArrayList<>();
        NodeList nodeList = elm.getElementsByTagName(tag);
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                elements.add((Element) node);
            }
        }
        return elements;
    }

    private static class Rule {
        private final String name;
        private final String sourceEndsWith;
        private final Pattern regexp;

        public Rule(String name, 
                          String sourceEndsWith, String numberRegexp) {
            this.name = name;
            this.sourceEndsWith = sourceEndsWith;
            if (numberRegexp == null) {
                this.regexp = null;
            } else {
                this.regexp = Pattern.compile(numberRegexp);
            }
        }

        public boolean matchesSource(String source) {
            return source.endsWith(sourceEndsWith);
        }

        public String getName() {
            return name;
        }

        public int getNumber(String message) {
            if (regexp == null) {
                return 1;
            } else {
                Matcher match = regexp.matcher(message);
                match.find();
                String numberString = match.group(1);
                numberString = numberString.replaceAll("[^0-9]", "");
                return Integer.parseInt(numberString);
            }
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            return o.getClass() == getClass() 
                && toString().equals(((Rule)o).toString());
        }
    }

    private static class FileStats implements Comparable<FileStats> {
        private final String filename;
        private final Map<Rule, List<Integer>> ruleNumbers = new HashMap<>();

        public FileStats(String filename) {
            this.filename = filename;
        }

        public String getFilename() {
            return filename;
        }

        public void addRuleNumber(Rule rule, int number) {
            List<Integer> numbers = ruleNumbers.get(rule);
            if (numbers == null) {
                numbers = new ArrayList<>();
                ruleNumbers.put(rule, numbers);
            }
            numbers.add(number);
        }

        public List<Integer> getToxicities(Rule rule) {
            if (!ruleNumbers.containsKey(rule)) {
                return Collections.emptyList();
            }
            return new ArrayList<>(ruleNumbers.get(rule));
        }

        public int getToxicity(Rule rule) {
            if (!ruleNumbers.containsKey(rule)) {
                return 0;
            }
            int sum = 0;
            for (Integer number : ruleNumbers.get(rule)) {
                sum += number;
            }
            return sum;
        }

        public int getToxicity() {
            int sum = 0;
            for (Rule rule : RULE_PARSERS) {
                sum += getToxicity(rule);
            }
            return sum;
        }

        @Override
        public int compareTo(FileStats fileStats) {
            return Double.compare(fileStats.getToxicity(), getToxicity());
        }
    }
}
