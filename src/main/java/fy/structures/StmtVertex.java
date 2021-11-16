package fy.structures;

import java.util.Comparator;
import java.util.Objects;

public class StmtVertex implements Comparator<StmtVertex> {
    private final NodeAttrs nodeAttrs;

    public StmtVertex(NodeAttrs nodeAttrs) {
        this.nodeAttrs = nodeAttrs;
    }

    @Override
    public String toString() {
        return nodeAttrs.keyString();
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof StmtVertex) && (toString().equals(o.toString()));
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public int compare(StmtVertex v1, StmtVertex v2) {
        return v1.getNodeAttrs().getLabel().compareTo(v2.getNodeAttrs().getLabel());
    }

    public NodeAttrs getNodeAttrs() {
        return nodeAttrs;
    }
}
