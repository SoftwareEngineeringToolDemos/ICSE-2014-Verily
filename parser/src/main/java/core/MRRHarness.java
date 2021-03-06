package core;

import org.antlr.v4.runtime.ANTLRFileStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import verily.lang.*;
import verily.lang.exceptions.TableHomomorphismException;
import verily.lang.util.MRRTableSet;
import verily.lang.util.TableDiffResult;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class MRRHarness {


    final Logger logger = LoggerFactory.getLogger(MRRHarness.class);

    private Path base;
    private static String routersPath;
    private static String methodsPath;

    static {
        routersPath = "routers";
        methodsPath = "methods";
    }

    //
    // Note to future explorers:
    //
    // To turn on more logging, manipulate the simplelogger system property like so:
    // -Dorg.slf4j.simpleLogger.defaultLogLevel=trace
    //
    public static void main(String args[]) throws IOException, TableHomomorphismException {

        MRRHarness h = new MRRHarness(Paths.get(""));

        try {
            VerilyTable t = h.extractTranslationTable().getMethodTable();
        }catch(TableHomomorphismException e){

            System.err.println(e.getMessage());

            if(e.errorLocations!=null){
                for(TableDiffResult r : e.errorLocations){
                    System.err.println(r.toString());
                }
                if(e.errorLocations.size()==1){
                    System.err.println("1 error");
                }else{
                    System.err.println(String.format("\n%d errors", e.errorLocations.size()));
                }
            }
        }
    }

    public MRRHarness(Path base) {
        this.base = base;
    }



    public MRRTableSet extractTranslationTable() throws IOException, TableHomomorphismException {

        // Parse out translation table from controllers
        VerilyTable routerTable = new VerilyTable();

        DirectoryStream<Path> controllerFiles = Files.newDirectoryStream(base.resolve("src").resolve("main").resolve("java").resolve(routersPath), "*.java");
        for (Path p : controllerFiles) {
            parseFile(p.toString(), routerTable, VerilyParserModes.VerilyModeType.TYPE_ROUTER);
        }

        // Parse out translation table from methods
        VerilyTable methodTable = new VerilyTable();

        DirectoryStream<Path> methodFiles = Files.newDirectoryStream(base.resolve("src").resolve("main").resolve("java").resolve(methodsPath), "*.java");
        for (Path p : methodFiles) {
            parseFile(p.toString(), methodTable, VerilyParserModes.VerilyModeType.TYPE_METHOD);
        }

        // Start checking things

        //
        // Check Translation Table homomorphism
        //
        List<TableDiffResult> diffResultList = VerilyTable.checkMRRContractWith(routerTable, methodTable);

        if (diffResultList!=null && diffResultList.size()!=0) {
            throw new TableHomomorphismException("Method and Controller tables do not match. For any given function in a method, there should be one matching in name, arity and type in your routers.", diffResultList);
        }

        if(methodTable.hasMultipleMutations()==true){
            throw new TableHomomorphismException("You are attempting to modify your session in more than one place. Please ensure only one WritableValue per key is used.");
        }

        logger.info("The Following MRR Endpoints Are Available in Your Application:");

        String[] parts = methodTable.asASCIITable().split("\n");
        for(String p : parts){
            logger.info(p);
        }


        return new MRRTableSet(methodTable, routerTable);
    }

    public void parseFile(String f, VerilyTable table, VerilyParserModes.VerilyModeType mode) {
        boolean gui = true;
        boolean printTree = true;

        try {
            logger.trace("Parsing file: {}", f);

            // Create Lexer
            Lexer lexer = new JavaLexer(new ANTLRFileStream(f));

            CommonTokenStream tokens = new CommonTokenStream(lexer);
            long start = System.currentTimeMillis();
            tokens.fill(); // load all and check time
            long stop = System.currentTimeMillis();
            logger.trace(String.format("File [%s] (lexed in %d ms)", f, stop - start));

            // Create a parser that reads from the scanner
            JavaParser parser = new JavaParser(tokens);

//            if (diag) parser.addErrorListener(new DiagnosticErrorListener());
//            if (bail) parser.setErrorHandler(new BailErrorStrategy());
//            if (SLL) parser.getInterpreter().setPredictionMode(PredictionMode.SLL);

//
//            // start parsing at the compilationUnit rule
            ParserRuleContext t = parser.compilationUnit();
//
//            ParserRuleContext t = parser.compilationUnit();
////            if (notree) parser.setBuildParseTree(false);
//            if (gui) t.inspect(parser);
//            if (printTree) System.out.println(t.toStringTree(parser));

            ParseTreeWalker walker = new ParseTreeWalker(); // create standard walker
            ExtractClassInfoListener extractor = new ExtractClassInfoListener(parser, table, f, mode);
            walker.walk(extractor, t); // initiate walk of tree with listener


        } catch (Exception e) {
            System.err.println("parser exception: " + e);
            e.printStackTrace();   // so we can get stack trace
        }
    }
}
