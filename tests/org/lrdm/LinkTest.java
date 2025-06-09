package org.lrdm;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class LinkTest {
    @Test
    void testLinkEquality() throws IOException {
        TestProperties.loadProperties("resources/sim-test-1.conf");

        Mirror m1 = new Mirror(1,0, TestProperties.props);
        Mirror m2 = new Mirror(2,0, TestProperties.props);
        Mirror m3 = new Mirror(3,0, TestProperties.props);

        Link l1 = new Link(4,m1,m2,0, TestProperties.props);
        Link l2 = new Link(5,m2,m3,0, TestProperties.props);
        Link l3 = new Link(6,m1,m2,0, TestProperties.props);

        System.out.println(l1+","+l2+","+l3);

        assertEquals(l1, l3);
        assertNotEquals(l1, l2);
        assertNotEquals(l1, m1);
    }
}
