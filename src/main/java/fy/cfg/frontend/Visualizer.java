package fy.cfg.frontend;

import com.mxgraph.layout.mxCircleLayout;
import com.mxgraph.layout.mxIGraphLayout;
import com.mxgraph.util.mxCellRenderer;
import fy.structures.StmtEdge;
import fy.structures.StmtVertex;
import org.jgrapht.ext.JGraphXAdapter;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedMultigraph;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class Visualizer {

    public static void generate_png(String png, DirectedMultigraph<StmtVertex, StmtEdge> g) throws IOException {
        JGraphXAdapter<StmtVertex, StmtEdge> graphAdapter =
                new JGraphXAdapter<>(g);
        mxIGraphLayout layout = new mxCircleLayout(graphAdapter);
        layout.execute(graphAdapter.getDefaultParent());

        BufferedImage image =
                mxCellRenderer.createBufferedImage(graphAdapter, null, 2, Color.WHITE, true, null);
        File imgFile = new File("src/main/resources/graph.png");
        ImageIO.write(image, "PNG", imgFile);
    }
}
