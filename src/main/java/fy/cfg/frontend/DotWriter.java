package fy.cfg.frontend;

import fy.structures.StmtEdge;
import fy.structures.StmtVertex;
import org.jgrapht.ext.ComponentAttributeProvider;
import org.jgrapht.ext.ComponentNameProvider;
import org.jgrapht.ext.DOTExporter;
import org.jgrapht.graph.DirectedMultigraph;

import java.io.StringWriter;
import java.io.Writer;

public class DotWriter {

    public static void write(String output, DirectedMultigraph<StmtVertex, StmtEdge> g) {
        // providers
        ComponentNameProvider<StmtVertex> vertexIDProvider = s -> s.getNodeAttrs().keyString();
        ComponentNameProvider<StmtVertex> vertexLabelProvider = s -> s.getNodeAttrs().getAttrs();
        ComponentNameProvider<StmtEdge> edgeLabelProvider = e -> e.getType().toString();

        DOTExporter<StmtVertex, StmtEdge> exporter =
                new DOTExporter<StmtVertex, StmtEdge>(vertexIDProvider, vertexLabelProvider, edgeLabelProvider);
        Writer writer = new StringWriter();
        exporter.exportGraph(g, writer);
        System.out.println(writer);
    }
}
