package tlc2;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import tla2sany.configuration.Configuration;
import tla2sany.semantic.AbortException;
import tla2sany.semantic.Errors;
import tla2sany.semantic.ModuleNode;
import tla2sany.semantic.OpDefNode;
import tla2sany.st.SyntaxTreeConstants;
import tlc2.output.EC;
import tlc2.output.MP;
import tlc2.tool.EvalException;
import tlc2.tool.impl.FastTool;
import tlc2.tool.impl.Tool;
import tlc2.value.impl.Value;
import util.Assert;
import util.SimpleFilenameToStream;
import util.TLAConstants;
import util.ToolIO;


/**
 * A TLA+ REPL which provides an interactive mode of evaluating expressions and specifications.
 */
public class REPL {
	
	private static final String HISTORY_PATH = System.getProperty("user.home", "") + File.separator + ".tlaplus" + File.separator + "history.repl";

    // The spec file to use in the REPL context, if any.
    private File specFile = null;

    private REPLState replState = new REPLState();

    // The naming prefix of the temporary directory.
    static final String TEMP_DIR_PREFIX = "tlarepl";

    // The name of the spec used for evaluating expressions.
    final String REPL_SPEC_NAME = "tlarepl";
    
    private static final String prompt = "(tla+) ";

    private final Writer replWriter = new PrintWriter(System.out);
    
    // A temporary directory to place auxiliary files needed for REPL evaluation.
    Path replTempDir;

    public REPL(Path tempDir) {
        replTempDir = tempDir;

        // Add default modules.
        replState.addModule(
            // FIXME: slpit
            "Reals,Sequences,Bags,FiniteSets,TLC,Randomization"
        );
        try {
            // Try loading the "index" class of the Community Modules that define
            // popular modules that should be loaded by default. If the Community Modules
            // are not present, silently fail.
            final Class<?> clazz = Class.forName("tlc2.overrides.CommunityModules");
            final Method m = clazz.getDeclaredMethod("popularModules");
            replState.addModule(
                // FXIME: split and trim
                m.invoke(null).toString()
            );
        } catch (Exception | NoClassDefFoundError ignore) {
        }
        replState.commit();
    }

    public void setSpecFile(final File pSpecFile) {
        specFile = pSpecFile;
        String mainModuleName = specFile.getName().replaceFirst(TLAConstants.Files.TLA_EXTENSION + "$", "");
        // FIXME: split into parts and trim
        replState.addModule(mainModuleName);
        replState.commit();
    }


