package fy.cfg.graph;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedMultigraph;
import org.junit.jupiter.api.Test;

public class testcases {

    @Test
    void test() {
        Graph<String, DefaultEdge> graph = new DirectedMultigraph<>(DefaultEdge.class);
        graph.addVertex("a");
        graph.addVertex("b");
        graph.addVertex("c");
        System.out.println(graph.getClass());
    }
}
