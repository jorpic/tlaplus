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
import java.util.ArrayList;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import tla2sany.semantic.ModuleNode;
import tla2sany.semantic.OpDefNode;
import tlc2.output.EC;
import tlc2.output.MP;
import tlc2.tool.EvalException;
import tlc2.tool.impl.FastTool;
import tlc2.tool.impl.Tool;
import tlc2.repl.REPLException;
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

    // The modules we will extend in the REPL environment.
    public String moduleExtends = "";

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
    }

    public void setSpecFile(final File pSpecFile) {
        specFile = pSpecFile;
    }

    /**
     * Evaluate the given string input as a TLA+ expression.
     *
     * @return the pretty printed result of the evaluation or an empty string if there was an error.
     */
    public String processInput(String evalExpr) throws REPLException, IOException {
        String replValueVarName = "replvalue";
        String specExpr = replValueVarName + " == " + evalExpr;

        // We want to place the spec files used by REPL evaluation into the temporary directory.
        String dir = replTempDir.toString();
        writeConfig(new File(dir, REPL_SPEC_NAME + TLAConstants.Files.CONFIG_EXTENSION));
        writeSpec(
            specExpr,
            new File(dir, REPL_SPEC_NAME + TLAConstants.Files.TLA_EXTENSION)
        );

        // Avoid sending log messages to stdout and reset the messages recording.
        ToolIO.setMode(ToolIO.TOOL);
        ToolIO.reset();

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
			return exprVal.toString();
        } catch (EvalException e) {
            throw new REPLException(explainEvalException(e), e);
        } catch (Assert.TLCRuntimeException e) {
            throw new REPLException(explainTLCRuntimeException(e), e);
        } finally {
            replWriter.flush();
    		tlc2.module.TLC.OUTPUT = null;
        }
    }

    private void writeConfig(File configFile) throws IOException {
        // Create the config file.
        BufferedWriter cfgWriter = new BufferedWriter(new FileWriter(configFile.getAbsolutePath(), false));
        cfgWriter.append("INIT replinit");
        cfgWriter.newLine();
        cfgWriter.append("NEXT replnext");
        cfgWriter.newLine();
        cfgWriter.close();
    }

    private void writeSpec(String evalExpr, File specFile) throws IOException {
        // Create the spec file lines.
        ArrayList<String> lines = new ArrayList<String>();
        lines.add("---- MODULE tlarepl ----");
        if (!moduleExtends.isEmpty()) {
            lines.add("EXTENDS " + moduleExtends);
        }
        lines.add("VARIABLE replvar");
        // Dummy Init and Next predicates.
        lines.add("replinit == replvar = 0");
        lines.add("replnext == replvar' = 0");
        // The expression to evaluate.
        lines.add(evalExpr);
        lines.add("====");

        // Write out the spec file.
        BufferedWriter writer = new BufferedWriter(new FileWriter(specFile.getAbsolutePath(), false));
        for (String line : lines) {
            writer.append(line);
            writer.newLine();
        }
        writer.close();
    }

    private String explainEvalException(EvalException e) {
        // TODO: Improve error messages with more specific detail.
        return e.getMessage().replaceAll("\\n", " ").trim();
    }

    private String explainTLCRuntimeException(Assert.TLCRuntimeException e) {
        String msg;
        if (e.parameters != null && e.parameters.length > 0) {
            // 0..1 \X 0..1 has non-null params of length zero. Actual error message is
            // "Parsing or semantic analysis failed.".
            msg = String.join("\n", e.parameters);
        } else {
            // Examples of what ends up here:
            // 23 = TRUE
            // Attempted to evaluate an expression of form P \/ Q when P was an integer.
            // 23 \/ TRUE
            // Attempted to check equality of integer 23 with non-integer: TRUE
            // CHOOSE x \in Nat : x = 4
            // Attempted to compute the value of an expression of form CHOOSE x \in S: P, but S was not enumerable.
            msg = e.getMessage();
            if (msg == null) {
                msg = "Unknown TLCRuntimeException.";
            }
        }

        // Strip meaningless location from error message.
        msg = msg.replaceFirst("line [0-9]+, col [0-9]+ to line [0-9]+, col [0-9]+ of module tlarepl", "");
        // Replace any newlines with whitespaces.
        msg = msg.replaceAll("\\n", " ").trim();
        return msg;
    }

    /**
     * Runs the main REPL loop continuously until there is a fatal error or a user interrupt.
     */
    public void runREPL(final LineReader reader) throws IOException {
        // Run the loop.
    	String expr = "";
        while (true) {
            try {
                expr = reader.readLine(prompt);
                String res = processInput(expr);
                if (res.equals("")) {
                    continue;
                }
                System.out.println(res);
            } catch (REPLException e) {
                String errorMessage = e.getMessage();
                if (errorMessage != null) {
                    System.out.printf("Error evaluating expression: '%s'%n%s%n", expr, errorMessage);
                } else {
                    System.out.printf("Error evaluating expression: '%s'%n", expr);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (UserInterruptException e) {
                return;
            } catch (EndOfFileException e) {
                e.printStackTrace();
                return;
            } finally {
				// Persistent file and directory will be create on demand.
            	reader.getHistory().save();
            }
        }
    }

    static String getCommunityModules() {
        try {
            // Try loading the "index" class of the Community Modules that define
            // popular modules that should be loaded by default. If the Community Modules
            // are not present, silently fail.
            final Class<?> clazz = Class.forName("tlc2.overrides.CommunityModules");
            final Method m = clazz.getDeclaredMethod("popularModules");
            return (String) m.invoke(null);
        } catch (Exception | NoClassDefFoundError ignore) {
            return null;
        }
    }

    static String getSpecName(File specFile) {
        return specFile.getName().replaceFirst(TLAConstants.Files.TLA_EXTENSION + "$", "");
    }

    public static void main(String[] args) {
        try {
            final Path tempDir = Files.createTempDirectory(TEMP_DIR_PREFIX);
            final REPL repl = new REPL(tempDir);

            // The modules we will extend in the REPL environment.
            repl.moduleExtends += "Reals,Sequences,Bags,FiniteSets,TLC,Randomization";
            String communityModules = getCommunityModules();
            if (communityModules != null) {
                repl.moduleExtends += communityModules;
            }
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