    private void writeSpecFiles(
        final String configPath,
        final String specPath,
        final String expr
    ) throws IOException {
        // FIXME: use util.TLAConstants
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(configPath, false))) {
            String str = Stream.of("INIT replinit", "NEXT replnext")
                .collect(Collectors.joining(System.lineSeparator()));
            writer.append(str);
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(specPath, false))) {
            String str = Stream.of(
                Stream.of(
                    "---- MODULE tlarepl ----",
                    // FIXME: there could be no modules at all
                    "EXTENDS " + replState.getModules().collect(Collectors.joining(","))),

                replState.getLines(),

                Stream.of(
                    "VARIABLE replvar",
                    // Dummy Init and Next predicates.
                    "replinit == replvar = 0",
                    "replnext == replvar' = 0",
                    // The expression to evaluate.
                    "replvalue == " + expr,
                    "====\n"))
            .flatMap(x -> x)
            .collect(Collectors.joining(System.lineSeparator()));
            System.out.print(str);
            writer.append(str);
        }
    }

    /**
     * Evaluate the given string input as a TLA+ expression.
     *
     * @return the pretty printed result of the evaluation or an empty string if there was an error.
     */
    public String processInput(String evalExpr) {
				tlc2.module.TLC.OUTPUT = replWriter;

        // Try to parse input as top-level TLA+ """command""".
        REPLParser p = new REPLParser();
        Integer res = p.parse(evalExpr);
        switch (res) {
            case SyntaxTreeConstants.N_Extends:
              p.getExtendsModules().forEach(replState::addModule);
              evalExpr = "TRUE";
              break;
            case SyntaxTreeConstants.N_OperatorDefinition:
              replState.addDefinition(p.getOperatorName(), evalExpr);
              evalExpr = "TRUE";
              break;
            case SyntaxTreeConstants.N_FunctionDefinition:
              replState.addDefinition(p.getFunctionName(), evalExpr);
              evalExpr = "TRUE";
              break;
            case SyntaxTreeConstants.N_ModuleDefinition:
              replState.addDefinition(p.getOperatorName(), evalExpr);
              evalExpr = "TRUE";
              break;
        }


        try {
            // We want to place the spec files used by REPL evaluation into the temporary directory.
            File tempFile = new File(replTempDir.toString(), REPL_SPEC_NAME + TLAConstants.Files.TLA_EXTENSION);
            File configFile = new File(replTempDir.toString(), REPL_SPEC_NAME + TLAConstants.Files.CONFIG_EXTENSION);

            String replValueVarName = "replvalue";
            writeSpecFiles(
                configFile.getAbsolutePath(),
                tempFile.getAbsolutePath(),
                evalExpr);

            try {
                // We placed the REPL spec files into a temporary directory, so, we add this temp directory
                // path to the filename resolver used by the Tool.
                SimpleFilenameToStream resolver = new SimpleFilenameToStream(replTempDir.toAbsolutePath().toString());
                Tool tool = new FastTool(REPL_SPEC_NAME, REPL_SPEC_NAME, resolver);
                ModuleNode module = tool.getSpecProcessor().getRootModule();
                OpDefNode valueNode = module.getOpDef(replValueVarName);
                
				// Make output of TLC!Print and TLC!PrintT appear in the REPL. Set here
				// and unset in finally below to suppress output of FastTool instantiation
				// above.
				tlc2.module.TLC.OUTPUT = replWriter;
				final Value exprVal = (Value) tool.eval(valueNode.getBody());
				replState.commit();
				return exprVal.toString();
            } catch (EvalException | Assert.TLCRuntimeException exc) {
                replState.revert();
                translateError(evalExpr, exc);
            } finally {
                replWriter.flush();
        		tlc2.module.TLC.OUTPUT = null;
            }
        } catch (IOException pe) {
            pe.printStackTrace();
        }
        return "";
    }


    private void translateError(String evalExpr, Exception e) {
        if (e instanceof Assert.TLCRuntimeException) {
            Assert.TLCRuntimeException exc = (Assert.TLCRuntimeException) e;
            if (exc.parameters != null && exc.parameters.length > 0) {
                // 0..1 \X 0..1 has non-null params of length zero. Actual error message is
                // "Parsing or semantic analysis failed.".
                System.out.printf(
                    "Error evaluating expression: '%s'%n%s%n",
                    evalExpr,
                    Arrays.toString(exc.parameters));
            } else if (exc.getMessage() != null) {
                // Examples of what ends up here:
                // 23 = TRUE
                // Attempted to evaluate an expression of form P \/ Q when P was an integer.
                // 23 \/ TRUE
                // Attempted to check equality of integer 23 with non-integer: TRUE
                // CHOOSE x \in Nat : x = 4
                // Attempted to compute the value of an expression of form CHOOSE x \in S: P, but S was not enumerable.
                String msg = exc.getMessage().trim();
                // Strip meaningless location from error message.
                msg = msg.replaceFirst("\\nline [0-9]+, col [0-9]+ to line [0-9]+, col [0-9]+ of module tlarepl$", "");
                // Replace any newlines with whitespaces.
                msg = msg.replaceAll("\\n", " ").trim();
                System.out.printf("Error evaluating expression: '%s'%n%s%n", evalExpr, msg);
            } else {
                System.out.printf("Error evaluating expression: '%s'%n", evalExpr);
            }
        } else {
            // TODO: Improve error messages with more specific detail.
            System.out.printf(
                "Error evaluating expression: '%s'%n%s%n",
                evalExpr,
                e);
        }
    }


    /**
     * Runs the main REPL loop continuously until there is a fatal error or a user interrupt.
     */
    public void runREPL(final LineReader reader) throws IOException {
        // Load sany config.
        // This is required to run TLAplusParser.
        try {
            Configuration.load(new Errors());
        } catch (AbortException e) {
            System.out.println("Can`t load SANY configuration.");
            return;
        }

        // Run the loop.
    	String expr;
        while (true) {
            try {
                expr = reader.readLine(prompt);
                String res = processInput(expr);
                if (res.equals("")) {
                    continue;
                }
                System.out.println(res);
            } catch (UserInterruptException e) {
                return;
            } catch (EndOfFileException e) {
                e.printStackTrace(); // Why?
                return;
            } finally {
				// Persistent file and directory will be create on demand.
            	reader.getHistory().save();
            }
        }
    }

    public static void main(String[] args) {
        // Avoid sending log messages to stdout and reset the messages recording.
        // ToolIO.setMode(ToolIO.TOOL);
        // ToolIO.reset();



        try {
            final Path tempDir = Files.createTempDirectory(TEMP_DIR_PREFIX);
            final REPL repl = new REPL(tempDir);
            // TODO: Allow external spec file to be loaded into REPL context.
 
            if(args.length == 1) {
                String res = repl.processInput(args[0]);
                if (!res.equals("")) {
                	System.out.println(res);
                }
                //TODO Return actual exit value if parsing/evaluation fails.
                System.exit(0);
            }

            // For TLA+ we don't want to treat backslashes as escape chars e.g. for LaTeX like operators.
			final DefaultParser parser = new DefaultParser();
			parser.setEscapeChars(null);
			final Terminal terminal = TerminalBuilder.builder().build();
			final LineReader reader = LineReaderBuilder.builder().parser(parser).terminal(terminal)
					.history(new DefaultHistory()).build();
			reader.setVariable(LineReader.HISTORY_FILE, HISTORY_PATH);

			System.out.println("Welcome to the TLA+ REPL!");
            MP.printMessage(EC.TLC_VERSION, TLCGlobals.versionOfTLC);
        	System.out.println("Enter a constant-level TLA+ expression.");

            repl.runREPL(reader);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
