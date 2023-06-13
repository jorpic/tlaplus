package tlc2;

import static org.junit.Assert.assertEquals;
import org.junit.Assert;
import org.junit.Test;

import tlc2.repl.REPLException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class REPLTest {

    @Test
    public void testProcessInput() throws IOException, REPLException {
        Path tempDir = Files.createTempDirectory("repltest");
        final REPL repl = new REPL(tempDir);
        repl.moduleExtends += "Reals,Sequences,Bags,FiniteSets,TLC,Randomization";
        String res;

        // Numeric expressions.
        res = repl.processInput("2+2");
        assertEquals("4", res);
        res = repl.processInput("4-2");
        assertEquals("2", res);
        res = repl.processInput("10 \\div 2");
        assertEquals("5", res);

        // Set expressions.
        res = repl.processInput("{1,2} \\X {3,4}");
        assertEquals("{<<1, 3>>, <<1, 4>>, <<2, 3>>, <<2, 4>>}", res);
        res = repl.processInput("{1,2} \\cup {3,4}");
        assertEquals("{1, 2, 3, 4}", res);
        res = repl.processInput("{1,2} \\cap {2,3}");
        assertEquals("{2}", res);

        // Tuple expressions.
        res = repl.processInput("Append(<<1,2>>, 3)");
        assertEquals("<<1, 2, 3>>", res);
        res = repl.processInput("Tail(<<1,2,3>>)");
        assertEquals("<<2, 3>>", res);
        res = repl.processInput("Head(<<1,2,3>>)");
        assertEquals("1", res);
        res = repl.processInput("<<1,2>> \\o <<3>>");
        assertEquals("<<1, 2, 3>>", res);
    }

    @Test
    public void testInvalidExpressions() throws IOException {
        Path tempDir = Files.createTempDirectory("repltest");
        final REPL repl = new REPL(tempDir);
        repl.moduleExtends += "Reals,Sequences,Bags,FiniteSets,TLC,Randomization";

        try {
            repl.processInput("invalid");
            Assert.fail();
        } catch (REPLException e) {
            assertEquals("Unknown operator: `invalid'.", e.getMessage());
        }

        try {
            repl.processInput("123abc");
            Assert.fail();
        } catch (REPLException e) {
            assertEquals("Unknown operator: `123abc'.", e.getMessage());
        }

        try {
            repl.processInput("Append(3, <<1,2>>)");
            Assert.fail();
        } catch (REPLException e) {
            assertEquals("Evaluating an expression of the form Append(s, v) when s is not a sequence: 3", e.getMessage());
        }

        try {
            repl.processInput("0..1 \\X 0..1");
            Assert.fail();
        } catch (REPLException e) {
            assertEquals("Parsing or semantic analysis failed.", e.getMessage());
        }

        try {
            repl.processInput("23 = TRUE");
            Assert.fail();
        } catch (REPLException e) {
            assertEquals("Attempted to check equality of integer 23 with non-integer: TRUE", e.getMessage());
        }

        try {
            repl.processInput("23 \\/ TRUE");
            Assert.fail();
        } catch (REPLException e) {
            assertEquals("Attempted to evaluate an expression of form P \\/ Q when P was an integer.", e.getMessage());
        }

        try {
            repl.processInput("CHOOSE x \\in Nat : x = 4");
            Assert.fail();
        } catch (REPLException e) {
            assertEquals("Attempted to compute the value of an expression of form CHOOSE x \\in S: P, but S was not enumerable.", e.getMessage());
        }
    }
}
