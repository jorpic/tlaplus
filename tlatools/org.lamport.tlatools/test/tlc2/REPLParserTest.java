package tlc2;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import java.util.List;
import tla2sany.st.SyntaxTreeConstants;

public class REPLParserTest {

    @Test
    public void testModules() {
      REPLParser p = new REPLParser();
      p.parse("");
      assertEquals(
          SyntaxTreeConstants.N_Extends,
          p.parse("EXTENDS A, B, C"));

      List<String> modules = p.getExtendsModules();
      assertEquals(3, modules.size());
      assertEquals("A", modules.get(0));
      assertEquals("B", modules.get(1));
      assertEquals("C", modules.get(2));
    }


    @Test
    public void testOperators() {
      REPLParser p = new REPLParser();
      p.parse("");

      assertEquals(
          SyntaxTreeConstants.N_OperatorDefinition,
          p.parse("Op == TRUE"));
      assertEquals("Op", p.getOperatorName());

      assertEquals(
          SyntaxTreeConstants.N_OperatorDefinition,
          p.parse("Foo_Bar(foo(_), bar(_), x) == foo(bar(x))"));
      assertEquals("Foo_Bar", p.getOperatorName());

      assertEquals(
          SyntaxTreeConstants.N_ModuleDefinition,
          p.parse("Seq == INSTANCE Sequences"));
      assertEquals("Seq", p.getOperatorName());

      assertEquals(
          SyntaxTreeConstants.N_OperatorDefinition,
          p.parse("a ++ b == a + b"));
      assertEquals("++", p.getOperatorName());
    }


    @Test
    public void testFuncitons() {
      REPLParser p = new REPLParser();
      p.parse("");

      assertEquals(
          SyntaxTreeConstants.N_FunctionDefinition,
          p.parse("Fn[x \\in 1..3] == x"));
      assertEquals("Fn", p.getFunctionName());
    }


    // errors
    // p.parse("Foo_Bar(foo(a), bar(b), x) == foo(bar(x))"));
    //
    // p.parse("Seq == INSTANCE Sequences"));
    // Expression at location line 2, col 11 to line 2, col 12 of module x and expression at location line 2, col 13 to line 2, col 13 of module x follow each other without any intervening operator.
    // extends A, B
    // def == TRUE
    // fn[x \in 1..5] == x

    // variables
    // instance
    // comment
    // empty string

    // malformed definition
    // malformed function
}
