package fy.structures;

import java.util.Objects;

public class StmtVertex {
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

    public NodeAttrs getNodeAttrs() {
        return nodeAttrs;
    }
}
