package fy.diffgraph;

import fy.structures.NodeAttrs;
import fy.structures.StmtEdge;
import fy.structures.StmtVertex;
import org.jgraph.graph.DefaultEdge;
import org.jgrapht.Graph;
import org.jgrapht.graph.DirectedMultigraph;
import org.jgrapht.graph.Multigraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class testcases {
    Graph<StmtVertex, StmtEdge> g1 = new DirectedMultigraph<StmtVertex, StmtEdge>(StmtEdge.class);
    Graph<StmtVertex, StmtEdge> g2 = new DirectedMultigraph<StmtVertex, StmtEdge>(StmtEdge.class);


    @BeforeEach
    void init() {
        for (int i=0; i<10; i++) {
            String label = "label" + i;
            String dotNum = "dot" + i;
            int line = i;
            NodeAttrs attrs = new NodeAttrs(label, line, dotNum);
            StmtVertex stmtVertex = new StmtVertex(attrs);
            g1.addVertex(stmtVertex);
        }

        for (int i=0; i<6; i++) {
            String label = "label" + i;
            String dotNum = "dot" + i;
            int line = i;
            NodeAttrs attrs = new NodeAttrs(label, line, dotNum);
            StmtVertex stmtVertex = new StmtVertex(attrs);
            g2.addVertex(stmtVertex);
        }

        for (int i=12; i<16; i++) {
            String label = "label" + i;
            String dotNum = "dot" + i;
            int line = i;
            NodeAttrs attrs = new NodeAttrs(label, line, dotNum);
            StmtVertex stmtVertex = new StmtVertex(attrs);
            g2.addVertex(stmtVertex);
        }
    }

    @Test
    void test() {
        Set<StmtVertex> diff1 = new HashSet<>(g1.vertexSet());
        diff1.removeAll(g2.vertexSet());
        System.out.println(diff1);
        Set<StmtVertex> diff2 = new HashSet<>(g2.vertexSet());
        diff2.removeAll(g1.vertexSet());
        System.out.println(diff2);
        Set<StmtVertex> diff = new HashSet<>(diff1);
        diff.addAll(diff2);
        System.out.println(diff);
    }
    
    @Test
    void t() {

    }
}
