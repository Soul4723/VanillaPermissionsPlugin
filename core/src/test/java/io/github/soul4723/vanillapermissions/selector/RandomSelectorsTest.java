package io.github.soul4723.vanillapermissions.selector;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RandomSelectorsTest {

    @Test
    void modernRandomSelectorsBecomeTheirWholePool() {
        assertEquals("@a", RandomSelectors.candidates("@r", false));
        assertEquals("@a[team=red]", RandomSelectors.candidates("@r[team=red,limit=3]", false));
        assertEquals("@e[type=zombie]", RandomSelectors.candidates("@e[sort=random,limit=1,type=zombie]", false));
        assertEquals("@e[ type = zombie ]", RandomSelectors.candidates("@e[ sort = RANDOM , type = zombie ]", false));
        assertEquals("@a", RandomSelectors.candidates("@a[sort=random]", false));
        assertEquals("@e[nbt={Tags:[\"a,b\"]},name=\"x,y\"]",
                RandomSelectors.candidates("@e[nbt={Tags:[\"a,b\"]},sort=random,name=\"x,y\",limit=2]", false));
    }

    @Test
    void deterministicSelectorsAndNamesAreLeftAlone() {
        assertNull(RandomSelectors.candidates("@a", false));
        assertNull(RandomSelectors.candidates("@e[sort=nearest,limit=1]", false));
        assertNull(RandomSelectors.candidates("@p", false));
        assertNull(RandomSelectors.candidates("Steve", false));
        assertNull(RandomSelectors.candidates("@", false));
        assertNull(RandomSelectors.candidates("@r[team=red", false));
        assertNull(RandomSelectors.candidates("@e[name=\"x]", false));
        // Before 1.13 only @r is random; c is a count, not a sort.
        assertNull(RandomSelectors.candidates("@e[c=1]", true));
    }

    @Test
    void legacyRandomSelectorsKeepTheirType() {
        assertEquals("@a", RandomSelectors.candidates("@r", true));
        assertEquals("@a[team=red]", RandomSelectors.candidates("@r[c=2,team=red]", true));
        assertEquals("@e[type=Zombie,r=10]", RandomSelectors.candidates("@r[type=Zombie,r=10,c=1]", true));
        assertEquals("@a[0,64,0,5]", RandomSelectors.candidates("@r[0,64,0,5]", true));
    }

    @Test
    void splitsOnlyTopLevelCommas() {
        assertEquals(List.of("a=1", "scores={x=1,y=2}", "b='c,d'"), RandomSelectors.splitOptions("a=1,scores={x=1,y=2},b='c,d'"));
        assertEquals(List.of(), RandomSelectors.splitOptions(""));
        assertNull(RandomSelectors.splitOptions("a={"));
    }
}
