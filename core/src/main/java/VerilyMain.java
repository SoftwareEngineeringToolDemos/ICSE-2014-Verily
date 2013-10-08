/*
 * PwEMain.java
 *
 * Main entrypoint into PwE.
 *
 */

import content.TemplateFactory;
import core.PwE;
import core.PwEContainer;
import exceptions.InitException;
import exceptions.PwECompileFailedException;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.apache.commons.cli.*;
import org.simpleframework.http.core.Container;
import org.simpleframework.http.core.ContainerServer;
import org.simpleframework.transport.Server;
import org.simpleframework.transport.connect.Connection;
import org.simpleframework.transport.connect.SocketConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.impl.SimpleLogger;
import pwe.lang.exceptions.TableHomomorphismException;
import utils.PwEUtil;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class VerilyMain {

    public static final Options argList = new Options();

    // Main PwE Modes:
    // -run        - run the application (the default)
    // -init <dir> - create a new pwe application in the named directory
    // -new Name   - Create a new Method/Controller pair
    final Logger logger = LoggerFactory.getLogger(VerilyMain.class);

    static {

        Option port = OptionBuilder.withArgName(PwE.ARG_PORTNUMBER)
                .hasArg()
                .withDescription("port number to bind to (default 8000)")
                .create(PwE.ARG_PORT);

        Option init = OptionBuilder.withArgName(PwE.ARG_INIT_DIR)
                .hasArg()
                .withDescription("create a new PwE application in the specified directory")
                .create(PwE.ARG_INIT);

        Option run = new Option(PwE.ARG_RUN, "run the application");
        Option oNew = OptionBuilder.withArgName(PwE.ARG_NEW_CLASS)
                .hasArg()
                .withDescription("create a new PwE Method+Controller pair")
                .create(PwE.ARG_NEW);

        Option help = new Option(PwE.ARG_HELP, "display this help");
        Option nocompile = new Option(PwE.ARG_NOCOMPILE, "do not do internal recompile (used for development only)");

        Option fast = new Option(PwE.ARG_FAST, "do not recalculate dependencies before running");


        Option watch = new Option(PwE.ARG_WATCH, "try to dynamically reload classes and templates (not for production use)");

        Option test = new Option(PwE.ARG_TEST, "run the unit tests for this application");

        Option daemon = new Option(PwE.ARG_DAEMON, "run this application in the background");
        Option threads = OptionBuilder.withArgName(PwE.ARG_NUM_THREADS)
                .hasArg()
                .withDescription("the number of threads to create for handling requests.")
                .create(PwE.ARG_THREADS);


        argList.addOption(port);
        argList.addOption(help);
        argList.addOption(init);
        argList.addOption(run);
        argList.addOption(oNew);
        argList.addOption(nocompile);
        argList.addOption(fast);
        argList.addOption(watch);
        argList.addOption(test);
        argList.addOption(daemon);
        argList.addOption(threads);

        System.setProperty(SimpleLogger.LEVEL_IN_BRACKETS_KEY, "true");
        System.setProperty(SimpleLogger.SHOW_LOG_NAME_KEY, "false");
        System.setProperty(SimpleLogger.LOG_FILE_KEY, "System.out");
        System.setProperty(SimpleLogger.SHOW_THREAD_NAME_KEY, "false");

    }

    public static void main(String args[]) {


        CommandLineParser parser = new PosixParser();
        int EXIT = 0;

        try {
            CommandLine line = parser.parse(argList, args);

            sanityCheckCommandLine(line);

            VerilyMain m = new VerilyMain();

            // this code assumes the command line has been sanity checked already
            if (line.hasOption(PwE.ARG_RUN)) {

                long ts1 = System.currentTimeMillis();
                m.bootstrap(line);

                // compile the project
                if (line.hasOption(PwE.ARG_NOCOMPILE) == false) {
                    PwEUtil.reloadProject();
                }

                long ts2 = System.currentTimeMillis();

                m.ready(ts2 - ts1);


            } else if (line.hasOption(PwE.ARG_HELP)) {
                VerilyMain.usage();
            } else if (line.hasOption(PwE.ARG_INIT)) {
                m.init(line);
            } else if (line.hasOption(PwE.ARG_NEW)) {
                m.newPair(line);
            } else if (line.hasOption(PwE.ARG_TEST)) {
                PwEUtil.test();
            }


        } catch (ParseException e) {
            // we aren't really interested in moving forward if this fails
            System.err.println(PwEUtil.getMessage("MsgParsingFailed") + e.getMessage());
            VerilyMain.usage();
            EXIT = 1;

        } catch (InitException e) {
            // we aren't really interested in moving forward if this fails
            System.err.println(PwEUtil.getMessage("MsgInitFailed") + e.getMessage());
            EXIT = 1;

        } catch (NoSuchFileException e) {
            System.err.println(PwEUtil.getMessage("MsgInvalidDirectoryFormat"));
            EXIT = 1;
        } catch (IOException e) {
            System.err.println(PwEUtil.getMessage("MsgContainerInitFailed") + e.getMessage());
            EXIT = 1;
        } catch (NumberFormatException e) {
            System.err.println(PwEUtil.getMessage("MsgInvalidPort"));
            EXIT = 1;

        } catch (TableHomomorphismException e) {
            System.err.println(e.getMessage());
        } catch (InterruptedException e) {
            // this is a little bit of an unexpected exception so we are going to bail ungracefully
            e.printStackTrace();
            EXIT = 1;
        } catch (PwECompileFailedException e) {
            System.err.println(PwEUtil.getMessage("MsgCompileFailed"));
            EXIT = 1;
        }

        // only make an explicit call to exit if we have an abnormal exit condition
        if (EXIT != 0) {
            System.exit(EXIT);
        }
    }

    public static void sanityCheckCommandLine(CommandLine l) throws ParseException {

        // make sure only one of -run, -init, or -new are specified
        boolean orun = l.hasOption(PwE.ARG_RUN);
        boolean oinit = l.hasOption(PwE.ARG_INIT);
        boolean onew = l.hasOption(PwE.ARG_NEW);
        boolean otest = l.hasOption(PwE.ARG_TEST);


        if (orun ^ oinit ^ onew ^ otest == false) {
            throw new ParseException("Exactly one of -init, -run, -test, or -new must be specified");
        }
    }

    public static void usage() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("pwe", argList);
    }

    public void init(CommandLine cl) throws InitException {

        String newProject = cl.getOptionValue(PwE.ARG_INIT);

        Path here = Paths.get("");

        if (Files.exists(here.resolve(newProject))) {
            throw new InitException(String.format("Directory %s already exists.", newProject));
        }

        // Step 1 - Create the directory hierarchy
        try {

            logger.info("Creating directory hierarchy...");
            Files.createDirectories(here.resolve(newProject).resolve("src").resolve("main").resolve("java").resolve("controllers"));
            Files.createDirectories(here.resolve(newProject).resolve("src").resolve("main").resolve("java").resolve("methods"));

            Files.createDirectories(here.resolve(newProject).resolve("src").resolve("main").resolve("resources").resolve("css"));
            Files.createDirectories(here.resolve(newProject).resolve("src").resolve("main").resolve("resources").resolve("images"));
            Files.createDirectories(here.resolve(newProject).resolve("src").resolve("main").resolve("resources").resolve("js"));

            Files.createDirectories(here.resolve(newProject).resolve("src").resolve("test").resolve("java"));

            logger.info("Done.");

        } catch (IOException e) {
            throw new InitException(String.format("Error creating project directories. Message: %s", e.getMessage()));

        }

        logger.info("Initializing Maven POM...");

        // Step 2 - Fill in the pom.xml template and copy it over.
        try {
            Writer body = new OutputStreamWriter(new FileOutputStream(here.resolve(newProject).resolve("pom.xml").toFile()));
            Map<String, String> vars = new HashMap<String, String>();

            vars.put("version", PwE.VERSION);
            vars.put("projectName", newProject);

            Template t = TemplateFactory.getBootstrapInstance().getPOMTemplate();

            t.process(vars, body);

            body.close();

        } catch (FileNotFoundException e) {
            throw new InitException(String.format("FileNotFoundException. Message: %s", e.getMessage()));
        } catch (TemplateException e) {
            throw new InitException(String.format("Error during template initialization. Message: %s", e.getMessage()));
        } catch (IOException e) {
            throw new InitException(String.format("Error creating POM file. Message: %s", e.getMessage()));
        }

        logger.info("Done. Execute \"pwe -run\" from inside your new project directory to run this project.");
    }

    public void newPair(CommandLine cl) throws InitException {

        String newPairName = cl.getOptionValue(PwE.ARG_NEW);

        Path here = Paths.get("");

        Path method = here.resolve("src").resolve("main").resolve("java").resolve("methods").resolve(newPairName + ".java");
        Path controller = here.resolve("src").resolve("main").resolve("java").resolve("controllers").resolve(newPairName + ".java");
        Path test = here.resolve("src").resolve("test").resolve("java").resolve(newPairName + "Test.java");


        if (Files.exists(method) || Files.exists(controller) || Files.exists(test)) {
            throw new InitException(String.format("At least one element of the pair \"%s\" already exists.", newPairName));
        }


        // Step 1 - Create a Method/Controller pair (and a unit test.)
        logger.info("Creating a new Method/Controller pair...");

        // Step 2 - Fill in the templates and copy it over.
        try {
            Writer methodBody = new OutputStreamWriter(new FileOutputStream(method.toFile()));
            Writer controllerBody = new OutputStreamWriter(new FileOutputStream(controller.toFile()));
            Writer testBody = new OutputStreamWriter(new FileOutputStream(test.toFile()));

            Map<String, String> vars = new HashMap<String, String>();

            vars.put("version", PwE.VERSION);
            vars.put("NAME", newPairName);

            Template t1 = TemplateFactory.getBootstrapInstance().getMethodTemplate();
            Template t2 = TemplateFactory.getBootstrapInstance().getControllerTemplate();
            Template t3 = TemplateFactory.getBootstrapInstance().getTestTemplate();

            t1.process(vars, methodBody);
            t2.process(vars, controllerBody);
            t3.process(vars, testBody);

            methodBody.close();
            controllerBody.close();
            testBody.close();

        } catch (FileNotFoundException e) {
            throw new InitException(String.format("FileNotFoundException. Message: %s", e.getMessage()));
        } catch (TemplateException e) {
            throw new InitException(String.format("Error during template initialization. Message: %s", e.getMessage()));
        } catch (IOException e) {
            throw new InitException(String.format("Error creating Pair file. Message: %s", e.getMessage()));
        }


        logger.info("Method/Controller Pair Created. You can find the files created in the following locations:");
        logger.info("M: {}", method.toString());
        logger.info("C: {}", controller.toString());
        logger.info("T: {}", test.toString());

    }

    public void bootstrap(CommandLine cl) throws IOException, NumberFormatException, TableHomomorphismException {

        int port = PwE.DEFAULT_PORT;
        int numThreads = PwE.DEFAULT_THREADS;

        if (cl.getOptionValue(PwE.ARG_PORT) != null) {
            port = Integer.parseInt(cl.getOptionValue(PwE.ARG_PORT));
        }

        if(cl.getOptionValue(PwE.ARG_THREADS)!=null){
            numThreads = Integer.parseInt(cl.getOptionValue(PwE.ARG_THREADS));
        }

        logger.info("Bootstrapping PwE on port {}...", port);


        Container container = PwEContainer.getContainer(numThreads);

        Server server = new ContainerServer(container);
        Connection connection = new SocketConnection(server);
        SocketAddress address = new InetSocketAddress(port);

        connection.connect(address);

        PwEContainer.getContainer().getEnv().setPort(port);

        if (cl.hasOption(PwE.ARG_WATCH)) {
            PwEContainer.getContainer().getEnv().setReload(true);
        } else {
            PwEContainer.getContainer().getEnv().setReload(false);
        }


        logger.info("Starting services...");

        PwEContainer.getContainer().startServices();

        logger.info("------------------------------------------------------------------------");
        logger.info("PwE STARTUP COMPLETE");
        logger.info("------------------------------------------------------------------------");


    }

    public void ready(long timeInMs) throws IOException, TableHomomorphismException {
        logger.info("Bootstrapping complete in {} seconds. PwE ready to serve requests at http://localhost:{}/", (double) timeInMs / 1000, PwEContainer.getContainer().getEnv().getPort());
    }
}