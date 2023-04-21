package tlc2;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

public class REPLStateTest {

    @Test
    public void testModules() {
        REPLState st = new REPLState();
        List<String> modules = st.getModules().collect(Collectors.toList());
        assertEquals(0, modules.size());

        st.addModule("Integers");
        modules = st.getModules().collect(Collectors.toList());
        assertEquals(1, modules.size());
        assertEquals("Integers", modules.get(0));

        st.revert(); // reverts uncommited module additions
        modules = st.getModules().collect(Collectors.toList());
        assertEquals(0, modules.size());

        st.addModule("Integers");
        st.commit();
        st.revert(); // does not revert commited modules
        modules = st.getModules().collect(Collectors.toList());
        assertEquals(1, modules.size());
        assertEquals("Integers", modules.get(0));
    }

    @Test
    public void testDefinitions() {
        REPLState st = new REPLState();
        List<String> lines = st.getLines().collect(Collectors.toList());
        assertEquals(0, lines.size());

        String fooDef = "foo == TRUE";
        String alternativeFooDef = "foo == FALSE";
        String barDef = "bar(x) == x";
        st.addDefinition("foo", fooDef);
        lines = st.getLines().collect(Collectors.toList());
        assertEquals(1, lines.size());
        assertEquals(fooDef, lines.get(0));

        st.revert(); // reverts uncommited definition
        lines = st.getLines().collect(Collectors.toList());
        assertEquals(0, lines.size());

        st.addDefinition("foo", fooDef);
        st.commit();
        st.addDefinition("bar", barDef);
        st.commit();
        st.addDefinition("foo", alternativeFooDef);
        lines = st.getLines().collect(Collectors.toList());
        assertEquals(2, lines.size());
        assertEquals(alternativeFooDef, lines.get(0));
        assertEquals(barDef, lines.get(1));

        st.revert();
        lines = st.getLines().collect(Collectors.toList());
        assertEquals(2, lines.size());
        assertEquals(fooDef, lines.get(0));
        assertEquals(barDef, lines.get(1));
    }


    // check modules are empty
    // addModules(), check
    // revert(), check
    // - revert without commit reverts recent additions
    // addModules()
    // commit(), check
    // addModules()
    // commit(), check
    // revert(), check
    // - revert after commit changes nothing
    //
    //
    // addDefinition(), check
    // revert()
    // addDefinition()
    // commit()
    // addDefinition(new), check lines
    // commit(), check
    // addDefinition(replace), check lines
}

